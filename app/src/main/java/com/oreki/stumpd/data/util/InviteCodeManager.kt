package com.oreki.stumpd.data.util

import kotlin.random.Random

/**
 * Manages generation and validation of group invite codes and claim codes.
 * 
 * Invite codes: 6-character alphanumeric codes for sharing groups
 * Claim codes: 12-character secure codes for ownership recovery
 * 
 * Example invite codes: ABC123, XY7ZPQ, 9K3MNP
 * Example claim codes: ABCD-EFGH-JKLM
 */
object InviteCodeManager {
    
    // Characters used in codes (uppercase letters and digits)
    // Excludes similar-looking characters: 0, O, 1, I, L
    private const val CODE_CHARACTERS = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    private const val INVITE_CODE_LENGTH = 6
    private const val CLAIM_CODE_LENGTH = 12
    
    /**
     * Generate a new random invite code.
     * 
     * @return A 6-character alphanumeric code
     */
    fun generateCode(): String {
        return (1..INVITE_CODE_LENGTH)
            .map { CODE_CHARACTERS[Random.nextInt(CODE_CHARACTERS.length)] }
            .joinToString("")
    }
    
    /**
     * Generate a new random claim code (for ownership recovery).
     * Longer and more secure than invite codes.
     * 
     * @return A 12-character alphanumeric code
     */
    fun generateClaimCode(): String {
        return (1..CLAIM_CODE_LENGTH)
            .map { CODE_CHARACTERS[Random.nextInt(CODE_CHARACTERS.length)] }
            .joinToString("")
    }
    
    /**
     * Validate an invite code format.
     * 
     * @param code The code to validate
     * @return true if the code is a valid format
     */
    fun isValidFormat(code: String): Boolean {
        if (code.length != INVITE_CODE_LENGTH) return false
        return code.all { it in CODE_CHARACTERS }
    }
    
    /**
     * Validate a claim code format.
     * 
     * @param code The code to validate
     * @return true if the code is a valid format
     */
    fun isValidClaimCodeFormat(code: String): Boolean {
        val normalized = code.replace("-", "").replace(" ", "")
        if (normalized.length != CLAIM_CODE_LENGTH) return false
        return normalized.all { it in CODE_CHARACTERS }
    }
    
    /**
     * Normalize a code (uppercase, trim whitespace).
     * 
     * @param code The code to normalize
     * @return Normalized code
     */
    fun normalizeCode(code: String): String {
        return code.trim().uppercase()
    }
    
    /**
     * Normalize a claim code (uppercase, remove dashes/spaces).
     * 
     * @param code The code to normalize
     * @return Normalized code
     */
    fun normalizeClaimCode(code: String): String {
        return code.replace("-", "").replace(" ", "").trim().uppercase()
    }
    
    /**
     * Format an invite code for display (add spaces for readability).
     * 
     * @param code The code to format
     * @return Formatted code like "ABC 123"
     */
    fun formatForDisplay(code: String): String {
        if (code.length != INVITE_CODE_LENGTH) return code
        return "${code.substring(0, 3)} ${code.substring(3)}"
    }
    
    /**
     * Format a claim code for display (add dashes for readability).
     * 
     * @param code The code to format
     * @return Formatted code like "ABCD-EFGH-JKLM"
     */
    fun formatClaimCodeForDisplay(code: String): String {
        val normalized = code.replace("-", "").replace(" ", "")
        if (normalized.length != CLAIM_CODE_LENGTH) return code
        return "${normalized.substring(0, 4)}-${normalized.substring(4, 8)}-${normalized.substring(8)}"
    }
}
