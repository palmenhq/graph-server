package app.superadmin

import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.ApplicationTestBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class SuperadminTest {
  private fun newUsername() = "user-${UUID.randomUUID()}"

  private suspend fun ApplicationTestBuilder.createUser(
    username: String,
    password: String,
    token: String? = TestHelp.SUPERADMIN_SECRET,
    baseUrl: String = TestHelp.SUPERADMIN_URL,
  ): HttpResponse =
    client.post("$baseUrl/api/superadmin/users/createUser") {
      contentType(ContentType.Application.Json)
      if (token != null) header(SUPERADMIN_TOKEN_HEADER, token)
      setBody("""{"username":"$username","password":"$password"}""")
    }

  private suspend fun ApplicationTestBuilder.login(
    username: String,
    password: String,
  ): HttpResponse =
    client.post("/auth/token") {
      contentType(ContentType.Application.Json)
      setBody("""{"username":"$username","password":"$password"}""")
    }

  @Test
  fun `creates a user that can log in`() =
    TestHelp.testServer {
      val username = newUsername()

      val response = createUser(username, "secret")
      assertEquals(HttpStatusCode.Created, response.status)
      val body = response.bodyAsText()
      assertTrue(body.contains(username))
      assertFalse(body.contains("secret"))

      assertEquals(HttpStatusCode.OK, login(username, "secret").status)
    }

  @Test
  fun `rejects duplicate usernames`() =
    TestHelp.testServer {
      val username = newUsername()
      assertEquals(HttpStatusCode.Created, createUser(username, "secret").status)

      val duplicate = createUser(username, "other")
      assertEquals(HttpStatusCode.Conflict, duplicate.status)
      assertTrue(duplicate.bodyAsText().contains("username_taken"))
    }

  @Test
  fun `rejects blank credentials`() =
    TestHelp.testServer {
      assertEquals(HttpStatusCode.UnprocessableEntity, createUser("", "secret").status)
      assertEquals(HttpStatusCode.UnprocessableEntity, createUser(newUsername(), "").status)
    }

  @Test
  fun `rejects missing or wrong superadmin token`() =
    TestHelp.testServer {
      val username = newUsername()

      assertEquals(HttpStatusCode.Unauthorized, createUser(username, "secret", token = null).status)
      assertEquals(HttpStatusCode.Unauthorized, createUser(username, "secret", token = "wrong").status)
      assertEquals(HttpStatusCode.Unauthorized, login(username, "secret").status)
    }

  @Test
  fun `rejects all requests when no secret is configured`() =
    TestHelp.testServer(superadminTokenSecret = null) {
      assertEquals(HttpStatusCode.Unauthorized, createUser(newUsername(), "secret", token = "").status)
      assertEquals(HttpStatusCode.Unauthorized, createUser(newUsername(), "secret", token = "null").status)
    }

  @Test
  fun `superadmin api is only served on the superadmin port`() =
    TestHelp.testServer {
      // Not reachable on the public port
      assertEquals(HttpStatusCode.NotFound, createUser(newUsername(), "secret", baseUrl = "").status)

      // Public API is not reachable on the superadmin port
      assertEquals(HttpStatusCode.NotFound, client.get("${TestHelp.SUPERADMIN_URL}/api/workspaces/getAll").status)
      assertEquals(HttpStatusCode.NotFound, client.get("${TestHelp.SUPERADMIN_URL}/openapi.json").status)

      // Superadmin routes are not part of the public documentation
      val openApi = client.get("/openapi.json")
      assertEquals(HttpStatusCode.OK, openApi.status)
      assertFalse(openApi.bodyAsText().contains("superadmin"))
    }
}
