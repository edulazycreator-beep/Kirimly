package com.kirimly.app.network

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.PublicKey
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * End-to-end encryption for Android ↔ Android chunks: ephemeral ECDH (P-256) key agreement,
 * SHA-256 key derivation bound to the upload context, and AES-256-GCM per chunk
 * (12-byte random IV prefixed to each ciphertext). Protects transfers from passive sniffing
 * on shared Wi-Fi. It does not authenticate the peer beyond the PIN / approval flow.
 */
object TransferCrypto {

    private const val CURVE = "secp256r1"
    private const val IV_BYTES = 12
    private const val TAG_BITS = 128
    private val random = SecureRandom()

    fun generateKeyPair(): KeyPair = KeyPairGenerator.getInstance("EC").apply {
        initialize(ECGenParameterSpec(CURVE), random)
    }.generateKeyPair()

    fun encodePublicKey(key: PublicKey): String = Base64.encodeToString(key.encoded, Base64.NO_WRAP)

    fun decodePublicKey(encoded: String): PublicKey =
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(encoded, Base64.NO_WRAP)))

    fun deriveKey(privateKey: PrivateKey, peerPublicKey: PublicKey, context: String): SecretKeySpec {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(privateKey)
        agreement.doPhase(peerPublicKey, true)
        val secret = agreement.generateSecret()
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update("kirimly-v2".toByteArray(Charsets.UTF_8))
        digest.update(secret)
        digest.update(context.toByteArray(Charsets.UTF_8))
        return SecretKeySpec(digest.digest(), "AES")
    }

    fun encrypt(key: SecretKeySpec, plain: ByteArray, length: Int = plain.size): ByteArray {
        val iv = ByteArray(IV_BYTES).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, iv))
        val sealed = cipher.doFinal(plain, 0, length)
        return iv + sealed
    }

    fun decrypt(key: SecretKeySpec, data: ByteArray): ByteArray {
        require(data.size > IV_BYTES) { "Chunk terenkripsi terlalu pendek" }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, data, 0, IV_BYTES))
        return cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES)
    }

    /** Six-digit code from both public keys (order-independent), shown on both screens as "123 456". */
    fun verificationCode(publicKeyA: String, publicKeyB: String): String {
        val (first, second) = if (publicKeyA <= publicKeyB) publicKeyA to publicKeyB else publicKeyB to publicKeyA
        val digest = MessageDigest.getInstance("SHA-256").digest((first + "|" + second).toByteArray(Charsets.UTF_8))
        val value = ((digest[0].toLong() and 0xFF) shl 24) or ((digest[1].toLong() and 0xFF) shl 16) or
            ((digest[2].toLong() and 0xFF) shl 8) or (digest[3].toLong() and 0xFF)
        val code = (value % 1_000_000L).toString().padStart(6, '0')
        return code.substring(0, 3) + " " + code.substring(3)
    }

    fun sha256Hex(bytes: ByteArray, length: Int = bytes.size): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes, 0, length)
        return toHex(digest.digest())
    }

    fun toHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2)
        val alphabet = "0123456789abcdef"
        for (i in bytes.indices) {
            val v = bytes[i].toInt() and 0xFF
            chars[i * 2] = alphabet[v ushr 4]
            chars[i * 2 + 1] = alphabet[v and 0x0F]
        }
        return String(chars)
    }
}
