package com.example.mypodcast.media

import android.os.CountDownTimer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SleepTimerManager @Inject constructor() {

    private val _remainingMs = MutableStateFlow(0L)
    val remainingMs: StateFlow<Long> = _remainingMs

    private var timer: CountDownTimer? = null

    fun start(minutes: Int, onFinish: () -> Unit) {
        timer?.cancel()
        timer = object : CountDownTimer(minutes * 60_000L, 1000L) {
            override fun onTick(remaining: Long) { _remainingMs.value = remaining }
            override fun onFinish() { _remainingMs.value = 0L; onFinish() }
        }.start()
    }

    fun cancel() {
        timer?.cancel()
        _remainingMs.value = 0L
    }
}
