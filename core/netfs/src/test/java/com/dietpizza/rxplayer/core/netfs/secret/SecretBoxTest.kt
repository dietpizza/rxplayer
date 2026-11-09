package com.dietpizza.rxplayer.core.netfs.secret

import java.security.GeneralSecurityException
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** The Keystore isn't on the JVM, so a plain JCE AES key stands in for it. */
private class FakeKeyProvider : SecretKeyProvider {
    private val key: SecretKey = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
    override fun getOrCreateKey(): SecretKey = key
}

class SecretBoxTest {
    private val box = SecretBox(FakeKeyProvider())

    @Test fun `round trips, including unicode`() {
        for (secret in listOf("hunter2", "", "pässwörd 🔑 with spaces")) {
            assertEquals(secret, box.decrypt(box.encrypt(secret)))
        }
    }

    @Test fun `ciphertext is not the plaintext and IVs are fresh`() {
        val a = box.encrypt("same password")
        val b = box.encrypt("same password")
        assertEquals(12, a.iv.size)
        assertFalse(a.iv.contentEquals(b.iv))
        assertFalse(a.ciphertext.contentEquals(b.ciphertext))
        assertFalse(String(a.ciphertext, Charsets.ISO_8859_1).contains("same password"))
    }

    @Test(expected = GeneralSecurityException::class)
    fun `tampering is detected`() {
        val sealed = box.encrypt("secret")
        sealed.ciphertext[0] = (sealed.ciphertext[0].toInt() xor 1).toByte()
        box.decrypt(sealed)
    }

    @Test fun `another key can't decrypt`() {
        val sealed = box.encrypt("secret")
        assertNull(SecretBox(FakeKeyProvider()).decryptOrNull(sealed))
    }

    @Test fun `toString never shows the secret`() {
        assertFalse(box.encrypt("secret").toString().contains("secret"))
    }
}
