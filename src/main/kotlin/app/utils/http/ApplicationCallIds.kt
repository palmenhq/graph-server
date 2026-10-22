package app.utils.http

import app.auth.userSession
import io.ktor.server.application.ApplicationCall

val ApplicationCall.workspaceId: String
  get() = parameters["workspaceId"]!!

val ApplicationCall.userId: String
  get() = userSession!!.userId
