package com.oreki.stumpd.data.util

import com.google.gson.Gson
import com.google.gson.GsonBuilder

/**
 * Centralized Gson provider to avoid creating multiple instances
 * throughout the application. Thread-safe singleton pattern.
 */
object GsonProvider {
    private val gsonInstance: Gson by lazy {
        GsonBuilder()
            .setPrettyPrinting()
            .create()
    }

    /**
     * Returns the shared Gson instance for JSON serialization/deserialization
     */
    fun get(): Gson = gsonInstance
}



