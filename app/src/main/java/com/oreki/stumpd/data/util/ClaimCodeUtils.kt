package com.oreki.stumpd.data.util

import java.security.MessageDigest

/**
 * Utility for securely hashing and verifying claim codes.
 *
 * Claim codes are stored as SHA-256 hashes (salted with groupId) in Firestore
 * so that even if someone inspects the raw Firestore data via browser dev tools,
 * they only see an irreversible hash — not the plaintext recovery code.
 *
 * The plaintext claim code is stored ONLY in the local Room database on the owner's device.
 */
object ClaimCodeUtils {

    /**
     * Hash a claim code using SHA-256 with groupId as salt.
     *
     * The salt prevents rainbow table attacks and ensures the same claim code
     * produces different hashes for different groups.
     *
     * @param claimCode The plaintext claim code (will be uppercased)
     * @param groupId The group ID used as salt
     * @return Hex-encoded SHA-256 hash
     */
    fun hashClaimCode(claimCode: String, groupId: String): String {
        val input = "$groupId:${claimCode.uppercase()}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify a claim code against a stored hash.
     *
     * @param inputCode The claim code entered by the user
     * @param storedHash The SHA-256 hash stored in Firestore
     * @param groupId The group ID used as salt
     * @return true if the input code matches the stored hash
     */
    fun verifyClaimCode(inputCode: String, storedHash: String, groupId: String): Boolean {
        val inputHash = hashClaimCode(inputCode, groupId)
        return inputHash == storedHash
    }

    /**
     * Hash an email address for ownership binding.
     * Uses SHA-256 with a fixed salt so the same email always produces the same hash.
     */
    fun hashEmail(email: String): String {
        val input = "stumpd:${email.lowercase().trim()}"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    /**
     * Verify an email against a stored email hash.
     */
    fun verifyEmailHash(email: String, storedHash: String): Boolean {
        return hashEmail(email) == storedHash
    }
}
