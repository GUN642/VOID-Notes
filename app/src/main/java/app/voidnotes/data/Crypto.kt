package app.voidnotes.data

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** End-to-end encryption of shared notes: AES-256-GCM, the key only lives in the share link. */
object Crypto {
    private val random = SecureRandom()
    private const val FLAGS = Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING

    fun randomBytes(n: Int) = ByteArray(n).also { random.nextBytes(it) }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, FLAGS)
    fun unb64(s: String): ByteArray = Base64.decode(s, FLAGS)

    fun newKey(): String = b64(randomBytes(32))
    fun newId(): String = b64(randomBytes(16))

    fun encrypt(key: String, plain: ByteArray): String {
        val iv = randomBytes(12)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(unb64(key), "AES"), GCMParameterSpec(128, iv))
        return b64(iv + c.doFinal(plain))
    }

    fun decrypt(key: String, data: String): ByteArray {
        val raw = unb64(data)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(unb64(key), "AES"), GCMParameterSpec(128, raw, 0, 12))
        return c.doFinal(raw, 12, raw.size - 12)
    }

    fun encryptText(key: String, text: String) = encrypt(key, text.toByteArray(Charsets.UTF_8))
    fun decryptText(key: String, data: String) = String(decrypt(key, data), Charsets.UTF_8)

    fun hash(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8)).take(12).joinToString("") { "%02x".format(it) }
}
