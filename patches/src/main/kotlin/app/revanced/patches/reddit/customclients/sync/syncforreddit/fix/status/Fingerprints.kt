package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.status

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

// RedditStatusRequest.parseNetworkResponse (r8.f in v23.06.30): reads reddit.statuspage.io incidents.
internal val BytecodePatchContext.statusParseMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    returnType("Lcom/android/volley/Response;")
    parameterTypes("Lcom/android/volley/NetworkResponse;")
    instructions(string { equals("incidents") })
    custom { immutableClassDef.sourceFile == "RedditStatusRequest.java" }
}

// RedditIsDownAlertDialogBottomSheet's message (t9.q1.v4()).
internal val BytecodePatchContext.statusMessageMethodMatch by composingFirstMethod {
    returnType("Ljava/lang/String;")
    parameterTypes()
    instructions(string { startsWith("You might be able to still use Sync") })
    custom { immutableClassDef.sourceFile == "RedditIsDownAlertDialogBottomSheet.java" }
}
