package com.oreki.stumpd.ui.scoring

/** A moment in the innings worth answering with movement and a haptic. */
enum class ScoringEffect {
    /** Four runs off the bat. */
    Four,

    /** Six runs off the bat. */
    Six,

    /** A wicket — answered in the opposite direction to a boundary. */
    Wicket,
}
