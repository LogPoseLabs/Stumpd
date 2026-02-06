package com.oreki.stumpd.domain.model

data class RunOutInput(
    val runsCompleted: Int,
    val end: RunOutEnd,
    val whoOut: String
)

data class NoBallBoundaryOutInput(
    val outBatterName: String? = null, // default striker if null
)
