package com.dietpizza.rxplayer.core.player.gesture

import com.dietpizza.rxplayer.core.player.gesture.PlayerGestureMachine.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlayerGestureMachineTest {

    private class Recorder : PlayerGestureSink {
        val events = mutableListOf<String>()
        override fun onSingleTap(x: Float, y: Float) { events += "tap" }
        override fun onDoubleTap(x: Float, y: Float) { events += "double" }
        override fun onLongPressStart(x: Float, y: Float) { events += "long+" }
        override fun onLongPressEnd() { events += "long-" }
        override fun onHorizontalSeekStart() { events += "seek+" }
        override fun onHorizontalSeek(totalDx: Float) { events += "seek:${totalDx.toInt()}" }
        override fun onHorizontalSeekEnd(cancelled: Boolean) { events += if (cancelled) "seek-x" else "seek-" }
        override fun onVerticalDownStart() { events += "down+" }
        override fun onVerticalDown(totalDy: Float) { events += "down:${totalDy.toInt()}" }
        override fun onVerticalDownEnd(cancelled: Boolean) { events += "down-" }
        override fun onPinchStart() { events += "pinch+" }
        override fun onPinch(scale: Float) { events += "pinch:$scale" }
        override fun onPinchEnd(cancelled: Boolean) { events += "pinch-" }
        var chain = false
        override fun canChainDoubleTap(x: Float, y: Float) = chain
    }

    private lateinit var sink: Recorder
    private lateinit var m: PlayerGestureMachine

    @Before
    fun setUp() {
        sink = Recorder()
        m = PlayerGestureMachine(sink, touchSlopPx = 10f, doubleTapSlopPx = 100f, doubleTapTimeoutMs = 300, longPressTimeoutMs = 400)
    }

    private fun tap(t: Long, x: Float = 100f, y: Float = 100f, hold: Long = 50) {
        m.onDown(t, x, y)
        m.onUp(t + hold)
    }

    @Test
    fun singleTapFiresImmediatelyOnUp() {
        m.onDown(0, 100f, 100f)
        assertEquals(emptyList<String>(), sink.events)
        m.onUp(80)
        assertEquals(listOf("tap"), sink.events)
    }

    @Test
    fun secondTapInsideWindowIsDoubleTapOnDown() {
        tap(0)
        m.onDown(250, 110f, 105f) // 200 ms after first up
        assertEquals(listOf("tap", "double"), sink.events)
        m.onUp(300)
        assertEquals(listOf("tap", "double"), sink.events)
    }

    @Test
    fun secondTapAfterWindowIsAnotherSingleTap() {
        tap(0)
        tap(50 + 301)
        assertEquals(listOf("tap", "tap"), sink.events)
    }

    @Test
    fun secondTapFarAwayIsNotDoubleTap() {
        tap(0, x = 100f)
        tap(200, x = 400f)
        assertEquals(listOf("tap", "tap"), sink.events)
    }

    @Test
    fun thirdTapStartsFresh() {
        tap(0)
        tap(150) // double
        tap(300)
        assertEquals(listOf("tap", "double", "tap"), sink.events)
    }

    @Test
    fun doubleTapDisabledGivesTwoSingleTaps() {
        m.doubleTapEnabled = false
        tap(0)
        tap(150)
        assertEquals(listOf("tap", "tap"), sink.events)
    }

    @Test
    fun doubleTapSwallowsMovementOfSecondTap() {
        tap(0)
        m.onDown(150, 100f, 100f)
        m.onMove(170, 300f, 100f, 1, 0f)
        m.onUp(200)
        assertEquals(listOf("tap", "double"), sink.events)
    }

    @Test
    fun movementWithinSlopIsStillATap() {
        m.onDown(0, 100f, 100f)
        m.onMove(20, 106f, 106f, 1, 0f) // 8.5 px < 10
        m.onUp(60)
        assertEquals(listOf("tap"), sink.events)
    }

    @Test
    fun horizontalAxisLocksAfterSlopAndMeasuresFromLockPoint() {
        m.onDown(0, 100f, 100f)
        m.onMove(10, 115f, 104f, 1, 0f)
        assertEquals(Mode.Seek, m.mode)
        m.onMove(20, 165f, 140f, 1, 0f) // vertical drift doesn't unlock the axis
        m.onMove(30, 85f, 160f, 1, 0f)
        m.onUp(40)
        assertEquals(listOf("seek+", "seek:50", "seek:-30", "seek-"), sink.events)
    }

    @Test
    fun verticalDownLocksAndReportsNonNegativeProgress() {
        m.onDown(0, 100f, 100f)
        m.onMove(10, 103f, 115f, 1, 0f)
        assertEquals(Mode.SwipeDown, m.mode)
        m.onMove(20, 103f, 175f, 1, 0f)
        m.onMove(30, 103f, 90f, 1, 0f)
        m.onUp(40)
        assertEquals(listOf("down+", "down:60", "down:0", "down-"), sink.events)
    }

    @Test
    fun upwardSwipeAndDisabledDownSwipeDoNothing() {
        m.onDown(0, 100f, 100f)
        m.onMove(10, 100f, 70f, 1, 0f)
        m.onUp(20)
        m.verticalDownEnabled = false
        m.onDown(1000, 100f, 100f)
        m.onMove(1010, 100f, 150f, 1, 0f)
        m.onMove(1020, 300f, 150f, 1, 0f) // no re-lock to seek
        m.onUp(1030)
        assertEquals(emptyList<String>(), sink.events)
    }

    @Test
    fun longPressFiresAtDeadlineAndEndsOnUp() {
        m.onDown(0, 100f, 100f)
        assertEquals(400L, m.nextDeadlineMs)
        m.onTick(399)
        assertEquals(emptyList<String>(), sink.events)
        m.onTick(400)
        assertEquals(listOf("long+"), sink.events)
        assertEquals(PlayerGestureMachine.NO_TIME, m.nextDeadlineMs)
        m.onMove(500, 300f, 100f, 1, 0f) // moving while held doesn't start a seek
        m.onUp(900)
        assertEquals(listOf("long+", "long-"), sink.events)
    }

    @Test
    fun missedTickIsProcessedOnUp() {
        m.onDown(0, 100f, 100f)
        m.onUp(600)
        assertEquals(listOf("long+", "long-"), sink.events)
    }

    @Test
    fun longPressDisabledHoldIsATap() {
        m.longPressEnabled = false
        m.onDown(0, 100f, 100f)
        assertEquals(PlayerGestureMachine.NO_TIME, m.nextDeadlineMs)
        m.onUp(900)
        assertEquals(listOf("tap"), sink.events)
    }

    @Test
    fun dragBeforeLongPressCancelsIt() {
        m.onDown(0, 100f, 100f)
        m.onMove(100, 150f, 100f, 1, 0f)
        m.onTick(1000)
        m.onUp(1100)
        assertEquals(listOf("seek+", "seek-"), sink.events)
    }

    @Test
    fun secondFingerBeforeLockIsPinch() {
        m.onDown(0, 100f, 100f)
        m.onMove(10, 101f, 100f, 2, 200f)
        assertEquals(Mode.Pinch, m.mode)
        m.onMove(20, 101f, 100f, 2, 300f)
        m.onMove(30, 101f, 100f, 1, 0f) // one finger lifted: ignored
        m.onUp(40)
        assertEquals(listOf("pinch+", "pinch:1.5", "pinch-"), sink.events)
    }

    @Test
    fun secondFingerAfterSeekLockDoesNotPinch() {
        m.onDown(0, 100f, 100f)
        m.onMove(10, 130f, 100f, 1, 0f)
        m.onMove(20, 140f, 100f, 2, 200f)
        m.onUp(30)
        assertEquals(listOf("seek+", "seek:10", "seek-"), sink.events)
    }

    @Test
    fun cancelEndsActiveGestureAsCancelledAndForgetsTap() {
        tap(0)
        m.onDown(1000, 100f, 100f)
        m.onMove(1010, 150f, 100f, 1, 0f)
        m.cancel()
        assertEquals(listOf("tap", "seek+", "seek-x"), sink.events)
        assertEquals(Mode.Idle, m.mode)
        tap(1100)
        tap(1200)
        assertTrue(sink.events.takeLast(2) == listOf("tap", "double"))
    }

    @Test
    fun thirdTapChainsWhenSinkAllows() {
        sink.chain = true
        tap(0)
        tap(200)
        tap(400)
        tap(600)
        assertEquals(listOf("tap", "double", "double", "double"), sink.events)
    }

    @Test
    fun thirdTapIsSingleWhenSinkDoesNotChain() {
        tap(0)
        tap(200)
        tap(400)
        assertEquals(listOf("tap", "double", "tap"), sink.events)
    }

    @Test
    fun chainEndsAfterTheWindow() {
        sink.chain = true
        tap(0)
        tap(200)
        tap(700)
        assertEquals(listOf("tap", "double", "tap"), sink.events)
    }
}
