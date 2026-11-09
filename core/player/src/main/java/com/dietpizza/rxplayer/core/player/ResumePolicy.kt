package com.dietpizza.rxplayer.core.player

import com.dietpizza.rxplayer.core.model.PlaybackState
import com.dietpizza.rxplayer.core.model.ResumeMode

sealed interface ResumeDecision {
    data object StartOver : ResumeDecision
    data class Resume(val positionMs: Long) : ResumeDecision
    data class Ask(val positionMs: Long) : ResumeDecision
}

/** Resume-on-open rules (plan §6.4, decision 30). */
object ResumePolicy {
    /** Progress below this isn't worth resuming (or asking about). */
    const val MIN_RESUME_MS = 5_000L

    /** Progress within this of the end counts as finished. */
    const val END_MARGIN_MS = 5_000L

    fun decide(mode: ResumeMode, state: PlaybackState?): ResumeDecision {
        if (state == null || state.completed || mode == ResumeMode.NEVER) return ResumeDecision.StartOver
        val pos = state.positionMs
        if (pos < MIN_RESUME_MS) return ResumeDecision.StartOver
        if (state.durationMs > 0 && pos >= state.durationMs - END_MARGIN_MS) return ResumeDecision.StartOver
        return when (mode) {
            ResumeMode.AUTO -> ResumeDecision.Resume(pos)
            ResumeMode.ASK -> ResumeDecision.Ask(pos)
            ResumeMode.NEVER -> ResumeDecision.StartOver
        }
    }
}
