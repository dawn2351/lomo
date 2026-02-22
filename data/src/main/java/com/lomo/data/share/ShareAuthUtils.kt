package com.lomo.data.share

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Shared authentication helpers for LAN share requests.
 *
 * Uses user-provided pairing code (stored as derived key hex) to sign requests with HMAC-SHA256.
 */
internal object ShareAuthUtils {
    private const val LEGACY_KEY_DERIVATION_SALT = "lomo-lan-share-v1"
    private const val KDF_V2_SALT = "lomo-lan-share-v2"
    private const val KDF_V2_ITERATIONS = 120_000
    private const val KDF_V2_BITS = 256
    private const val NONCE_BYTES = 16
    private val KEY_HEX_REGEX = Regex("^[0-9a-fA-F]{64}$")
    private val KEY_MATERIAL_V2_REGEX = Regex("^v2:([0-9a-fA-F]{64}):([0-9a-fA-F]{64})$")
    const val AUTH_WINDOW_MS = 2 * 60 * 1000L
    private val secureRandom = SecureRandom()

    data class ResolvedKeySet(
        val primaryKeyHex: String,
        val candidateKeyHexes: List<String>,
    )

    /**
     * Generates versioned key material from user pairing code.
     *
     * Format:
     * - v2:<pbkdf2_key_hex>:<legacy_sha256_key_hex>
     *
     * The second key keeps compatibility with older peers during transition.
     */
    fun deriveKeyMaterialFromPairingCode(pairingCode: String): String? {
        val normalized = pairingCode.trim()
        if (normalized.length !in 6..64) return null
        val primary = deriveKeyHexV2(normalized)
        val legacy = deriveLegacyKeyHexFromPairingCode(normalized)
        return "v2:$primary:$legacy"
    }

    /**
     * Returns the primary key hex for cryptographic operations.
     * Kept for compatibility with existing call sites/tests.
     */
    fun deriveKeyHexFromPairingCode(pairingCode: String): String? {
        val material = deriveKeyMaterialFromPairingCode(pairingCode) ?: return null
        return resolvePrimaryKeyHex(material)
    }

    fun isValidKeyHex(keyHex: String?): Boolean = resolveKeySet(keyHex) != null

    fun resolvePrimaryKeyHex(keyMaterialOrKeyHex: String?): String? = resolveKeySet(keyMaterialOrKeyHex)?.primaryKeyHex

    fun resolveCandidateKeyHexes(keyMaterialOrKeyHex: String?): List<String> =
        resolveKeySet(keyMaterialOrKeyHex)?.candidateKeyHexes ?: emptyList()

    fun resolveKeySet(keyMaterialOrKeyHex: String?): ResolvedKeySet? {
        val normalized = keyMaterialOrKeyHex?.trim() ?: return null
        if (normalized.isEmpty()) return null

        if (KEY_HEX_REGEX.matches(normalized)) {
            val key = normalized.lowercase()
            return ResolvedKeySet(primaryKeyHex = key, candidateKeyHexes = listOf(key))
        }

        val match = KEY_MATERIAL_V2_REGEX.matchEntire(normalized) ?: return null
        val primary = match.groupValues[1].lowercase()
        val legacy = match.groupValues[2].lowercase()
        val candidates = linkedSetOf(primary, legacy).toList()
        return ResolvedKeySet(primaryKeyHex = primary, candidateKeyHexes = candidates)
    }

    fun generateNonce(): String {
        val bytes = ByteArray(NONCE_BYTES)
        secureRandom.nextBytes(bytes)
        return bytes.toHexString()
    }

    fun isTimestampWithinWindow(
        timestampMs: Long,
        nowMs: Long = System.currentTimeMillis(),
        windowMs: Long = AUTH_WINDOW_MS,
    ): Boolean = kotlin.math.abs(nowMs - timestampMs) <= windowMs

    fun buildPreparePayloadToSign(
        senderName: String,
        encryptedContent: String,
        contentNonce: String,
        timestamp: Long,
        attachmentNames: List<String>,
        authTimestampMs: Long,
        authNonce: String,
    ): String {
        val canonicalAttachments = attachmentNames.map { it.trim() }.sorted()
        return buildString {
            append("prepare\n")
            append(senderName).append('\n')
            append(timestamp).append('\n')
            append(encryptedContent).append('\n')
            append(contentNonce).append('\n')
            canonicalAttachments.forEach { append(it).append('\n') }
            append(authTimestampMs).append('\n')
            append(authNonce)
        }
    }

    fun buildTransferPayloadToSign(
        sessionToken: String,
        encryptedContent: String,
        contentNonce: String,
        timestamp: Long,
        attachmentNames: List<String>,
        authTimestampMs: Long,
        authNonce: String,
    ): String {
        val canonicalAttachments = attachmentNames.map { it.trim() }.sorted()
        return buildString {
            append("transfer\n")
            append(sessionToken).append('\n')
            append(timestamp).append('\n')
            append(encryptedContent).append('\n')
            append(contentNonce).append('\n')
            canonicalAttachments.forEach { append(it).append('\n') }
            append(authTimestampMs).append('\n')
            append(authNonce)
        }
    }

    fun signPayloadHex(
        keyHex: String,
        payload: String,
    ): String {
        val keyBytes = keyHex.hexToBytes()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(keyBytes, "HmacSHA256"))
        return mac.doFinal(payload.toByteArray(Charsets.UTF_8)).toHexString()
    }

    fun verifySignature(
        keyHex: String,
        payload: String,
        providedSignatureHex: String,
    ): Boolean {
        if (!KEY_HEX_REGEX.matches(providedSignatureHex)) return false
        val expected = signPayloadHex(keyHex, payload)
        return MessageDigest.isEqual(
            expected.lowercase().toByteArray(Charsets.UTF_8),
            providedSignatureHex.lowercase().toByteArray(Charsets.UTF_8),
        )
    }

    private fun deriveLegacyKeyHexFromPairingCode(pairingCode: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("$LEGACY_KEY_DERIVATION_SALT:$pairingCode".toByteArray(Charsets.UTF_8))
            .toHexString()

    private fun deriveKeyHexV2(pairingCode: String): String {
        val password = pairingCode.toCharArray()
        val spec =
            PBEKeySpec(
                password,
                KDF_V2_SALT.toByteArray(Charsets.UTF_8),
                KDF_V2_ITERATIONS,
                KDF_V2_BITS,
            )
        return try {
            val encoded =
                SecretKeyFactory
                    .getInstance("PBKDF2WithHmacSHA256")
                    .generateSecret(spec)
                    .encoded
            encoded.toHexString()
        } finally {
            spec.clearPassword()
            password.fill('\u0000')
        }
    }

    private fun ByteArray.toHexString(): String = joinToString("") { "%02x".format(it) }

    private fun String.hexToBytes(): ByteArray {
        require(length % 2 == 0) { "Invalid hex length" }
        return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
