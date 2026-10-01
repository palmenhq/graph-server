package app.config

import org.jooq.conf.MappedSchema
import org.jooq.conf.RenderMapping
import org.jooq.conf.Settings

object Database {
  val jooqSettings: Settings
    get() =
      Settings()
        .withRenderMapping(
          RenderMapping()
            .withSchemata(
              MappedSchema()
                .withInput("public_schema")
                .withOutput("public"),
            ),
        ).also {
          System.setProperty("org.jooq.no-logo", "true")
          System.setProperty("org.jooq.no-tips", "true")
        }
}
