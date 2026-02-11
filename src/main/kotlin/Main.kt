import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import config.Database
import config.Environment
import io.ktor.openapi.OpenApiDoc
import io.ktor.openapi.OpenApiInfo
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.openapi.hide
import io.ktor.server.routing.openapi.plus
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import io.ktor.server.routing.routingRoot
import kotlinx.serialization.json.Json
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.postgresql.Driver

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

  install(ContentNegotiation) {
    json(
      Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
      },
    )
  }

  routing {
    route("/api") {
    }
    get(path = "/openapi.json") {
      val doc = OpenApiDoc(info = OpenApiInfo(title = "Graph Crm", "0")) + routingRoot.descendants()
      call.respondText(Json.encodeToString(doc))
    }.hide()
  }

}

fun main() {
  embeddedServer(Netty, port = 8080, host = "0.0.0.0", module = Application::main)
    .start(wait = true)
}
