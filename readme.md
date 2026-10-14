# Backend of Graph

A small tool to build knowledge graphs with notes.

- Add nodes, connect them with edges, navigate the graph
- Add properties on nodes (instagram account, textarea, text, etc etc)
- Add notes, mentioning nodes - which will appear also in reverse

# Tech

- Powered by kotlin, ktor, jooq & postgres on the backend
- Powered by vue on the [frontend](https://github.com/palmenhq/graph-web) (see web repo for screenshots)
- Hosted using [scaleway](https://www.scaleway.com) serverless containers

# Development

This repo is supported by both `make` and `gradle` for convenience.

- Start postgres with `docker compose up -d`
- Run tests - `make test`, will run any pending test migrations and tests
- Run jooq codegen `make db-codegen db-migrate-test`, will run any pending migrations, generate jooq code and then migrate the test db
- An intellij .run file is provided for local web development
- An .http file is provided to create the first user on localhost with default config - [http/create-user.http](./http/create-user.http)
  - Credentials: `yohan` / `secret`
