package com.oreki.stumpd.domain.model

// Type alias for backward compatibility - actual class moved to data.storage package
@Deprecated(
    message = "Use com.oreki.stumpd.data.storage.MatchStorageManager instead",
    replaceWith = ReplaceWith("com.oreki.stumpd.data.storage.MatchStorageManager")
)
typealias MatchStorageManager = com.oreki.stumpd.data.storage.MatchStorageManager
