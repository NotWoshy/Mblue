package com.example.project_whatsappblue

import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.util.Base64
import javax.crypto.KeyAgreement
import android.security.keystore.KeyGenParameterSpec
import org.bouncycastle.crypto.digests.SHA256Digest
import org.bouncycastle.crypto.generators.HKDFBytesGenerator
import org.bouncycastle.crypto.params.HKDFParameters
import java.security.Key
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

fun ecdhSimulate(){
    // inicio de algoritmo
    val kpg = KeyPairGenerator.getInstance("EC")
    kpg.initialize(ECGenParameterSpec("secp256r1"))
    val alice: KeyPair = kpg.generateKeyPair()
    val bob:   KeyPair = kpg.generateKeyPair()
    // esto es lo que se manda
    val publicBytesAlice = alice.public.encoded
    val publicBytesBob = bob.public.encoded
    // se mandan por el output
    // ChatActivity.kt l.104
    // connectedThread?.write(imageJson.toString().toByteArray())

    val kaAlice = KeyAgreement.getInstance("ECDH")
    kaAlice.init(alice.private)

    // esperar hasta tener la pública de la otra persona
    kaAlice.doPhase(bob.public, true)
    val secretAlice = kaAlice.generateSecret()

    // la otra persona hace esto...
    val kaBob = KeyAgreement.getInstance("ECDH")
    kaBob.init(bob.private)
    // e igualmente espera a la pública de alicia
    kaBob.doPhase(alice.public, true)
    val secretBob = kaBob.generateSecret()

    val salt = ByteArray(32) { 0x00 } // aqui se puede escoger al momento algo que ocupes
    val info = "chat session key".toByteArray() // informacion adiccional
    val keyLen = 32 // bytes → 256 bits para AES-256

    // ¡Esta es tu clave final para cifrar!
    val derivedAlice = hkdfDeriveKey(secretAlice, salt, info, keyLen)
    val derivedBob   = hkdfDeriveKey(secretBob, salt, info, keyLen)

    println("P-256 ECDH demo")
    println("Alice shared (base64): ${Base64.getEncoder().encodeToString(derivedAlice)}")
    println("Bob   shared (base64): ${Base64.getEncoder().encodeToString(derivedBob)}")
    println("Coinciden: ${derivedAlice.contentEquals(derivedBob)}")
}

fun hkdfDeriveKey(secret: ByteArray, salt: ByteArray?, info: ByteArray?, length: Int): ByteArray {
    val hkdf = HKDFBytesGenerator(SHA256Digest())
    val params = HKDFParameters(secret, salt, info)
    hkdf.init(params)

    val derivedKey = ByteArray(length)
    hkdf.generateBytes(derivedKey, 0, length)
    return derivedKey
}

val SRG = SecureRandom()

fun cipherMessage(message: String, sessionKey: ByteArray) : ByteArray {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")

    val iv = ByteArray(12)
    // random bytes
    SRG.nextBytes(iv)

    // ????? Kotlin hace las cosas bien chistosas en criptografia
    val keySpec = SecretKeySpec(sessionKey, "AES")

    val gmcSpec = GCMParameterSpec(128, iv)
    cipher.init(Cipher.ENCRYPT_MODE, keySpec, gmcSpec)

    val ciphertext = cipher.doFinal(message.toByteArray(Charsets.UTF_8))
    val output = iv + ciphertext

    return output

}