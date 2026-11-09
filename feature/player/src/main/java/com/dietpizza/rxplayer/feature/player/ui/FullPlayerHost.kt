package com.dietpizza.rxplayer.feature.player.ui

import androidx.compose.runtime.Stable

/** Orientation choices of the ⋮ sheet (decision 18: Auto follows the video's aspect). */
enum class OrientationMode(val label: String) {
    Auto("Auto"),
    Landscape("Landscape"),
    Portrait("Portrait"),
    Sensor("Sensor"),
}

/**
 * Activity-level services the [FullPlayer] needs but must not own (PiP, orientation, immersive
 * bars, the subtitle file picker). `PlayerActivity` implements it; properties are snapshot state.
 */
@Stable
interface FullPlayerHost {
    val isInPip: Boolean
    val rotationLocked: Boolean
    val orientationMode: OrientationMode

    /** False when PiP isn't possible right now (nothing loaded, or the device disallows it). */
    val canEnterPip: Boolean

    fun toggleRotationLock()
    fun chooseOrientationMode(mode: OrientationMode)
    fun enterPip()
    fun setSystemBarsVisible(visible: Boolean)

    /** Opens the system file picker; the result goes to `PlaybackController.addSubtitle`. */
    fun pickSubtitleFile()
}
