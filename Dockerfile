# Runner: expects the jar to be built beforehand with `./gradlew assemble`
FROM eclipse-temurin:25-jre AS runner

RUN groupadd --system graphcrm && useradd --system --gid graphcrm graphcrm

WORKDIR /app
COPY --chown=graphcrm:graphcrm build/libs/graphcrm.jar /app/graphcrm.jar

USER graphcrm

# API and superadmin ports
EXPOSE 8080 8081

ENTRYPOINT ["java", "-jar", "/app/graphcrm.jar"]


# Migrator: runs liquibase against the changelog in src/main/resources/db
FROM liquibase/liquibase:5.0.1 AS migrator

ARG POSTGRES_DRIVER_VERSION=42.7.9

# Liquibase 5 no longer bundles JDBC drivers
ADD --chown=liquibase:liquibase \
  https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_DRIVER_VERSION}/postgresql-${POSTGRES_DRIVER_VERSION}.jar \
  /liquibase/lib/

# Keep the same relative path as the gradle liquibase activity, since liquibase
# records the changelog path in the databasechangelog table
COPY --chown=liquibase:liquibase src/main/resources/db /liquibase/changelog/src/main/resources/db

ENV LIQUIBASE_SEARCH_PATH=/liquibase/changelog
ENV LIQUIBASE_COMMAND_CHANGELOG_FILE=src/main/resources/db/changelog.yml

# Uses the same DB_URL, DB_USER and DB_PASSWORD variables as the application
ENTRYPOINT ["/bin/sh", "-c", "exec liquibase --url=\"$DB_URL\" --username=\"$DB_USER\" --password=\"$DB_PASSWORD\" \"$@\"", "--"]
CMD ["update"]
