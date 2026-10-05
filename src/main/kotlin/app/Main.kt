package app

import app.auth.AuthService
import app.auth.PostgresSessionStorage
import app.auth.RequireSession
import app.auth.SESSION_HEADER
import app.auth.UserRepository
import app.auth.UserSession
import app.auth.authRoutes
import app.auth.userSessionSerializer
import app.config.Database
import app.config.Environment
import app.graph.GraphRepository
import app.graph.GraphService
import app.graph.PropertyRepository
import app.graph.PropertyService
import app.graph.graphRoutes
import app.notes.NotesRepository
import app.notes.NotesService
import app.notes.noteRoutes
import app.superadmin.RequireSuperadminToken
import app.superadmin.superadminRoutes
import app.workspaces.RequireWorkspaceAccess
import app.workspaces.WorkspaceRepository
import app.workspaces.WorkspaceService
import app.workspaces.workspaceRoutes
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.ktor.http.ContentType
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.connector
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.header
import kotlinx.serialization.json.Json
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.Driver

const val API_PORT = 8080

fun Application.main() {
  val dataSource =
    HikariDataSource(
      HikariConfig().apply {
        this.jdbcUrl = Environment.databaseUrl
        this.username = Environment.databaseUser
        this.password = Environment.databasePassword
        this.driverClassName = Driver::class.qualifiedName
      },
    )
  val dslContext = DSL.using(dataSource, SQLDialect.POSTGRES, Database.jooqSettings)

  module(dslContext, superadminTokenSecret = Environment.superadminTokenSecret)
}

/**
 * When [superadminTokenSecret] is null, all superadmin requests are rejected.
 */
fun Application.module(
  dslContext: DSLContext,
  superadminTokenSecret: String?,
) {
  val sessionStorage = PostgresSessionStorage(dslContext)
  val authService = AuthService(UserRepository(dslContext), sessionStorage)

  val workspaceService = WorkspaceService(WorkspaceRepository(dslContext))

  val graphService = GraphService(GraphRepository(dslContext), workspaceService)

  val propertyService = PropertyService(PropertyRepository(dslContext), graphService)

  val notesService = NotesService(NotesRepository(dslContext), workspaceService, graphService)

  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
      },
    )
  }

  install(Sessions) {
    header<UserSession>(SESSION_HEADER, sessionStorage) {
      serializer = userSessionSerializer
    }
  }

  routing {
    // Superadmin routes are protected by their token only, so RequireSession must not be installed on /api itself
    val superadminRoot =
      route("/api/superadmin") {
        install(RequireSuperadminToken) { secret = superadminTokenSecret }
        superadminRoutes(authService)
      }

    authRoutes(authService)
    route("/api") {
      // Installed on the existing /api/workspaces node, which workspaceRoutes and the {workspaceId} routes extend
      route("workspaces") { install(RequireSession) }
      workspaceRoutes(workspaceService)
      route("workspaces/{workspaceId}") {
        install(RequireWorkspaceAccess) { this.workspaceService = workspaceService }
        graphRoutes(graphService, propertyService)
        noteRoutes(notesService)
      }
    }
    get(path = "/openapi.json") {
      // Superadmin routes are internal and must not show up in the public API documentation
      val publicRoutes =
        routingRoot.descendants().filterNot { route ->
          generateSequence<Route>(route) { it.parent }.any { it == superadminRoot }
        }
      val doc = OpenApiDoc(info = OpenApiInfo(title = "Graph Crm", "0")) + publicRoutes
      call.respondText(Json.encodeToString(doc), contentType = ContentType.Application.Json)
    }.hide()
  }
}

fun main() {
  embeddedServer(
    Netty,
    configure = {
      connector {
        host = "0.0.0.0"
        port = API_PORT
      }
    },
    module = Application::main,
  ).start(wait = true)
}
