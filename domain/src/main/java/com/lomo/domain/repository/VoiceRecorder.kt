package com.lomo.domain.repository

interface VoiceRecorder {
    fun start(outputUri: String)

    fun stop()

    fun getAmplitude(): Int
    // release() is lifecycle managed effectively by implementation, usually not exposed unless needed
}
