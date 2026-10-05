package app.workspaces

import app.auth.SESSION_HEADER
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WorkspaceAccessTest {
  private fun HttpResponse.field(name: String): suspend () -> String =
    {
      Json
        .parseToJsonElement(bodyAsText())
        .jsonObject[name]!!
        .jsonPrimitive.content
    }

  private suspend fun ApplicationTestBuilder.login(): String {
    val user = TestHelp.createUser(password = "secret")
    val response =
      client.post("/auth/token") {
        contentType(ContentType.Application.Json)
        setBody("""{"username":"${user.username}","password":"secret"}""")
      }
    return response.field("token")()
  }

  private suspend fun ApplicationTestBuilder.createWorkspace(
    token: String,
    name: String,
  ): String {
    val response =
      client.post("/api/workspaces/create") {
        header(SESSION_HEADER, token)
        contentType(ContentType.Application.Json)
        setBody("""{"name":"$name"}""")
      }
    assertEquals(HttpStatusCode.OK, response.status)
    return response.field("id")()
  }

  private suspend fun ApplicationTestBuilder.workspaceIds(token: String): List<String> {
    val response = client.get("/api/workspaces/getAll") { header(SESSION_HEADER, token) }
    return Json
      .parseToJsonElement(response.bodyAsText())
      .jsonObject["items"]!!
      .jsonArray
      .map { it.jsonObject["id"]!!.jsonPrimitive.content }
  }

  @Test
  fun `users only see their own workspaces`() =
    TestHelp.testServer {
      val alice = login()
      val bob = login()

      val alicesWorkspace = createWorkspace(alice, "alice")
      val bobsWorkspace = createWorkspace(bob, "bob")

      assertEquals(listOf(alicesWorkspace), workspaceIds(alice))
      assertEquals(listOf(bobsWorkspace), workspaceIds(bob))

      val own = client.get("/api/workspaces/getById?id=$alicesWorkspace") { header(SESSION_HEADER, alice) }
      assertEquals(HttpStatusCode.OK, own.status)

      val foreign = client.get("/api/workspaces/getById?id=$alicesWorkspace") { header(SESSION_HEADER, bob) }
      assertEquals(HttpStatusCode.UnprocessableEntity, foreign.status)
    }

  @Test
  fun `workspace scoped routes are only accessible to members`() =
    TestHelp.testServer {
      val alice = login()
      val bob = login()
      val workspace = createWorkspace(alice, "alice")

      val createNodeType: suspend (String) -> HttpResponse = { token ->
        client.post("/api/workspaces/$workspace/graph/nodeTypes/create") {
          header(SESSION_HEADER, token)
          contentType(ContentType.Application.Json)
          setBody("""{"title":"Person"}""")
        }
      }
      assertEquals(HttpStatusCode.OK, createNodeType(alice).status)

      val forbiddenCreate = createNodeType(bob)
      assertEquals(HttpStatusCode.UnprocessableEntity, forbiddenCreate.status)
      assertTrue(forbiddenCreate.bodyAsText().contains("no_such_workspace"))

      for (path in listOf("graph/nodeTypes/getAll", "graph/nodes/getAll", "notes/getAll")) {
        val own = client.get("/api/workspaces/$workspace/$path") { header(SESSION_HEADER, alice) }
        assertEquals(HttpStatusCode.OK, own.status, path)

        val foreign = client.get("/api/workspaces/$workspace/$path") { header(SESSION_HEADER, bob) }
        assertEquals(HttpStatusCode.UnprocessableEntity, foreign.status, path)
        assertTrue(foreign.bodyAsText().contains("no_such_workspace"), path)
      }

      // Indistinguishable from a workspace that doesn't exist
      val missing = client.get("/api/workspaces/wskp_missing/notes/getAll") { header(SESSION_HEADER, alice) }
      assertEquals(HttpStatusCode.UnprocessableEntity, missing.status)

      // Still requires a session first
      val anonymous = client.get("/api/workspaces/$workspace/notes/getAll")
      assertEquals(HttpStatusCode.Unauthorized, anonymous.status)
    }
}
