package com.oreki.stumpd.data.mappers

import android.util.Log
import com.oreki.stumpd.*
import com.oreki.stumpd.domain.model.*
import com.oreki.stumpd.data.local.entity.GroupDefaultEntity
import com.oreki.stumpd.data.local.entity.GroupEntity
import com.oreki.stumpd.data.util.GsonProvider

// Entity -> UI/domain

/**
 * Converts a GroupEntity to domain PlayerGroup model
 * @param defaults Optional group default settings
 * @param memberIds List of player IDs that are members of this group
 * @param unavailablePlayerIds List of player IDs that are temporarily unavailable
 * @return PlayerGroup domain model
 */
fun GroupEntity.toDomain(
    defaults: GroupDefaultEntity? = null,
    memberIds: List<String> = emptyList(),
    unavailablePlayerIds: List<String> = emptyList()
): PlayerGroup {
    val matchSettings: MatchSettings? = parseMatchSettings(defaults?.matchSettingsJson)
    
    return PlayerGroup(
        id = id,
        name = name,
        playerIds = memberIds,
        unavailablePlayerIds = unavailablePlayerIds,
        defaults = GroupDefaultSettings(
            matchSettings = createMatchSettings(matchSettings, defaults),
            groundName = defaults?.groundName.orEmpty(),
            format = defaults?.format ?: BallFormat.WHITE_BALL.name,
            shortPitch = defaults?.shortPitch ?: false
        )
    )
}

/**
 * Parses JSON string to MatchSettings object
 * @param json JSON string representation of MatchSettings
 * @return Parsed MatchSettings or null if parsing fails
 */
private fun parseMatchSettings(json: String?): MatchSettings? {
    return json?.let {
        try {
            GsonProvider.get().fromJson(it, MatchSettings::class.java)
        } catch (e: Exception) {
            Log.w("GroupMappers", "Failed to parse match settings JSON", e)
            null
        }
    }
}

/**
 * Creates MatchSettings with shortPitch preference applied
 * @param matchSettings Parsed match settings or null
 * @param defaults Group default entity
 * @return MatchSettings with correct shortPitch value
 */
private fun createMatchSettings(
    matchSettings: MatchSettings?,
    defaults: GroupDefaultEntity?
): MatchSettings {
    val baseSettings = matchSettings ?: MatchSettings()
    val shortPitch = defaults?.shortPitch ?: matchSettings?.shortPitch ?: false
    return baseSettings.copy(shortPitch = shortPitch)
}

// UI/domain -> Entity

/**
 * Converts GroupDefaultSettings to GroupDefaultEntity with specified group ID
 * @param groupId The ID of the group these defaults belong to
 * @return GroupDefaultEntity for database storage
 */
fun GroupDefaultSettings.toEntityWithId(groupId: String): GroupDefaultEntity {
    val settingsWithShortPitch = matchSettings.copy(shortPitch = shortPitch)
    return GroupDefaultEntity(
        groupId = groupId,
        groundName = groundName,
        format = format,
        shortPitch = shortPitch,
        matchSettingsJson = GsonProvider.get().toJson(settingsWithShortPitch)
    )
}
