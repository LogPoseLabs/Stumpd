package com.oreki.stumpd.domain.tournament

/**
 * What a tournament has to satisfy before its fixtures can be generated.
 *
 * Two of these rules aren't fussiness, they are the price of a model where a team is a name and a
 * player is a name: `"TIE"` is reserved because the stored result is either a team's name or that
 * literal, and two players sharing a name inside one tournament would be silently merged by the
 * scoring engine, which de-duplicates batters by name. Both are far likelier now that squads are
 * dealt out of one group rather than picked per match.
 */

/** A problem worth refusing for, with the sentence to show the user. */
data class TournamentProblem(val code: String, val message: String)

/** A team as the setup screen has it, before it is saved. */
data class TeamDraft(
    val name: String,
    val playerIds: List<String>,
    val playerNames: List<String> = emptyList(),
    val captainPlayerId: String? = null,
)

fun validateSetup(
    format: TournamentFormat,
    teamCount: Int,
    squadSize: Int,
    poolCount: Int = 0,
    advancePerPool: Int = 0,
): TournamentProblem? = when {
    teamCount < 2 -> TournamentProblem(
        "TOO_FEW_TEAMS",
        "A tournament needs at least two teams.",
    )

    teamCount > MAX_TEAMS -> TournamentProblem(
        "TOO_MANY_TEAMS",
        "$MAX_TEAMS teams is the most one tournament can hold.",
    )

    squadSize < 2 -> TournamentProblem(
        "SQUAD_TOO_SMALL",
        "A squad needs at least two players.",
    )

    squadSize > MAX_SQUAD -> TournamentProblem(
        "SQUAD_TOO_LARGE",
        "$MAX_SQUAD players a side is the most one tournament can hold.",
    )

    format == TournamentFormat.GROUPS_KNOCKOUT && poolCount < 2 -> TournamentProblem(
        "TOO_FEW_POOLS",
        "Groups and knockout needs at least two groups.",
    )

    format == TournamentFormat.GROUPS_KNOCKOUT && poolCount * 2 > teamCount -> TournamentProblem(
        "POOLS_TOO_THIN",
        "With $teamCount teams there aren't enough for $poolCount groups of two or more.",
    )

    format == TournamentFormat.GROUPS_KNOCKOUT && advancePerPool < 1 -> TournamentProblem(
        "NO_QUALIFIERS",
        "At least one team has to come out of each group.",
    )

    format == TournamentFormat.GROUPS_KNOCKOUT && advancePerPool * poolCount >= teamCount ->
        TournamentProblem(
            "EVERYONE_QUALIFIES",
            "That would put every team into the knockout, so the groups wouldn't decide anything.",
        )

    format == TournamentFormat.GROUPS_KNOCKOUT &&
        advancePerPool > teamCount / poolCount -> TournamentProblem(
        "TOO_MANY_QUALIFIERS",
        "A group of ${teamCount / poolCount} can't send $advancePerPool teams through.",
    )

    else -> null
}

/**
 * Checks the squads well enough to *save* them — not well enough to *play*.
 *
 * Teams are filled in one at a time, on their own screen, and every save sends the whole
 * tournament's drafts because the cross-team rules (a name, a player) can only be checked across
 * all of them. That means this runs every time any single team is saved, most of the time while
 * every other team is still a "Team 2" with nobody in it — and requiring *every* team to already
 * be complete would make it impossible to save the first one. So this only refuses what is wrong
 * regardless of how far setup has got: a blank or reserved name, a squad bigger than it should be,
 * a captain who isn't even in their own squad, or a name or player that collides across teams.
 *
 * Whether the *tournament* is ready — every team full, every team captained — is a separate
 * question, asked by [validateReadyToPlay] right before fixtures are generated.
 */
fun validateTeams(teams: List<TeamDraft>, squadSize: Int): TournamentProblem? {
    teams.forEach { team ->
        if (team.name.isBlank()) {
            return TournamentProblem("BLANK_NAME", "Every team needs a name.")
        }
        if (team.name.trim().equals("TIE", ignoreCase = true)) {
            return TournamentProblem(
                "RESERVED_NAME",
                "\"TIE\" is how a tied match is recorded, so it can't also be a team name.",
            )
        }
        if (team.playerIds.size > squadSize) {
            return TournamentProblem(
                "SQUAD_TOO_BIG",
                "${team.name} has ${team.playerIds.size} players, more than the $squadSize allowed.",
            )
        }
        if (team.captainPlayerId != null && team.captainPlayerId !in team.playerIds) {
            return TournamentProblem(
                "CAPTAIN_NOT_IN_SQUAD",
                "${team.name}'s captain isn't in its squad.",
            )
        }
    }

    val duplicateName = teams
        .groupBy { it.name.trim().lowercase() }
        .entries.firstOrNull { it.value.size > 1 }
    if (duplicateName != null) {
        return TournamentProblem(
            "DUPLICATE_TEAM_NAME",
            "Two teams are called \"${duplicateName.value.first().name}\". " +
                "Results are recorded against the team's name, so they have to differ.",
        )
    }

    val playerInTwoSquads = teams
        .flatMap { team -> team.playerIds.map { it to team.name } }
        .groupBy({ it.first }, { it.second })
        .entries.firstOrNull { it.value.size > 1 }
    if (playerInTwoSquads != null) {
        return TournamentProblem(
            "PLAYER_IN_TWO_SQUADS",
            "One player is in both ${playerInTwoSquads.value[0]} and ${playerInTwoSquads.value[1]}.",
        )
    }

    val duplicatePlayerName = teams
        .flatMap { it.playerNames }
        .filter { it.isNotBlank() }
        .groupBy { it.trim().lowercase() }
        .entries.firstOrNull { it.value.size > 1 }
    if (duplicatePlayerName != null) {
        return TournamentProblem(
            "DUPLICATE_PLAYER_NAME",
            "Two players are called \"${duplicatePlayerName.value.first()}\". " +
                "Scoring tells players apart by name, so one of them would be lost.",
        )
    }

    return null
}

/**
 * Whether the tournament is ready for a schedule.
 *
 * This is the completeness [validateTeams] deliberately doesn't ask for: every team present, with
 * a full squad and a captain. Called once, right before fixtures are generated — the moment the
 * teams stop being a work in progress and become the field the schedule is seeded from.
 */
fun validateReadyToPlay(teams: List<TeamDraft>, squadSize: Int): TournamentProblem? {
    val team = teams.firstOrNull { it.playerIds.size != squadSize || it.captainPlayerId == null }
        ?: return null
    val squadShort = team.playerIds.size != squadSize
    val message = when {
        squadShort && team.captainPlayerId == null ->
            "${team.name} needs ${squadSize - team.playerIds.size} more player(s) and a captain."
        squadShort -> "${team.name} has ${team.playerIds.size} of $squadSize players."
        else -> "${team.name} needs a captain."
    }
    return TournamentProblem("TEAMS_INCOMPLETE", message)
}

const val MAX_TEAMS = 16
const val MAX_SQUAD = 11
