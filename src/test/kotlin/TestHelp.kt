import app.config.Database
import app.workspaces.WorkspaceRepository
import app.workspaces.WorkspaceService
import org.jooq.DSLContext
import org.jooq.impl.DSL
import java.sql.Connection
import java.sql.DriverManager

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
}
