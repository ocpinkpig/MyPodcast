# On-Device Transcript Generation for Downloaded Episodes — Design

**Date:** 2026-06-10
**Status:** Approved

## Problem

Episodes whose RSS feed has no `<podcast:transcript>` URL show an empty transcript
page. The app should generate a transcript on-device for downloaded episodes using
the ML Kit GenAI Speech Recognition API, and surface it on the existing transcript
page plus a "transcript ready" badge in the Library.

## Requirements (confirmed with user)

1. **Trigger:** Automatic, but only for episodes with no publisher `transcriptUrl`.
   Publisher transcripts always win.
2. **Unsupported devices:** Silently skip. The transcript page keeps its current
   `Empty` state. No error messaging, no alternative ASR engine.
3. **Notifications:** None. In-app indication only.
4. **Indicator:** A badge on downloaded episode rows in the Library once the
   transcript is complete, plus live status on the transcript page.
5. **Execution model:** Transcription runs only while the episode is playing
   ("Variant 2"): an independent real-time-paced decode anchored to the playback
   session, not a tap of the playback audio.

## Key constraints (from ML Kit GenAI Speech Recognition, 1.0.0-alpha1)

- Input must be raw headerless 16-bit PCM, mono, 16 kHz, streamed at real-time
  rate (~32 KB/s). Full-speed file reads are unsupported. Hence transcribing N
  minutes of audio takes ~N minutes of wall-clock work.
- Basic mode requires API 31+; advanced (GenAI) mode currently Pixel-10-class
  hardware via AICore. App minSdk is 26, so availability gating is mandatory.
- `checkStatus()` returns `AVAILABLE` / `DOWNLOADABLE` / `DOWNLOADING` /
  `UNAVAILABLE`. Results stream as partial-then-final text segments without
  documented timestamps — we compute timestamps ourselves (see below).
- The API is alpha: the engine sits behind an app-owned interface so it can be
  swapped or upgraded without touching the rest of the pipeline.

## Architecture

```
PlaybackService (existing, foreground while playing)
   └── TranscriptionSessionManager (new, Hilt singleton)
         │  starts/suspends sessions based on player state
         └── EpisodeTranscriber (new, data/transcription)
               ├── Mp3PcmStreamer: MediaExtractor/MediaCodec decode → downmix to
               │     mono → linear resample to 16 kHz → 100 ms chunks paced at
               │     ~32 KB/s into a ParcelFileDescriptor pipe
               └── MlKitSpeechEngine: thin wrapper around ML Kit SpeechRecognizer
         └── GeneratedTranscriptStore (new): progress file + cues on disk
TranscriptRepositoryImpl (existing, extended): publisher URL → else generated store
PlayerViewModel / TranscriptPage / Library rows (existing, extended)
```

### Session rules

A session for episode E starts when ALL hold:

- E is currently playing, and
- E is downloaded, and
- E has no publisher `transcriptUrl`, and
- the engine reports `AVAILABLE` (API 31+ and feature status OK), and
- E is not already fully transcribed.

Pause/stop/episode-switch suspends the session and persists progress. The next
qualifying playback resumes from the saved offset. At most one session at a time.
If feature status is `DOWNLOADABLE`, request the model download once and stay
silent until it becomes `AVAILABLE`. Below API 31 or `UNAVAILABLE`: do nothing.

### Transcriber

- `Mp3PcmStreamer` decodes the local MP3 with `MediaExtractor`/`MediaCodec` (no
  new dependency), downmixes to mono, linear-resamples to 16 kHz, and writes
  100 ms chunks into a pipe PFD at real-time pace. Supports starting from an
  arbitrary millisecond offset (`MediaExtractor.seekTo`) for resume.
- **Timestamps:** we control the byte clock, so position-in-ms = fed-bytes /
  32 000. A finalized engine segment becomes a `TranscriptCue` with `startMs` =
  previous cue's end and `endMs` = current stream position. Generated transcripts
  are therefore `isSynced = true` (highlight, auto-scroll, tap-to-seek all work).
- **Errors:** an engine error suspends the session (progress already persisted);
  the next playback session retries fresh. No mid-playback retry loops.
- **Language:** recognizer locale from the podcast feed `<language>` tag when
  present, else the device locale.

### Persistence

- Generated cues + progress in one JSON file per episode:
  `filesDir/transcripts/generated/<guid>.json` with
  `{ cues[], transcribedUpToMs, isComplete, engineVersion }`. Written atomically
  (`.part` + rename, the existing codebase idiom) every ~15 finalized cues or
  30 seconds, whichever first.
- Room: new `transcriptStatus` column (`NONE` / `IN_PROGRESS` / `COMPLETE`) on
  `DownloadedEpisodeEntity`, with migration. Drives the Library badge through the
  DAO flows the Library already observes.
- Deleting a download also deletes the generated file and resets status
  (extends the existing delete path in `LibraryRepositoryImpl`).
- `TranscriptRepositoryImpl` gains a second source: when `transcriptUrl` is
  absent, read the generated store. `Transcript` gets one additive field,
  `transcribedUpToMs: Long? = null` (null = complete or publisher-sourced).
  Existing parsers and call sites untouched.

### UI

- **Transcript page:** while a session is live, the page observes a
  `StateFlow<List<TranscriptCue>>` from the session manager — cues appear as the
  episode plays, with a footer "Transcribing as you listen · up to 12:34". A
  partially transcribed, non-playing episode shows saved cues plus the same
  footer (static). Fully transcribed episodes render identically to publisher
  ones. Unsupported devices / non-downloaded episodes: current `Empty` state.
- **Library rows:** downloaded episodes with status `COMPLETE` get a small
  transcript badge (icon + "Transcript") in `EpisodeListItem`.

## Testing

JVM unit tests, matching existing test style:

- Resampler/downmix math in `Mp3PcmStreamer` (pure functions extracted).
- Byte-offset → timestamp mapping and cue boundary assignment.
- Progress JSON round-trip and atomic-write behavior.
- `TranscriptionSessionManager` state machine against a fake engine:
  start / suspend / resume / complete / error / episode-switch.
- Repository source selection: publisher URL wins; generated-store fallback;
  `transcribedUpToMs` propagation.
- Room migration test for the new column.

The ML Kit engine wrapper is intentionally thin and verified manually on an
API 31+ device.

## Out of scope (YAGNI)

- Transcribing episodes that already have publisher transcripts.
- Alternative ASR engines for pre-API-31 devices.
- System notifications of any kind.
- Speaker diarization, translation, or transcript editing.
- Transcribing streamed (non-downloaded) episodes.
