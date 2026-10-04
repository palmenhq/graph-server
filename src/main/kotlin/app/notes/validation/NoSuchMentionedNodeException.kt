package app.notes.validation

import app.utils.ResourceId
import app.utils.http.ClientException

class NoSuchMentionedNodeException(
  nodeId: ResourceId,
) : ClientException("no_such_mentioned_node", "Mentioned node $nodeId not found")
