package app.auth

import db.tables.pojos.AppUser
import io.ktor.server.sessions.SessionStorage
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64

class AuthService(
  private val userRepository: UserRepository,
  private val sessionStorage: SessionStorage,
) {
  private val random = SecureRandom()

  // Used to verify a password when the user does not exist, so response times don't reveal valid usernames
  private val dummyHash = PasswordHasher.hash("dummy-password")

  fun createUser(
    username: String,
    password: String,
  ): AppUser {
    val user =
      AppUser().apply {
        this.id = UserRepository.newUserId()
        this.username = username
        this.passwordHash = PasswordHasher.hash(password)
        this.createdAt = OffsetDateTime.now()
      }
    userRepository.insert(user)

    return user
  }

  suspend fun createSession(
    username: String,
    password: String,
  ): String? {
    val user = userRepository.findByUsername(username)
    val valid = PasswordHasher.verify(password, user?.passwordHash ?: dummyHash)
    if (user == null || !valid) return null

    val token = newToken()
    sessionStorage.write(token, userSessionSerializer.serialize(UserSession(userId = user.id!!)))
    return token
  }

  private fun newToken(): String {
    val bytes = ByteArray(32).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }
}
