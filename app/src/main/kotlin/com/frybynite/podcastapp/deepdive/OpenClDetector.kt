package com.frybynite.podcastapp.deepdive

object OpenClDetector {
    fun isSupported(): Boolean = try {
        System.loadLibrary("OpenCL")
        true
    } catch (e: UnsatisfiedLinkError) {
        false
    }
}
