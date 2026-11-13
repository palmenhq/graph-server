package app.config

object Environment {
  val databaseUrl: String
    get() = System.getenv("DB_URL")
  val databaseUser: String
    get() = System.getenv("DB_USER")
  val databasePassword: String
    get() = System.getenv("DB_PASSWORD")
  val superadminTokenSecret: String?
    get() = System.getenv("SUPERADMIN_TOKEN_SECRET")?.takeIf { it.isNotBlank() }
  val webAppHost: String
    get() = System.getenv("WEB_APP_HOST")?.takeIf { it.isNotBlank() }!!
}
