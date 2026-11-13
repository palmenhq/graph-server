package app.auth

import db.tables.references.PASSWORD_RESET_REQUEST
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.net.URI
import java.time.OffsetDateTime
import java.util.UUID

class PasswordResetTest {
  private val dsl = TestHelp.dslContext
  private val authService = AuthService(UserRepository(dsl), PostgresSessionStorage(dsl), PasswordResetRepository(dsl))

  private fun newUsername() = "user-${UUID.randomUUID()}"

  private suspend fun ApplicationTestBuilder.createPasswordReset(
    username: String,
    token: String? = TestHelp.SUPERADMIN_SECRET,
  ): HttpResponse =
    client.post("/api/superadmin/users/createPasswordReset") {
      contentType(ContentType.Application.Json)
      if (token != null) {
        header("X-Superadmin-Token", token)
      }
      setBody("""{"username":"$username"}""")
    }

  private suspend fun ApplicationTestBuilder.completePasswordReset(
    requestId: String,
    token: String,
    password: String,
  ): HttpResponse =
    client.post("/auth/password-reset") {
      contentType(ContentType.Application.Json)
      setBody("""{"requestId":"$requestId","token":"$token","password":"$password"}""")
    }

  private suspend fun ApplicationTestBuilder.validatePasswordReset(
    requestId: String,
    token: String,
  ): HttpResponse =
    client.post("/auth/password-reset/validate") {
      contentType(ContentType.Application.Json)
      setBody("""{"requestId":"$requestId","token":"$token"}""")
    }

  private suspend fun ApplicationTestBuilder.login(
    username: String,
    password: String,
  ): HttpResponse =
    client.post("/auth/token") {
      contentType(ContentType.Application.Json)
      setBody("""{"username":"$username","password":"$password"}""")
    }

  private fun tokenFromLink(link: String): String =
    URI(link)
      .rawFragment
      .split('&')
      .map { it.split('=', limit = 2) }
      .associate { it[0] to it[1] }
      .getValue("token")

  @Test
  fun `superadmin reset endpoint requires token and returns not found for unknown users`() =
    TestHelp.testServer {
      val unauthorized = createPasswordReset(newUsername(), token = null)
      assertEquals(HttpStatusCode.Unauthorized, unauthorized.status)

      val blankUsername = createPasswordReset("")
      assertEquals(HttpStatusCode.UnprocessableEntity, blankUsername.status)

      val notFound = createPasswordReset(newUsername())
      assertEquals(HttpStatusCode.NotFound, notFound.status)
      assertTrue(notFound.bodyAsText().contains("user_not_found"))
    }

  @Test
  fun `password reset link changes password and rejects reuse and wrong token`() =
    TestHelp.testServer {
      val username = newUsername()
      TestHelp.authService.createUser(username, "old-password")

      val reset = authService.createPasswordReset(username, TestHelp.WEB_APP_HOST)!!
      assertTrue(reset.link.startsWith("${TestHelp.WEB_APP_HOST}/iam/welcome#requestId=${reset.requestId}&token="))
      val token = tokenFromLink(reset.link)
      val storedHash =
        dsl
          .select(PASSWORD_RESET_REQUEST.TOKEN_HASH)
          .from(PASSWORD_RESET_REQUEST)
          .where(PASSWORD_RESET_REQUEST.ID.eq(reset.requestId))
          .fetchOne(PASSWORD_RESET_REQUEST.TOKEN_HASH)!!
      assertNotEquals(token, storedHash)
      assertEquals(64, storedHash.length)

      val unknownRequest = completePasswordReset("unknown-request-id", token, "new-password")
      assertEquals(HttpStatusCode.UnprocessableEntity, unknownRequest.status)
      assertTrue(unknownRequest.bodyAsText().contains("invalid_reset_token"))

      val blankPassword = completePasswordReset(reset.requestId, token, "")
      assertEquals(HttpStatusCode.UnprocessableEntity, blankPassword.status)

      val wrongToken = completePasswordReset(reset.requestId, "wrong-token", "new-password")
      assertEquals(HttpStatusCode.UnprocessableEntity, wrongToken.status)
      assertTrue(wrongToken.bodyAsText().contains("invalid_reset_token"))

      val completed = completePasswordReset(reset.requestId, token, "new-password")
      assertEquals(HttpStatusCode.NoContent, completed.status)
      assertEquals(HttpStatusCode.Unauthorized, login(username, "old-password").status)
      assertEquals(HttpStatusCode.OK, login(username, "new-password").status)

      val reused = completePasswordReset(reset.requestId, token, "another-password")
      assertEquals(HttpStatusCode.UnprocessableEntity, reused.status)
      assertTrue(reused.bodyAsText().contains("invalid_reset_token"))
    }

  @Test
  fun `expired reset request is rejected`() =
    TestHelp.testServer {
      val username = newUsername()
      TestHelp.authService.createUser(username, "old-password")
      val reset = authService.createPasswordReset(username, TestHelp.WEB_APP_HOST)!!
      val token = tokenFromLink(reset.link)

      dsl
        .update(PASSWORD_RESET_REQUEST)
        .set(PASSWORD_RESET_REQUEST.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1))
        .where(PASSWORD_RESET_REQUEST.ID.eq(reset.requestId))
        .execute()

      val response = completePasswordReset(reset.requestId, token, "new-password")
      assertEquals(HttpStatusCode.UnprocessableEntity, response.status)
      assertTrue(response.bodyAsText().contains("invalid_reset_token"))
    }

  @Test
  fun `password reset validation does not consume a valid request and rejects invalid requests`() =
    TestHelp.testServer {
      val username = newUsername()
      TestHelp.authService.createUser(username, "old-password")

      val validReset = authService.createPasswordReset(username, TestHelp.WEB_APP_HOST)!!
      val validToken = tokenFromLink(validReset.link)

      val unknown = validatePasswordReset("unknown-request-id", validToken)
      assertEquals(HttpStatusCode.UnprocessableEntity, unknown.status)
      assertTrue(unknown.bodyAsText().contains("invalid_reset_token"))

      val wrongToken = validatePasswordReset(validReset.requestId, "wrong-token")
      assertEquals(HttpStatusCode.UnprocessableEntity, wrongToken.status)
      assertTrue(wrongToken.bodyAsText().contains("invalid_reset_token"))

      val valid = validatePasswordReset(validReset.requestId, validToken)
      assertEquals(HttpStatusCode.NoContent, valid.status)

      val completed = completePasswordReset(validReset.requestId, validToken, "new-password")
      assertEquals(HttpStatusCode.NoContent, completed.status)

      val used = validatePasswordReset(validReset.requestId, validToken)
      assertEquals(HttpStatusCode.UnprocessableEntity, used.status)
      assertTrue(used.bodyAsText().contains("invalid_reset_token"))

      val expiredReset = authService.createPasswordReset(username, TestHelp.WEB_APP_HOST)!!
      val expiredToken = tokenFromLink(expiredReset.link)
      dsl
        .update(PASSWORD_RESET_REQUEST)
        .set(PASSWORD_RESET_REQUEST.EXPIRES_AT, OffsetDateTime.now().minusMinutes(1))
        .where(PASSWORD_RESET_REQUEST.ID.eq(expiredReset.requestId))
        .execute()

      val expired = validatePasswordReset(expiredReset.requestId, expiredToken)
      assertEquals(HttpStatusCode.UnprocessableEntity, expired.status)
      assertTrue(expired.bodyAsText().contains("invalid_reset_token"))
    }

  @Test
  fun `new reset request invalidates earlier requests`() {
    val username = newUsername()
    TestHelp.authService.createUser(username, "old-password")

    val first = authService.createPasswordReset(username, TestHelp.WEB_APP_HOST)!!
    val second = authService.createPasswordReset(username, TestHelp.WEB_APP_HOST)!!

    assertFalse(authService.resetPassword(first.requestId, tokenFromLink(first.link), "first-reset-password"))
    assertTrue(authService.resetPassword(second.requestId, tokenFromLink(second.link), "second-reset-password"))
  }

  @Test
  fun `superadmin response contains no token`() =
    TestHelp.testServer {
      val username = newUsername()
      TestHelp.authService.createUser(username, "old-password")

      val response = createPasswordReset(username)
      assertEquals(HttpStatusCode.Created, response.status)
      val body = Json.parseToJsonElement(response.bodyAsText()).jsonObject
      assertNotNull(body["requestId"]?.jsonPrimitive?.content)
      assertNotNull(body["expiresAt"]?.jsonPrimitive?.content)
      assertFalse(body.containsKey("token"))
    }
}
