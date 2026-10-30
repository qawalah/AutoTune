package dev.autotune.ai

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * The user's AI API keys (one per provider), encrypted with a key that never leaves the phone's Android Keystore. Stored apart from
 * the other settings and left out of backups and the diagnostics export.
 */
object KeyVault {
    private const val ALIAS = "autotune_ai_key"
    private const val PREFS = "ai_secret"

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val g = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        g.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build(),
        )
        return g.generateKey()
    }

    // Claude's key was stored without a suffix before Gemini was added (1.13)
    private fun suffix(p: AiProvider) = if (p == AiProvider.CLAUDE) "" else "_" + p.name.lowercase()

    fun save(ctx: Context, p: AiProvider, apiKey: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, secret())
        val enc = c.doFinal(apiKey.trim().toByteArray(Charsets.UTF_8))
        prefs(ctx).edit()
            .putString("iv" + suffix(p), Base64.encodeToString(c.iv, Base64.NO_WRAP))
            .putString("key" + suffix(p), Base64.encodeToString(enc, Base64.NO_WRAP))
            .apply()
    }

    fun load(ctx: Context, p: AiProvider): String? = runCatching {
        val pr = prefs(ctx)
        val iv = Base64.decode(pr.getString("iv" + suffix(p), null) ?: return null, Base64.NO_WRAP)
        val enc = Base64.decode(pr.getString("key" + suffix(p), null) ?: return null, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(128, iv))
        String(c.doFinal(enc), Charsets.UTF_8)
    }.getOrNull()

    fun clear(ctx: Context, p: AiProvider) = prefs(ctx).edit().remove("iv" + suffix(p)).remove("key" + suffix(p)).apply()

    /** "sk-ant-…1234" / "AIzaSyB…x9Qk" for showing which key is saved without showing it. */
    fun masked(key: String?): String? = key?.let { if (it.length > 12) it.take(7) + "…" + it.takeLast(4) else "…" }
}
