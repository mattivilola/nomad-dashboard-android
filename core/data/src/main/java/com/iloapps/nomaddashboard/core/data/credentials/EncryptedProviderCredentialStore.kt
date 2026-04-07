package com.iloapps.nomaddashboard.core.data.credentials

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.core.content.edit
import com.iloapps.nomaddashboard.core.common.IoDispatcher
import com.iloapps.nomaddashboard.core.model.ProviderCredentialSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

@Singleton
class EncryptedProviderCredentialStore @Inject constructor(
    @ApplicationContext context: Context,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ProviderCredentialStore {
    private val sharedPreferences =
        context.getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)

    private val _credentials = MutableStateFlow(loadCredentials())
    override val credentials: StateFlow<ProviderCredentialSettings> = _credentials.asStateFlow()

    override suspend fun update(transform: (ProviderCredentialSettings) -> ProviderCredentialSettings) {
        withContext(ioDispatcher) {
            val updated = transform(loadCredentials()).normalized()
            sharedPreferences.edit {
                putEncryptedString(TANKERKOENIG_API_KEY, updated.tankerkoenigApiKey)
            }
            _credentials.value = updated
        }
    }

    private fun loadCredentials(): ProviderCredentialSettings =
        ProviderCredentialSettings(
            tankerkoenigApiKey = sharedPreferences.getEncryptedString(TANKERKOENIG_API_KEY).orEmpty(),
        ).normalized()

    private fun ProviderCredentialSettings.normalized(): ProviderCredentialSettings =
        copy(
            tankerkoenigApiKey = tankerkoenigApiKey.trim(),
        )

    private fun android.content.SharedPreferences.Editor.putEncryptedString(
        key: String,
        value: String,
    ) {
        if (value.isBlank()) {
            remove(key)
            return
        }

        putString(key, encrypt(value))
    }

    private fun android.content.SharedPreferences.getEncryptedString(key: String): String? =
        getString(key, null)?.let(::decrypt)

    private fun encrypt(value: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateSecretKey())
        val ciphertext = cipher.doFinal(value.toByteArray(StandardCharsets.UTF_8))
        val iv = cipher.iv ?: error("Missing IV for encrypted provider credential.")
        return "${iv.encodeBase64()}:${ciphertext.encodeBase64()}"
    }

    private fun decrypt(payload: String): String? {
        val parts = payload.split(':', limit = 2)
        if (parts.size != 2) {
            return null
        }

        return runCatching {
            val iv = parts[0].decodeBase64()
            val ciphertext = parts[1].decodeBase64()
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateSecretKey(),
                GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv),
            )
            String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    private fun getOrCreateSecretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(KEY_SIZE_BITS)
            .setUserAuthenticationRequired(false)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private fun ByteArray.encodeBase64(): String =
        Base64.getEncoder().encodeToString(this)

    private fun String.decodeBase64(): ByteArray =
        Base64.getDecoder().decode(this)

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val GCM_TAG_LENGTH_BITS = 128
        const val KEY_ALIAS = "nomad-provider-credentials"
        const val KEY_SIZE_BITS = 256
        const val SHARED_PREFERENCES_NAME = "nomad-provider-credentials"
        const val TANKERKOENIG_API_KEY = "tankerkoenig_api_key"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
    }
}
