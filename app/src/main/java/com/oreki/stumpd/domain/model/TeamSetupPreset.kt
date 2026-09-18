package com.oreki.stumpd.domain.model

/**
 * A tournament fixture, handed to team setup as one object.
 *
 * One JSON extra rather than a dozen intent keys: the fields only make sense together, and half a
 * preset arriving would be worse than none. It lives in `domain.model` because it is Gson-
 * serialised and the release build's R8 rules keep only a few packages wholesale.
 *
 * The two sides are **home and away as the fixture lists them**, which is not the same as who bats
 * first — that is decided at the toss, in team setup. The ids travel so the finished match can be
 * attributed to real teams rather than matched back by name.
 *
 * Every field is defaulted because Gson bypasses Kotlin constructors: a key missing from a stored
 * blob arrives as null, not as the declared default, so nothing here may be non-null.
 */
data class TeamSetupPreset(
    val tournamentId: String = "",
    val tournamentName: String = "",
    val fixtureId: String = "",
    val fixtureLabel: String = "",
    val groupId: String = "",
    val homeTeamId: String = "",
    val homeTeamName: String = "",
    val homeCaptainPlayerId: String? = null,
    val homePlayerIds: List<String>? = null,
    val awayTeamId: String = "",
    val awayTeamName: String = "",
    val awayCaptainPlayerId: String? = null,
    val awayPlayerIds: List<String>? = null,
) {
    val homeSquad: List<String> get() = homePlayerIds.orEmpty()
    val awaySquad: List<String> get() = awayPlayerIds.orEmpty()

    /** True when the preset carries everything a match needs. A half-preset is ignored. */
    val isUsable: Boolean
        get() = tournamentId.isNotBlank() && fixtureId.isNotBlank() && groupId.isNotBlank() &&
            homeTeamId.isNotBlank() && awayTeamId.isNotBlank() &&
            homeTeamName.isNotBlank() && awayTeamName.isNotBlank() &&
            homeSquad.isNotEmpty() && awaySquad.isNotEmpty()

    /** The team id for a side, by the name it is playing under. */
    fun teamIdFor(teamName: String): String? = when {
        teamName.equals(homeTeamName, ignoreCase = true) -> homeTeamId
        teamName.equals(awayTeamName, ignoreCase = true) -> awayTeamId
        else -> null
    }

    companion object {
        const val INTENT_EXTRA = "team_setup_preset"
    }
}
