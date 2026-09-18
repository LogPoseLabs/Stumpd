package com.oreki.stumpd.domain.tournament

/**
 * Turning a list of teams into a schedule, for each of the four formats.
 *
 * All pure, all deterministic: the same teams in the same seed order always produce the same
 * fixtures, which is what makes a fixture's identity — and therefore its cloud document — stable
 * across re-uploads.
 */

/**
 * Round-robin rounds by the circle method: fix the first team, rotate the rest.
 *
 * An odd team count is padded with a phantom opponent, so every round pairs everyone off and each
 * team takes exactly one bye across the tournament. The alternative — pairing whoever is left —
 * would have somebody playing twice in a round.
 */
fun roundRobinRounds(teams: List<TeamRef>): List<List<Pair<TeamRef, TeamRef?>>> {
    if (teams.size < 2) return emptyList()
    val padded: List<TeamRef?> = if (teams.size % 2 == 0) teams else teams + listOf(null)
    val n = padded.size
    val rotating = padded.toMutableList()
    val rounds = mutableListOf<List<Pair<TeamRef, TeamRef?>>>()

    repeat(n - 1) {
        val pairs = mutableListOf<Pair<TeamRef, TeamRef?>>()
        for (i in 0 until n / 2) {
            val a = rotating[i]
            val b = rotating[n - 1 - i]
            // One of the pair may be the phantom; the real team takes a bye.
            when {
                a != null && b != null -> pairs += a to b
                a != null -> pairs += a to null
                b != null -> pairs += b to null
            }
        }
        rounds += pairs
        // Rotate everything except the first position.
        val moved = rotating.removeAt(1)
        rotating.add(moved)
    }
    return rounds
}

/** Every team plays every other once. */
fun planSingleRoundRobin(
    teams: List<TeamRef>,
    stage: FixtureStage = FixtureStage.LEAGUE,
    poolOrdinal: Int = 0,
    leg: Int = 1,
    matchNumberFrom: Int = 1,
): List<PlannedFixture> {
    var matchNumber = matchNumberFrom
    return roundRobinRounds(teams).flatMapIndexed { roundIndex, pairs ->
        pairs.mapIndexed { slot, (home, away) ->
            val isBye = away == null
            PlannedFixture(
                stage = stage,
                poolOrdinal = poolOrdinal,
                round = roundIndex + 1,
                slot = slot,
                leg = leg,
                homeTeamId = home.teamId,
                awayTeamId = away?.teamId,
                label = if (isBye) "${home.name} — bye" else "Match ${matchNumber++}",
                isBye = isBye,
            )
        }
    }
}

/** Every team plays every other twice, the second leg with the sides reversed. */
fun planDoubleRoundRobin(teams: List<TeamRef>): List<PlannedFixture> {
    val first = planSingleRoundRobin(teams)
    val realFirst = first.count { !it.isBye }
    val second = planSingleRoundRobin(teams, leg = 2, matchNumberFrom = realFirst + 1)
        .map { fixture ->
            // Reverse home and away, so a side that batted first at home doesn't again.
            if (fixture.isBye) {
                fixture
            } else {
                fixture.copy(homeTeamId = fixture.awayTeamId, awayTeamId = fixture.homeTeamId)
            }
        }
    return first + second
}

/**
 * A seeded single-elimination bracket.
 *
 * Padded to the next power of two with byes handed to the top seeds, and round one pairing seed *i*
 * against seed *(size + 1 − i)* — so the two best teams can only meet in the final. Later rounds
 * carry [FixtureSource.WinnerOf] references rather than team ids, because their occupants don't
 * exist yet.
 */
fun planKnockout(teams: List<TeamRef>): List<PlannedFixture> {
    if (teams.size < 2) return emptyList()
    val ordered = teams.sortedBy { it.seed }
    var bracketSize = 1
    while (bracketSize < ordered.size) bracketSize *= 2
    val totalRounds = Integer.numberOfTrailingZeros(bracketSize)

    // Seed positions, best against worst. A missing opponent is a bye for the higher seed.
    val firstRound = (0 until bracketSize / 2).map { slot ->
        val high = ordered.getOrNull(slot)
        val low = ordered.getOrNull(bracketSize - 1 - slot)
        high to low
    }

    val fixtures = mutableListOf<PlannedFixture>()
    var matchNumber = 1
    firstRound.forEachIndexed { slot, (high, low) ->
        if (high == null) return@forEachIndexed
        val isBye = low == null
        fixtures += PlannedFixture(
            stage = FixtureStage.KNOCKOUT,
            poolOrdinal = 0,
            round = 1,
            slot = slot,
            leg = 1,
            homeTeamId = high.teamId,
            awayTeamId = low?.teamId,
            label = if (isBye) {
                "${high.name} — bye"
            } else {
                knockoutRoundLabel(round = 1, totalRounds = totalRounds, slot = slot, matchNumber = matchNumber++)
            },
            isBye = isBye,
        )
    }

    for (round in 2..totalRounds) {
        val slots = bracketSize / (1 shl round)
        for (slot in 0 until slots) {
            fixtures += PlannedFixture(
                stage = FixtureStage.KNOCKOUT,
                poolOrdinal = 0,
                round = round,
                slot = slot,
                leg = 1,
                homeTeamId = null,
                awayTeamId = null,
                homeSource = FixtureSource.WinnerOf(round - 1, slot * 2),
                awaySource = FixtureSource.WinnerOf(round - 1, slot * 2 + 1),
                label = knockoutRoundLabel(round, totalRounds, slot, matchNumber++),
            )
        }
    }
    return fixtures
}

/** "Final", "Semi-final 2", "Quarter-final 1", "Round of 16" — counted back from the final. */
fun knockoutRoundLabel(round: Int, totalRounds: Int, slot: Int, matchNumber: Int = 0): String =
    when (totalRounds - round) {
        0 -> "Final"
        1 -> "Semi-final ${slot + 1}"
        2 -> "Quarter-final ${slot + 1}"
        else -> "Round of ${1 shl (totalRounds - round + 1)}" +
            if (matchNumber > 0) " — Match $matchNumber" else ""
    }

/**
 * Splits teams into pools by a snake draft over the seeds — 1 → A, 2 → B, 3 → B, 4 → A …
 *
 * Straight dealing would stack the strongest seeds in pool A; snaking keeps the pools comparable,
 * which is the point of seeding them at all.
 */
fun allocatePools(teams: List<TeamRef>, poolCount: Int): Map<Int, List<TeamRef>> {
    if (poolCount <= 1) return mapOf(1 to teams.map { it.copy(poolOrdinal = 1) })
    val pools = (1..poolCount).associateWith { mutableListOf<TeamRef>() }
    teams.sortedBy { it.seed }.forEachIndexed { index, team ->
        val row = index / poolCount
        val within = index % poolCount
        val pool = if (row % 2 == 0) within + 1 else poolCount - within
        pools.getValue(pool) += team.copy(poolOrdinal = pool)
    }
    return pools.mapValues { (_, v) -> v.toList() }
}

/**
 * Pool round robins, then a knockout among the qualifiers.
 *
 * The knockout is seeded across pools — the winner of A meets the runner-up of B — so pool winners
 * cannot meet before the final. Those slots start out as [FixtureSource.PoolPosition] references
 * and are filled once their pool has finished.
 */
fun planGroupsAndKnockout(
    teams: List<TeamRef>,
    poolCount: Int,
    advancePerPool: Int,
): List<PlannedFixture> {
    val pools = allocatePools(teams, poolCount)
    var matchNumber = 1
    val poolFixtures = pools.entries.sortedBy { it.key }.flatMap { (ordinal, poolTeams) ->
        val planned = planSingleRoundRobin(
            teams = poolTeams,
            stage = FixtureStage.POOL,
            poolOrdinal = ordinal,
            matchNumberFrom = matchNumber,
        )
        matchNumber += planned.count { !it.isBye }
        planned
    }

    // The qualifiers, in cross-pool order: A1, B2, B1, A2 for two pools, so 1st plays 2nd.
    val qualifiers = mutableListOf<FixtureSource.PoolPosition>()
    for (position in 1..advancePerPool) {
        for (ordinal in 1..poolCount) {
            qualifiers += FixtureSource.PoolPosition(ordinal, position)
        }
    }
    if (qualifiers.size < 2) return poolFixtures

    var bracketSize = 1
    while (bracketSize < qualifiers.size) bracketSize *= 2
    val totalRounds = Integer.numberOfTrailingZeros(bracketSize)

    val knockout = mutableListOf<PlannedFixture>()
    for (slot in 0 until bracketSize / 2) {
        val high = qualifiers.getOrNull(slot)
        val low = qualifiers.getOrNull(bracketSize - 1 - slot)
        if (high == null) continue
        knockout += PlannedFixture(
            stage = FixtureStage.KNOCKOUT,
            poolOrdinal = 0,
            round = 1,
            slot = slot,
            leg = 1,
            homeTeamId = null,
            awayTeamId = null,
            homeSource = high,
            awaySource = low,
            label = knockoutRoundLabel(1, totalRounds, slot, matchNumber++),
            isBye = low == null,
        )
    }
    for (round in 2..totalRounds) {
        val slots = bracketSize / (1 shl round)
        for (slot in 0 until slots) {
            knockout += PlannedFixture(
                stage = FixtureStage.KNOCKOUT,
                poolOrdinal = 0,
                round = round,
                slot = slot,
                leg = 1,
                homeTeamId = null,
                awayTeamId = null,
                homeSource = FixtureSource.WinnerOf(round - 1, slot * 2),
                awaySource = FixtureSource.WinnerOf(round - 1, slot * 2 + 1),
                label = knockoutRoundLabel(round, totalRounds, slot, matchNumber++),
            )
        }
    }
    return poolFixtures + knockout
}

/** The single entry point: a format and some teams in, a schedule out. */
fun planFixtures(
    format: TournamentFormat,
    teams: List<TeamRef>,
    poolCount: Int = 0,
    advancePerPool: Int = 0,
): List<PlannedFixture> = when (format) {
    TournamentFormat.SINGLE_ROUND_ROBIN -> planSingleRoundRobin(teams)
    TournamentFormat.DOUBLE_ROUND_ROBIN -> planDoubleRoundRobin(teams)
    TournamentFormat.KNOCKOUT -> planKnockout(teams)
    TournamentFormat.GROUPS_KNOCKOUT -> planGroupsAndKnockout(teams, poolCount, advancePerPool)
}
