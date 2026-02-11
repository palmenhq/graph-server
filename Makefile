MAIN_SOURCES := $(shell find src/main -type f)

.PHONY: clean run db-migrate db-migrate-test db-codegen db-rollback-one

clean:
	./gradlew clean

run: build
	java -jar build/libs/graphcrm.jar dev

build: build/libs/graphcrm.jar

test: db-migrate-test
	./gradlew check

db-migrate:
	./gradlew update --info

db-migrate-test:
	DB_URL='jdbc:postgresql://127.0.0.1:54322/graphcrm_test' \
	DB_USER='graphcrm_test' \
	DB_PASSWORD='graphcrm_test' \
	./gradlew update

db-rollback-one-test:
	DB_URL='jdbc:postgresql://127.0.0.1:54322/graphcrm_test' \
	DB_USER='graphcrm_test' \
	DB_PASSWORD='graphcrm_test' \
	./gradlew rollbackCount -Dliquibase.command.count=1

db-codegen: db-migrate
	./gradlew jooqCodegen --info

db-rollback-one:
	./gradlew rollbackCount -Dliquibase.command.count=1

db-dangerously-clear-checksums:
	./gradlew clearChecksums

build/libs/graphcrm.jar: build.gradle.kts gradle/libs.versions.toml $(MAIN_SOURCES)
	./gradlew build
