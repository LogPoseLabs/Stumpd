package com.oreki.stumpd.data.sync.firebase

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.oreki.stumpd.data.local.entity.TournamentEntity
import com.oreki.stumpd.data.local.entity.TournamentFixtureEntity
import com.oreki.stumpd.data.local.entity.TournamentSquadPlayerEntity
import com.oreki.stumpd.data.local.entity.TournamentTeamEntity
import com.oreki.stumpd.data.sync.FirebaseConfig
import kotlinx.coroutines.tasks.await

/**
 * Tournaments in Firestore, at `groups/{groupId}/tournaments/{tournamentId}`.
 *
 * **One document per tournament**, with teams, squads and fixtures embedded as arrays. A tournament
 * is a few kilobytes against a 1 MiB document limit and is always read and written whole, so this
 * costs one write and one read where per-fixture documents would cost about thirty writes and need
 * an orphan sweep. That matters: the sync manager is full of quota-budget paths.
 *
 * The path is deliberate too. `firestore.rules` already grants `groups/{groupId}/{sub=**}` read to
 * any signed-in user and write only to the group's owner — so this is owner-gated server-side, with
 * no rules change to deploy.
 *
 * The wire format itself lives in [TournamentCloudCodec], where it can be round-tripped by a test.
 */
class FirestoreTournamentDao(
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(),
) {

    companion object {
        private const val TAG = "FirestoreTournamentDao"
        private const val SUBCOLLECTION = "tournaments"
    }

    private fun collection(groupId: String) = firestore
        .collection(FirebaseConfig.COLLECTION_GROUPS)
        .document(groupId)
        .collection(SUBCOLLECTION)

    suspend fun uploadTournament(
        ownerId: String,
        tournament: TournamentEntity,
        teams: List<TournamentTeamEntity>,
        squads: List<TournamentSquadPlayerEntity>,
        fixtures: List<TournamentFixtureEntity>,
    ) {
        val data = TournamentCloudCodec.encode(ownerId, tournament, teams, squads, fixtures)
        collection(tournament.groupId)
            .document(tournament.tournamentId)
            .set(data, SetOptions.merge())
            .await()
        Log.d(TAG, "Uploaded tournament ${tournament.name} (${fixtures.size} fixtures)")
    }

    /**
     * Every tournament of a group.
     *
     * A malformed document is skipped rather than failing the sync: one bad tournament must not
     * stop the rest of a download.
     */
    suspend fun downloadTournaments(groupId: String): List<TournamentCloudData> {
        val snapshot = collection(groupId).get().await()
        return snapshot.documents.mapNotNull { doc ->
            runCatching { TournamentCloudCodec.decode(doc.id, groupId, doc.data.orEmpty()) }
                .onFailure { Log.w(TAG, "Skipping malformed tournament ${doc.id}", it) }
                .getOrNull()
        }
    }

    suspend fun deleteTournament(groupId: String, tournamentId: String) {
        collection(groupId).document(tournamentId).delete().await()
        Log.d(TAG, "Deleted tournament $tournamentId from the cloud")
    }
}
