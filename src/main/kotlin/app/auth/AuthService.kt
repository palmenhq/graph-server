package app.auth

import app.utils.toResourceId
import db.tables.pojos.AppUser
import db.tables.pojos.PasswordResetRequest
import io.ktor.server.sessions.SessionStorage
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Duration
import java.time.OffsetDateTime
import java.util.Base64
import java.util.HexFormat
import java.util.UUID

class AuthService(
  private val userRepository: UserRepository,
  private val sessionStorage: SessionStorage,
  private val passwordResetRepository: PasswordResetRepository,
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

  fun createPasswordReset(
    username: String,
    webAppHost: String,
  ): PasswordResetLink? {
    val requestId = UUID.randomUUID().toResourceId("pwrs")
    val token = newToken()
    val tokenHash = hashPasswordResetToken(requestId, token)

    val expiresAt =
      passwordResetRepository.transaction { tx ->
        val user = UserRepository(tx).findByUsernameForUpdate(username) ?: return@transaction null
        val now = OffsetDateTime.now()
        val expiresAt = now.plus(PASSWORD_RESET_TTL)
        val repository = PasswordResetRepository(tx)
        repository.invalidateOpenForUser(user.id!!, now)
        repository.insert(
          PasswordResetRequest(
            id = requestId,
            userId = user.id,
            tokenHash = tokenHash,
            createdAt = now,
            expiresAt = expiresAt,
          ),
        )
        expiresAt
      } ?: return null
    // TODO /welcome or password-reset should link to different urls
    val link = "${webAppHost.trimEnd('/')}/iam/welcome#requestId=$requestId&token=$token"
    return PasswordResetLink(requestId = requestId, link = link, expiresAt = expiresAt)
  }

  fun resetPassword(
    requestId: String,
    token: String,
    newPassword: String,
  ): Boolean {
    val now = OffsetDateTime.now()
    return passwordResetRepository.transaction { tx ->
      val resetRepository = PasswordResetRepository(tx)
      val request = findValidPasswordReset(resetRepository, requestId, token, now) ?: return@transaction false
      val userId = request.userId ?: return@transaction false
      val expiresAt = request.expiresAt ?: return@transaction false

      val txUserRepository = UserRepository(tx)
      if (txUserRepository.findByIdForUpdate(userId) == null) return@transaction false
      val usedAt = OffsetDateTime.now()
      if (!expiresAt.isAfter(usedAt)) return@transaction false
      if (resetRepository.markUsed(requestId, usedAt) != 1) return@transaction false

      txUserRepository.updatePasswordHash(userId, PasswordHasher.hash(newPassword))
    }
  }

  fun validatePasswordReset(
    requestId: String,
    token: String,
  ): Boolean = findValidPasswordReset(passwordResetRepository, requestId, token, OffsetDateTime.now()) != null

  private fun findValidPasswordReset(
    resetRepository: PasswordResetRepository,
    requestId: String,
    token: String,
    now: OffsetDateTime,
  ): PasswordResetRequest? {
    val request = resetRepository.findById(requestId) ?: return null
    val storedTokenHash = request.tokenHash ?: return null
    val expiresAt = request.expiresAt ?: return null
    if (request.usedAt != null || !expiresAt.isAfter(now)) return null
    if (
      !MessageDigest.isEqual(
        storedTokenHash.toByteArray(StandardCharsets.UTF_8),
        hashPasswordResetToken(requestId, token).toByteArray(StandardCharsets.UTF_8),
      )
    ) {
      return null
    }
    return request
  }

  private fun newToken(): String {
    val bytes = ByteArray(32).also(random::nextBytes)
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
  }

  private fun hashPasswordResetToken(
    requestId: String,
    token: String,
  ): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update(requestId.toByteArray(StandardCharsets.UTF_8))
    return HexFormat.of().formatHex(digest.digest(token.toByteArray(StandardCharsets.UTF_8)))
  }

  companion object {
    // TODO shorter for user-triggered password resets?
    val PASSWORD_RESET_TTL: Duration = Duration.ofDays(7)
  }
}

data class PasswordResetLink(
  val requestId: String,
  val link: String,
  val expiresAt: OffsetDateTime,
)
