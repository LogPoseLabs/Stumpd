package com.oreki.stumpd.data.local.db

import com.oreki.stumpd.domain.model.*
import androidx.room.TypeConverter
import com.oreki.stumpd.data.util.GsonProvider

/**
 * Room TypeConverters for complex data types
 * Uses centralized GsonProvider for JSON serialization
 */
class Converters {
    private val gson = GsonProvider.get()

    @TypeConverter
    fun fromBallFormat(v: BallFormat?): String? = v?.name

    @TypeConverter
    fun toBallFormat(s: String?): BallFormat? = s?.let(BallFormat::valueOf)

    @TypeConverter
    fun fromPlayerId(pid: PlayerId?): String? = pid?.value

    @TypeConverter
    fun toPlayerId(s: String?): PlayerId? = s?.let(::PlayerId)

    @TypeConverter
    fun fromMatchSettings(ms: MatchSettings?): String? = ms?.let(gson::toJson)

    @TypeConverter
    fun toMatchSettings(json: String?): MatchSettings? =
        json?.let { gson.fromJson(it, MatchSettings::class.java) }
}
