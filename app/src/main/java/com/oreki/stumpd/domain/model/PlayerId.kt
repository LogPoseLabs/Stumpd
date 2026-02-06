package com.oreki.stumpd.domain.model

data class PlayerId(val value: String = java.util.UUID.randomUUID().toString())
