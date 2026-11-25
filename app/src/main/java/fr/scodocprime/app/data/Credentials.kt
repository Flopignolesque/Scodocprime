package fr.scodocprime.app.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class Credentials(val user: String, val pass: String)

/** Chiffrement AES-256-GCM avec une clé stockée dans le coffre matériel Android (non exportable). */
object Crypto {
    private const val ALIAS = "scodocprime_creds"
    private const val TRANSFORM = "AES/GCM/NoPadding"

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return gen.generateKey()
    }

    fun encrypt(plain: String): String {
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.ENCRYPT_MODE, key())
        val out = c.iv + c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(out, Base64.NO_WRAP)
    }

    fun decrypt(s: String): String? = try {
        val b = Base64.decode(s, Base64.NO_WRAP)
        val c = Cipher.getInstance(TRANSFORM)
        c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, b, 0, 12))
        String(c.doFinal(b, 12, b.size - 12), Charsets.UTF_8)
    } catch (_: Exception) {
        null
    }
}

class CredentialStore(private val prefs: Prefs) {
    fun has(): Boolean = prefs.credUser != null && prefs.credPass != null

    fun save(c: Credentials) {
        prefs.credUser = Crypto.encrypt(c.user)
        prefs.credPass = Crypto.encrypt(c.pass)
    }

    fun user(): String? = prefs.credUser?.let { Crypto.decrypt(it) }

    fun load(): Credentials? {
        val u = prefs.credUser?.let { Crypto.decrypt(it) } ?: return null
        val p = prefs.credPass?.let { Crypto.decrypt(it) } ?: return null
        return Credentials(u, p)
    }

    fun clear() {
        prefs.credUser = null
        prefs.credPass = null
    }

    /** Identifiants à utiliser pour la reconnexion automatique, si activée. */
    fun forAutoLogin(): Credentials? = if (prefs.autoLogin) load() else null
}
