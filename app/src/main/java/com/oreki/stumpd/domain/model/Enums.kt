package com.oreki.stumpd.domain.model

// White/Red-ball and short/long pitch
enum class BallFormat { WHITE_BALL, RED_BALL }

enum class ExtraType(val displayName: String) {
    NO_BALL("No Ball"),
    OFF_SIDE_WIDE("Off Side Wide"),
    LEG_SIDE_WIDE("Leg Side Wide"),
    BYE("Bye"),
    LEG_BYE("Leg Bye")
}

enum class WicketType {
    BOWLED,
    CAUGHT,
    LBW,
    RUN_OUT,
    STUMPED,
    HIT_WICKET,
    BOUNDARY_OUT
}

enum class RunOutEnd { STRIKER_END, NON_STRIKER_END }

enum class NoBallSubOutcome { NONE, RUN_OUT, BOUNDARY_OUT }
