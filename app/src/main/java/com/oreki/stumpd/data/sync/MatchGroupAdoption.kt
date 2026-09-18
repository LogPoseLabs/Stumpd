package com.oreki.stumpd.data.sync

import com.oreki.stumpd.domain.match.DeliveryOutcome
import com.oreki.stumpd.domain.model.DeliveryUI
import com.oreki.stumpd.domain.model.FallOfWicket
import com.oreki.stumpd.domain.model.MatchHistory
import com.oreki.stumpd.domain.model.Partnership
import com.oreki.stumpd.domain.model.PlayerImpact
import com.oreki.stumpd.domain.model.PlayerMatchStats

data class MergeDestPlayer(
    val id: String,
    val name: String,
)

data class MergeSourcePlayer(
    val normalizedName: String,
    val displayName: String,
)

/**
 * Remaps imported or foreign-device matches into a destination group's player roster.
 */
object MatchGroupAdoption {

    fun normalizePlayerName(name: String): String =
        name.trim().lowercase().replace(Regex("\\s+"), " ")

    fun buildPlayerIdByNormalizedName(players: List<Pair<String, String>>): Map<String, String> {
        val map = linkedMapOf<String, String>()
        for ((id, name) in players) {
            val key = normalizePlayerName(name)
            if (key.isNotEmpty() && key !in map) {
                map[key] = id
            }
        }
        return map
    }

    fun collectPlayerNames(match: MatchHistory): Set<String> {
        val names = linkedSetOf<String>()
        fun add(name: String?) {
            val trimmed = name?.trim().orEmpty()
            if (trimmed.isNotEmpty()) names.add(trimmed)
        }
        fun fromStats(list: List<PlayerMatchStats>) {
            list.forEach { stat ->
                add(stat.name)
                add(stat.bowlerName)
                add(stat.fielderName)
            }
        }
        fromStats(match.firstInningsBatting)
        fromStats(match.firstInningsBowling)
        fromStats(match.secondInningsBatting)
        fromStats(match.secondInningsBowling)
        fromStats(match.team1Players)
        fromStats(match.team2Players)
        match.playerImpacts.forEach { add(it.name) }
        add(match.playerOfTheMatchName)
        add(match.jokerPlayerName)
        add(match.team1CaptainName)
        add(match.team2CaptainName)
        match.firstInningsPartnerships.forEach {
            add(it.batsman1Name)
            add(it.batsman2Name)
        }
        match.secondInningsPartnerships.forEach {
            add(it.batsman1Name)
            add(it.batsman2Name)
        }
        match.firstInningsFallOfWickets.forEach {
            add(it.batsmanName)
            add(it.bowlerName)
            add(it.fielderName)
        }
        match.secondInningsFallOfWickets.forEach {
            add(it.batsmanName)
            add(it.bowlerName)
            add(it.fielderName)
        }
        match.allDeliveries.forEach { delivery ->
            add(delivery.strikerName)
            add(delivery.nonStrikerName)
            add(delivery.bowlerName)
        }
        return names
    }

    fun collectSourcePlayers(matches: List<MatchHistory>): List<MergeSourcePlayer> {
        val byKey = linkedMapOf<String, String>()
        for (match in matches) {
            for (name in collectPlayerNames(match)) {
                val key = normalizePlayerName(name)
                if (key.isNotEmpty() && key !in byKey) {
                    byKey[key] = name.trim()
                }
            }
        }
        return byKey.map { (normalized, display) -> MergeSourcePlayer(normalized, display) }
    }

    fun remapMatchIntoGroup(
        match: MatchHistory,
        destinationGroupId: String,
        destinationGroupName: String,
        playerIdByNormalizedName: Map<String, String>,
    ): MatchHistory {
        val destByName = playerIdByNormalizedName.mapValues { (_, id) ->
            MergeDestPlayer(id = id, name = "")
        }
        return remapMatchForMerge(
            match = match,
            destinationGroupId = destinationGroupId,
            destinationGroupName = destinationGroupName,
            newMatchId = match.id,
            destByNormalizedSourceName = destByName,
        )
    }

    fun remapMatchForMerge(
        match: MatchHistory,
        destinationGroupId: String,
        destinationGroupName: String,
        newMatchId: String,
        destByNormalizedSourceName: Map<String, MergeDestPlayer>,
    ): MatchHistory = remapNames(
        match = match,
        newMatchId = newMatchId,
        destinationGroupId = destinationGroupId,
        destinationGroupName = destinationGroupName,
    ) { name ->
        destByNormalizedSourceName[normalizePlayerName(name)]
            ?: error("No destination group player for name: $name")
    }

    /**
     * Rewrites every player reference in a match through [resolve].
     *
     * A player's identity is scattered across a saved match: the stats rows carry an id, and
     * everything else — dismissal credits, partnerships, fall of wickets, the delivery trail,
     * the joker, both captains, the award — carries a *name*. This is the one traversal that
     * knows all of them, so both "adopt this whole match into my group" and "one player was
     * recorded under the wrong name" go through it rather than each missing a field.
     *
     * [resolve] returns null to leave a name untouched, which is what makes a single-player
     * substitution possible.
     */
    fun remapNames(
        match: MatchHistory,
        newMatchId: String = match.id,
        destinationGroupId: String? = null,
        destinationGroupName: String? = null,
        resolve: (String) -> MergeDestPlayer?,
    ): MatchHistory {
        fun destFor(name: String): MergeDestPlayer =
            resolve(name) ?: MergeDestPlayer(id = "", name = name)

        fun mappedName(name: String): String {
            val dest = destFor(name)
            return dest.name.ifBlank { name }
        }

        fun mappedNameOrNull(name: String?): String? {
            if (name.isNullOrBlank()) return name
            return mappedName(name)
        }

        fun remapStats(list: List<PlayerMatchStats>): List<PlayerMatchStats> =
            list.map { stat ->
                val dest = destFor(stat.name)
                stat.copy(
                    id = dest.id.ifBlank { stat.id },
                    name = dest.name.ifBlank { stat.name },
                    bowlerName = mappedNameOrNull(stat.bowlerName),
                    fielderName = mappedNameOrNull(stat.fielderName),
                )
            }

        fun remapPartnerships(list: List<Partnership>): List<Partnership> =
            list.map { p ->
                p.copy(
                    batsman1Name = mappedName(p.batsman1Name),
                    batsman2Name = mappedName(p.batsman2Name),
                )
            }

        fun remapFow(list: List<FallOfWicket>): List<FallOfWicket> =
            list.map { fow ->
                fow.copy(
                    batsmanName = mappedName(fow.batsmanName),
                    bowlerName = mappedNameOrNull(fow.bowlerName),
                    fielderName = mappedNameOrNull(fow.fielderName),
                )
            }

        fun remapDeliveries(list: List<DeliveryUI>): List<DeliveryUI> =
            list.map { delivery ->
                val embedded = DeliveryOutcome.embeddedOutName(delivery.outcome)
                val remappedOutcome = embedded
                    ?.let { mappedNameOrNull(it) }
                    ?.takeIf { it != embedded }
                    ?.let { DeliveryOutcome.withEmbeddedOutName(delivery.outcome, it) }
                    ?: delivery.outcome
                delivery.copy(
                    outcome = remappedOutcome,
                    strikerName = mappedNameOrNull(delivery.strikerName).orEmpty(),
                    nonStrikerName = mappedNameOrNull(delivery.nonStrikerName).orEmpty(),
                    bowlerName = mappedNameOrNull(delivery.bowlerName).orEmpty(),
                )
            }

        val firstBat = remapStats(match.firstInningsBatting)
        val firstBowl = remapStats(match.firstInningsBowling)
        val secondBat = remapStats(match.secondInningsBatting)
        val secondBowl = remapStats(match.secondInningsBowling)
        val potmName = match.playerOfTheMatchName
        val potmId = potmName?.let { destFor(it).id.ifBlank { match.playerOfTheMatchId } }

        val impacts = match.playerImpacts.map { impact ->
            val dest = destFor(impact.name)
            impact.copy(id = dest.id.ifBlank { impact.id }, name = dest.name.ifBlank { impact.name })
        }

        return match.copy(
            id = newMatchId,
            groupId = destinationGroupId ?: match.groupId,
            groupName = destinationGroupName ?: match.groupName,
            jokerPlayerName = mappedNameOrNull(match.jokerPlayerName),
            team1CaptainName = mappedNameOrNull(match.team1CaptainName),
            team2CaptainName = mappedNameOrNull(match.team2CaptainName),
            firstInningsBatting = firstBat,
            firstInningsBowling = firstBowl,
            secondInningsBatting = secondBat,
            secondInningsBowling = secondBowl,
            team1Players = remapStats(match.team1Players),
            team2Players = remapStats(match.team2Players),
            topBatsman = match.topBatsman?.let { remapStats(listOf(it)).first() },
            topBowler = match.topBowler?.let { remapStats(listOf(it)).first() },
            playerOfTheMatchId = potmId,
            playerOfTheMatchName = mappedNameOrNull(potmName),
            playerImpacts = impacts,
            firstInningsPartnerships = remapPartnerships(match.firstInningsPartnerships),
            secondInningsPartnerships = remapPartnerships(match.secondInningsPartnerships),
            firstInningsFallOfWickets = remapFow(match.firstInningsFallOfWickets),
            secondInningsFallOfWickets = remapFow(match.secondInningsFallOfWickets),
            allDeliveries = remapDeliveries(match.allDeliveries),
        )
    }
}

data class MatchAdoptionResult(
    val adoptedCount: Int,
    val skippedCount: Int,
    val playersAddedToGroup: Int,
    val errors: List<String>,
)

data class PlayerMergeMapping(
    val sourceNormalizedName: String,
    val createNew: Boolean,
    val destinationPlayerId: String? = null,
)
