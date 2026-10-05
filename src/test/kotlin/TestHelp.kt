import app.SUPERADMIN_PORT
import app.auth.AuthService
import app.auth.PostgresSessionStorage
import app.auth.UserRepository
import app.config.Database
import app.module
import app.workspaces.WorkspaceRepository
import app.workspaces.WorkspaceService
import db.tables.pojos.AppUser
import db.tables.pojos.Workspace
import io.ktor.server.engine.connector
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.sql.Connection
import java.sql.DriverManager
import java.util.UUID

object TestHelp {
  val dbConnection: Connection
    get() =
      DriverManager.getConnection(
        System.getenv("TEST_DB_URL") ?: "jdbc:postgresql://localhost:54322/graphcrm_test",
        System.getenv("TEST_DB_USER") ?: "graphcrm_test",
        System.getenv("TEST_DB_PASSWORD") ?: "graphcrm_test",
      )

  val dslContext: DSLContext
    get() = DSL.using(dbConnection, Database.jooqSettings)

  val workspaceService = WorkspaceService(WorkspaceRepository(dslContext))

  val authService = AuthService(UserRepository(dslContext), PostgresSessionStorage(dslContext))

  fun createUser(password: String = "secret"): AppUser = authService.createUser("user-${UUID.randomUUID()}", password)

  // Owner of workspaces created by service level tests that don't care about access control
  val defaultUser: AppUser by lazy { createUser() }

  fun createWorkspace(
    name: String,
    ownerId: String = defaultUser.id!!,
  ): Workspace = workspaceService.createWorkspace(name, ownerId = ownerId)

  const val SUPERADMIN_SECRET = "test-superadmin-secret"

  // The test client sends relative URLs to localhost:80, so the public API is bound there in tests
  const val TEST_API_PORT = 80
  const val SUPERADMIN_URL = "http://localhost:$SUPERADMIN_PORT"

  /**
   * Runs the full application with both the public and the superadmin connector.
   */
  fun testServer(
    superadminTokenSecret: String? = SUPERADMIN_SECRET,
    block: suspend ApplicationTestBuilder.() -> Unit,
  ) = testApplication {
    engine {
      connector {
        host = "localhost"
        port = TEST_API_PORT
      }
      connector {
        host = "localhost"
        port = SUPERADMIN_PORT
      }
    }
    application { module(dslContext, superadminTokenSecret = superadminTokenSecret, apiPort = TEST_API_PORT) }
    block()
  }
}
