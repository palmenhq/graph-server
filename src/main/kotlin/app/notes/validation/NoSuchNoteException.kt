package app.notes.validation

import app.utils.ResourceId
import app.utils.http.ClientException

class NoSuchNoteException(
  noteId: ResourceId,
) : ClientException("note_not_found", "Note $noteId not found")
