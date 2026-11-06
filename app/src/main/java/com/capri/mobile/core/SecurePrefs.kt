package com.capri.mobile.core

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** API keys never touch plain SharedPreferences: they are AES/GCM encrypted with a key held by Android Keystore. */
class SecurePrefs(context: Context) {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences("capri_secure", Context.MODE_PRIVATE)

    @Volatile private var cached: String? = null

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getEntry(ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }
        val gen = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        val builder = android.security.keystore.KeyGenParameterSpec.Builder(
            ALIAS, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                android.security.keystore.KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        gen.init(builder)
        return gen.generateKey()
    }

    fun putApiKey(value: String) {
        cached = value.trim()
        if (value.isBlank()) {
            prefs.edit().remove(KEY_API).apply()
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            cipher.init(Cipher.ENCRYPT_MODE, key())
            val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
            val blob = ByteArray(1 + cipher.iv.size + encrypted.size)
            blob[0] = cipher.iv.size.toByte()
            cipher.iv.copyInto(blob, 1)
            encrypted.copyInto(blob, 1 + cipher.iv.size)
            prefs.edit().putString(KEY_API, Base64.encodeToString(blob, Base64.NO_WRAP)).apply()
        } catch (_: Exception) {
            // A broken/absent Keystore must not crash the app; the key stays usable for this process only.
            prefs.edit().remove(KEY_API).apply()
        }
    }

    fun apiKey(): String {
        cached?.let { return it }
        val raw = prefs.getString(KEY_API, null) ?: run { cached = ""; return "" }
        return try {
            val blob = Base64.decode(raw, Base64.NO_WRAP)
            val ivLen = blob[0].toInt()
            val iv = blob.copyOfRange(1, 1 + ivLen)
            val data = blob.copyOfRange(1 + ivLen, blob.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(cipher.doFinal(data), Charsets.UTF_8).also { cached = it }
        } catch (_: Exception) {
            cached = ""
            ""
        }
    }

    fun clear() {
        cached = ""
        prefs.edit().clear().apply()
    }

    private companion object {
        const val ALIAS = "capri_master_key"
        const val KEY_API = "api_key"
    }
}
