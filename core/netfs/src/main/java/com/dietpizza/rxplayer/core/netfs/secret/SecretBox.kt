package com.dietpizza.rxplayer.core.netfs.secret

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/** Supplies the AES key [SecretBox] seals with. Production: [KeystoreKeyProvider]; tests: a plain JCE key. */
interface SecretKeyProvider {
    fun getOrCreateKey(): SecretKey
}

/**
 * An AES-256-GCM key in the Android Keystore under [ALIAS]. The key material never leaves the
 * Keystore (TEE/StrongBox), and Keystore insists on generating the IV itself (randomized encryption).
 */
@Singleton
class KeystoreKeyProvider @Inject constructor() : SecretKeyProvider {

    @Synchronized
    override fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    companion object {
        const val ALIAS = "rx_netfs"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
    }
}

/** AES-GCM ciphertext (tag appended) and its 12-byte IV, as stored in `secret_ct` / `secret_iv`. */
class Sealed(val ciphertext: ByteArray, val iv: ByteArray) {
    override fun toString(): String = "Sealed(${ciphertext.size} B)"
}

/**
 * Encrypts server passwords. Nothing here logs or stringifies plaintext.
 *
 * The associated data binds the ciphertext to this purpose/version, so a blob copied from some
 * other Keystore use can't be decrypted as a password.
 */
@Singleton
class SecretBox @Inject constructor(private val keys: SecretKeyProvider) {

    fun encrypt(plaintext: String): Sealed {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, keys.getOrCreateKey())
        cipher.updateAAD(AAD)
        val ct = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return Sealed(ct, cipher.iv)
    }

    /** @throws GeneralSecurityException if the blob was tampered with or the key is gone. */
    fun decrypt(sealed: Sealed): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, keys.getOrCreateKey(), GCMParameterSpec(TAG_BITS, sealed.iv))
        cipher.updateAAD(AAD)
        return String(cipher.doFinal(sealed.ciphertext), Charsets.UTF_8)
    }

    /** [decrypt], or null when it can't be decrypted (e.g. Keystore wiped by a restore). */
    fun decryptOrNull(sealed: Sealed): String? =
        try {
            decrypt(sealed)
        } catch (_: GeneralSecurityException) {
            null
        }

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val TAG_BITS = 128
        val AAD = "rx_netfs:v1".toByteArray(Charsets.UTF_8)
    }
}
