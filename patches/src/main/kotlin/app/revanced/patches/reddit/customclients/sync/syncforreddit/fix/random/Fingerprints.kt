package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.random

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

// RandomRequest.parseNetworkResponse (r8.d in v23.06.30): reads data.children[0].data.subreddit.
internal val BytecodePatchContext.randomParseMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    returnType("Lcom/android/volley/Response;")
    parameterTypes("Lcom/android/volley/NetworkResponse;")
    instructions(string { equals("subreddit") })
    custom { immutableClassDef.sourceFile == "RandomRequest.java" }
}
