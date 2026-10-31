package com.echodrumz.audio

/** Thin JNI bridge to the C++ audio engine (engine.cpp). */
object NativeEngine {
    init { System.loadLibrary("echodrumz") }

    external fun start(): Boolean
    external fun stop()
    external fun loadSample(pad: Int, data: FloatArray, channels: Int, sampleRate: Int)
    external fun clearSample(pad: Int)
    external fun triggerPad(pad: Int, velocity: Float)
    external fun releasePad(pad: Int)
    external fun setPadParams(pad: Int, volume: Float, pitch: Float, pan: Float, gate: Boolean, choke: Int, mute: Boolean)
    external fun setBpm(bpm: Float)
    external fun setSwing(swing: Float)
    external fun setPlaying(playing: Boolean)
    external fun setStep(pad: Int, step: Int, velocity: Int)
    external fun getPlayhead(): Int
}
