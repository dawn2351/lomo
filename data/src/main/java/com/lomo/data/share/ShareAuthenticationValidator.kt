package com.lomo.data.share

import com.lomo.data.share.LomoShareServer.PrepareRequest
import com.lomo.data.share.LomoShareServer.TransferMetadata
import io.ktor.http.HttpStatusCode

internal class ShareAuthenticationValidator {
    private val nonceLock = Any()
    private val usedAuthNonces = mutableMapOf<String, Long>()

    fun clearNonces() {
        synchronized(nonceLock) {
            usedAuthNonces.clear()
        }
    }

    suspend fun validatePrepareAuthentication(
        request: PrepareRequest,
        resolvePairingKeyHex: suspend () -> String?,
    ): ShareAuthValidation {
        if (!request.e2eEnabled) {
            return ShareAuthValidation(ok = true, keyHex = null)
        }

        val pairingKeyHex = resolvePairingKeyHex()?.trim()
        if (!ShareAuthUtils.isValidKeyHex(pairingKeyHex)) {
            return ShareAuthValidation(
                ok = false,
                status = HttpStatusCode.PreconditionFailed,
                message = "LAN share pairing code is not configured on receiver",
            )
        }
        val keyCandidates = ShareAuthUtils.resolveCandidateKeyHexes(pairingKeyHex)
        if (keyCandidates.isEmpty()) {
            return ShareAuthValidation(
                ok = false,
                status = HttpStatusCode.PreconditionFailed,
                message = "LAN share pairing code is not configured on receiver",
            )
        }
        if (!ShareAuthUtils.isTimestampWithinWindow(request.authTimestampMs)) {
            return ShareAuthValidation(false, HttpStatusCode.Unauthorized, "Expired auth timestamp")
        }
        if (!registerNonce(request.authNonce)) {
            return ShareAuthValidation(false, HttpStatusCode.Forbidden, "Replay detected")
        }

        val payload =
            ShareAuthUtils.buildPreparePayloadToSign(
                senderName = request.senderName,
                encryptedContent = request.encryptedContent,
                contentNonce = request.contentNonce,
                timestamp = request.timestamp,
                attachmentNames = request.attachments.map { it.name.trim() },
                authTimestampMs = request.authTimestampMs,
                authNonce = request.authNonce,
            )
        val verified =
            keyCandidates.firstOrNull { candidate ->
                ShareAuthUtils.verifySignature(
                    keyHex = candidate,
                    payload = payload,
                    providedSignatureHex = request.authSignature,
                )
            }
        return if (verified != null) {
            ShareAuthValidation(true, keyHex = verified)
        } else {
            ShareAuthValidation(false, HttpStatusCode.Unauthorized, "Invalid auth signature")
        }
    }

    suspend fun validateTransferAuthentication(
        metadata: TransferMetadata,
        resolvePairingKeyHex: suspend () -> String?,
    ): ShareAuthValidation {
        if (!metadata.e2eEnabled) {
            return ShareAuthValidation(ok = true, keyHex = null)
        }

        val pairingKeyHex = resolvePairingKeyHex()?.trim()
        if (!ShareAuthUtils.isValidKeyHex(pairingKeyHex)) {
            return ShareAuthValidation(
                ok = false,
                status = HttpStatusCode.PreconditionFailed,
                message = "LAN share pairing code is not configured on receiver",
            )
        }
        val keyCandidates = ShareAuthUtils.resolveCandidateKeyHexes(pairingKeyHex)
        if (keyCandidates.isEmpty()) {
            return ShareAuthValidation(
                ok = false,
                status = HttpStatusCode.PreconditionFailed,
                message = "LAN share pairing code is not configured on receiver",
            )
        }
        if (!ShareAuthUtils.isTimestampWithinWindow(metadata.authTimestampMs)) {
            return ShareAuthValidation(false, HttpStatusCode.Unauthorized, "Expired auth timestamp")
        }
        if (!registerNonce(metadata.authNonce)) {
            return ShareAuthValidation(false, HttpStatusCode.Forbidden, "Replay detected")
        }

        val payload =
            ShareAuthUtils.buildTransferPayloadToSign(
                sessionToken = metadata.sessionToken,
                encryptedContent = metadata.encryptedContent,
                contentNonce = metadata.contentNonce,
                timestamp = metadata.timestamp,
                attachmentNames = metadata.attachmentNames.map { it.trim() },
                authTimestampMs = metadata.authTimestampMs,
                authNonce = metadata.authNonce,
            )
        val verified =
            keyCandidates.firstOrNull { candidate ->
                ShareAuthUtils.verifySignature(
                    keyHex = candidate,
                    payload = payload,
                    providedSignatureHex = metadata.authSignature,
                )
            }
        return if (verified != null) {
            ShareAuthValidation(true, keyHex = verified)
        } else {
            ShareAuthValidation(false, HttpStatusCode.Unauthorized, "Invalid auth signature")
        }
    }

    private fun cleanupExpiredAuthNoncesLocked(nowMs: Long = System.currentTimeMillis()) {
        usedAuthNonces.entries.removeIf { (_, issuedAt) ->
            nowMs - issuedAt > AUTH_NONCE_TTL_MS
        }
    }

    private fun registerNonce(nonce: String): Boolean =
        synchronized(nonceLock) {
            val now = System.currentTimeMillis()
            cleanupExpiredAuthNoncesLocked(now)
            if (usedAuthNonces.containsKey(nonce)) {
                false
            } else {
                if (usedAuthNonces.size >= MAX_AUTH_NONCES_TRACKED) {
                    val oldestKey = usedAuthNonces.minByOrNull { it.value }?.key
                    if (oldestKey != null) {
                        usedAuthNonces.remove(oldestKey)
                    }
                }
                usedAuthNonces[nonce] = now
                true
            }
        }

    private companion object {
        private const val AUTH_NONCE_TTL_MS = 10 * 60 * 1000L
        private const val MAX_AUTH_NONCES_TRACKED = 5000
    }
}
