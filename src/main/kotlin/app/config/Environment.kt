package app.config

object Environment {
  val databaseUrl: String
    get() = System.getenv("DB_URL")
  val databaseUser: String
    get() = System.getenv("DB_USER")
  val databasePassword: String
    get() = System.getenv("DB_PASSWORD")
}
