package app.auth

import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * PBKDF2-HMAC-SHA256 password hashing. Hashes are encoded as `pbkdf2_sha256$<iterations>$<salt>$<hash>`.
 */
object PasswordHasher {
  private const val ALGORITHM = "PBKDF2WithHmacSHA256"
  private const val PREFIX = "pbkdf2_sha256"
  private const val ITERATIONS = 310_000
  private const val SALT_BYTES = 16
  private const val KEY_BITS = 256

  private val random = SecureRandom()
  private val encoder = Base64.getEncoder().withoutPadding()
  private val decoder = Base64.getDecoder()

  fun hash(password: String): String {
    val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
    val hash = derive(password, salt, ITERATIONS)
    return listOf(PREFIX, ITERATIONS.toString(), encoder.encodeToString(salt), encoder.encodeToString(hash))
      .joinToString("$")
  }

  fun verify(
    password: String,
    encoded: String,
  ): Boolean {
    val parts = encoded.split("$")
    if (parts.size != 4 || parts[0] != PREFIX) return false
    val iterations = parts[1].toIntOrNull() ?: return false
    val salt = decoder.decode(parts[2])
    val expected = decoder.decode(parts[3])
    return MessageDigest.isEqual(expected, derive(password, salt, iterations))
  }

  private fun derive(
    password: String,
    salt: ByteArray,
    iterations: Int,
  ): ByteArray {
    val spec = PBEKeySpec(password.toCharArray(), salt, iterations, KEY_BITS)
    try {
      return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
    } finally {
      spec.clearPassword()
    }
  }
}
