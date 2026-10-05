package app.auth

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Duration
import java.util.UUID

class AuthTest {
  private val dsl = TestHelp.dslContext
  private val authService = AuthService(UserRepository(dsl), PostgresSessionStorage(dsl))

  private fun newUsername() = "user-${UUID.randomUUID()}"

  private suspend fun ApplicationTestBuilder.requestToken(
    username: String,
    password: String,
  ) = client.post("/auth/token") {
    contentType(ContentType.Application.Json)
    setBody("""{"username":"$username","password":"$password"}""")
  }

  @Test
  fun `hashes and verifies passwords`() {
    val hash = PasswordHasher.hash("secret")

    assertFalse(hash.contains("secret"))
    assertTrue(PasswordHasher.verify("secret", hash))
    assertFalse(PasswordHasher.verify("wrong", hash))
    assertFalse(PasswordHasher.verify("secret", "garbage"))
  }

  @Test
  fun `creates session only for valid credentials`() =
    runBlocking {
      val username = newUsername()
      authService.createUser(username, "secret")

      assertNotNull(authService.createSession(username, "secret"))
      assertNull(authService.createSession(username, "wrong"))
      assertNull(authService.createSession(newUsername(), "secret"))
    }

  @Test
  fun `storage does not return expired or invalidated sessions`() =
    runBlocking<Unit> {
      val expired = PostgresSessionStorage(dsl, ttl = Duration.ofSeconds(-1))
      val expiredId = "expired-${UUID.randomUUID()}"
      expired.write(expiredId, "data")
      assertThrows(NoSuchElementException::class.java) { runBlocking { expired.read(expiredId) } }

      val storage = PostgresSessionStorage(dsl)
      val id = "valid-${UUID.randomUUID()}"
      storage.write(id, "data")
      assertEquals("data", storage.read(id))
      storage.invalidate(id)
      assertThrows(NoSuchElementException::class.java) { runBlocking { storage.read(id) } }
    }

  @Test
  fun `obtains token and uses it to access protected routes`() =
    TestHelp.testServer {
      val username = newUsername()
      authService.createUser(username, "secret")

      val tokenResponse = requestToken(username, "secret")
      assertEquals(HttpStatusCode.OK, tokenResponse.status)
      val token =
        Json
          .parseToJsonElement(tokenResponse.bodyAsText())
          .jsonObject["token"]!!
          .jsonPrimitive.content

      val authorized = client.get("/api/workspaces/getAll") { header(SESSION_HEADER, token) }
      assertEquals(HttpStatusCode.OK, authorized.status)
    }

  @Test
  fun `rejects invalid credentials`() =
    TestHelp.testServer {
      val username = newUsername()
      authService.createUser(username, "secret")

      val response = requestToken(username, "wrong")
      assertEquals(HttpStatusCode.Unauthorized, response.status)
      assertTrue(response.bodyAsText().contains("invalid_credentials"))
    }

  @Test
  fun `rejects requests without a valid session`() =
    TestHelp.testServer {
      val missing = client.get("/api/workspaces/getAll")
      assertEquals(HttpStatusCode.Unauthorized, missing.status)
      assertTrue(missing.bodyAsText().contains("unauthorized"))

      val invalid = client.get("/api/workspaces/getAll") { header(SESSION_HEADER, "not-a-token") }
      assertEquals(HttpStatusCode.Unauthorized, invalid.status)
    }
}
