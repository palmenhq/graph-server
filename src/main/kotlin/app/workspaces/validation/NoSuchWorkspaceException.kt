package app.workspaces.validation

import app.utils.http.ClientException

class NoSuchWorkspaceException : ClientException("no_such_workspace", "Workspace not found")
