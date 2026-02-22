package com.lomo.data.share

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.MessageDigest

class ShareAuthUtilsTest {
    @Test
    fun `deriveKeyMaterialFromPairingCode returns stable versioned material`() {
        val material1 = ShareAuthUtils.deriveKeyMaterialFromPairingCode("shared-secret-123")
        val material2 = ShareAuthUtils.deriveKeyMaterialFromPairingCode("shared-secret-123")

        assertNotNull(material1)
        assertEquals(material1, material2)
        assertTrue(material1!!.startsWith("v2:"))

        val keySet = ShareAuthUtils.resolveKeySet(material1)
        assertNotNull(keySet)
        assertTrue(keySet!!.primaryKeyHex.matches(Regex("^[0-9a-f]{64}$")))
        assertEquals(2, keySet.candidateKeyHexes.size)
        assertTrue(keySet.candidateKeyHexes.all { it.matches(Regex("^[0-9a-f]{64}$")) })
    }

    @Test
    fun `deriveKeyMaterialFromPairingCode rejects invalid lengths`() {
        assertNull(ShareAuthUtils.deriveKeyMaterialFromPairingCode("short"))
        assertNull(ShareAuthUtils.deriveKeyMaterialFromPairingCode("x".repeat(65)))
    }

    @Test
    fun `resolveKeySet supports v2 and legacy formats`() {
        val pairingCode = "pairing-code-legacy-compat"
        val material = ShareAuthUtils.deriveKeyMaterialFromPairingCode(pairingCode)
        val set = ShareAuthUtils.resolveKeySet(material)
        assertNotNull(set)
        val expectedLegacy = legacyKey(pairingCode)
        assertTrue(set!!.candidateKeyHexes.contains(expectedLegacy))

        val legacyOnly = legacyKey("legacy-only")
        val legacySet = ShareAuthUtils.resolveKeySet(legacyOnly)
        assertNotNull(legacySet)
        assertEquals(legacyOnly, legacySet!!.primaryKeyHex)
        assertEquals(listOf(legacyOnly), legacySet.candidateKeyHexes)
    }

    @Test
    fun `verifySignature succeeds for matching payload and fails for tampered payload`() {
        val keyHex = ShareAuthUtils.deriveKeyHexFromPairingCode("pairing-code-001")!!
        val payload =
            ShareAuthUtils.buildPreparePayloadToSign(
                senderName = "Pixel",
                encryptedContent = "ciphertextA==",
                contentNonce = "nonceA==",
                timestamp = 1234L,
                attachmentNames = listOf("b.png", "a.png"),
                authTimestampMs = 5000L,
                authNonce = "aabbccddeeff0011",
            )
        val signature = ShareAuthUtils.signPayloadHex(keyHex, payload)

        assertTrue(ShareAuthUtils.verifySignature(keyHex, payload, signature))
        assertFalse(ShareAuthUtils.verifySignature(keyHex, "$payload-tampered", signature))
    }

    @Test
    fun `payload canonicalization ignores attachment order`() {
        val payloadA =
            ShareAuthUtils.buildTransferPayloadToSign(
                sessionToken = "token",
                encryptedContent = "ciphertextA==",
                contentNonce = "nonceA==",
                timestamp = 10L,
                attachmentNames = listOf("b.png", "a.png"),
                authTimestampMs = 20L,
                authNonce = "1122334455667788",
            )
        val payloadB =
            ShareAuthUtils.buildTransferPayloadToSign(
                sessionToken = "token",
                encryptedContent = "ciphertextA==",
                contentNonce = "nonceA==",
                timestamp = 10L,
                attachmentNames = listOf("a.png", "b.png"),
                authTimestampMs = 20L,
                authNonce = "1122334455667788",
            )

        assertEquals(payloadA, payloadB)
    }

    @Test
    fun `timestamp window check behaves as expected`() {
        assertTrue(ShareAuthUtils.isTimestampWithinWindow(1_000L, nowMs = 1_500L, windowMs = 1_000L))
        assertFalse(ShareAuthUtils.isTimestampWithinWindow(1_000L, nowMs = 2_500L, windowMs = 1_000L))
    }

    private fun legacyKey(pairingCode: String): String =
        MessageDigest
            .getInstance("SHA-256")
            .digest("lomo-lan-share-v1:${pairingCode.trim()}".toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
}
