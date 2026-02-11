import org.jooq.meta.jaxb.MatcherTransformType

plugins {
  alias(libs.plugins.kotlin.jvm)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.kotlin.kapt)
  alias(libs.plugins.ktor)
  alias(libs.plugins.ksp)

  alias(libs.plugins.ktlint)

  application
  alias(libs.plugins.liquibase)
  alias(libs.plugins.jooq.codegen)
}

kotlin {
  jvmToolchain(25)
  compilerOptions {
    optIn.add("kotlinx.serialization.ExperimentalSerializationApi")
    optIn.add("kotlin.time.ExperimentalTime")
    optIn.add("io.ktor.utils.io.ExperimentalKtorApi")
  }
}

repositories {
  mavenCentral()
}

buildscript {
  dependencies {
    classpath(libs.liquibase.core)
  }
}

dependencies {
  implementation(libs.kotlin.stdlib)

  implementation(libs.ktor.server.core)
  implementation(libs.ktor.server.netty)
  implementation(libs.ktor.server.content.negotiation)
  implementation(libs.ktor.serialization.kotlinx.json)
  implementation(libs.ktor.server.status.pages)
  implementation(libs.ktor.server.call.logging)
  implementation(libs.ktor.server.openapi)
  implementation(libs.ktor.server.routing.openapi)

  ksp(libs.kotlinx.schema.ksp)
  implementation(libs.kotlinx.schema.generator.json)
  implementation(libs.kotlinx.datetime)

  implementation(libs.postgres)
  implementation(libs.hikari)

  liquibaseRuntime(libs.liquibase.core)
  liquibaseRuntime(libs.picocli)
  liquibaseRuntime(libs.postgres)

  implementation(libs.jooq)
  jooqCodegen(libs.postgres)

  implementation(libs.kotlin.logging)
  implementation(libs.slf4j.simple)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.junit.jupiter)
  testRuntimeOnly("org.junit.platform:junit-platform-launcher")
  testImplementation(libs.mockk)
}

val mainClassName = "MainKt"

sourceSets {
  main {
    kotlin {
      srcDir("src/main/kotlin")
      srcDir("src/generated/kotlin") // jooq
    }
  }

  test {
    kotlin {
      srcDir("src/test/kotlin")
    }
  }
}

tasks.jar {
  archiveBaseName = "graphcrm"

  manifest {
    attributes += "Main-Class" to mainClassName
  }

  from(
    configurations.runtimeClasspath
      .get()
      .map { if (it.isDirectory) it else zipTree(it) },
  )

  duplicatesStrategy = DuplicatesStrategy.EXCLUDE

  exclude("META-INF/*.DSA", "META-INF/*.RSA", "META-INF/*.SF", "META-INF/LICENSE")
}

tasks.test {
  useJUnitPlatform()
}

ktor {
  openApi {
    enabled = true
    codeInferenceEnabled = true
  }
}

val dbUrl = System.getenv("DB_URL") ?: "jdbc:postgresql://127.0.0.1:54321/graphcrm"
val dbUsername = System.getenv("DB_USER") ?: "graphcrm"
val dbPassword = System.getenv("DB_PASSWORD") ?: "graphcrm"

jooq {
  configuration {
    jdbc {
      url = dbUrl
      user = dbUsername
      password = dbPassword
    }

    generator {
      name = "org.jooq.codegen.KotlinGenerator"
      database {
        name = "org.jooq.meta.postgres.PostgresDatabase"
        includes = "public\\..*"
        excludes = "public\\.databasechangelog|public\\.databasechangeloglock"
        inputSchema = "public"
        outputSchema = "public_schema"
      }

      generate {
        isDeprecated = false
        isRecords = false
        isDaos = true
      }

      target {
        packageName = "db"
        directory = "src/generated/kotlin"
      }
      strategy {
        matchers {
          tables {
            table {
              tableClass {
                transform = MatcherTransformType.PASCAL
                expression = "$0_table"
              }
            }
          }
        }
      }
    }
  }
}

liquibase {
  activities.register("main") {
    arguments =
      mapOf(
        "changelogFile" to "src/main/resources/db/changelog.yml",
        "url" to dbUrl,
        "username" to dbUsername,
        "password" to dbPassword,
      )
  }
}

ktlint {
  filter {
    exclude { it.file.path.contains("generated") }
  }
}
