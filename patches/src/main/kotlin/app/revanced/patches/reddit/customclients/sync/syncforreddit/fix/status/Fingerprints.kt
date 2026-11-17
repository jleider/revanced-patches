package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.status

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import app.revanced.patcher.string

// RedditStatusRequest.parseNetworkResponse: reads reddit.statuspage.io incidents.
internal val BytecodePatchContext.statusParseMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    returnType("Lcom/android/volley/Response;")
    parameterTypes("Lcom/android/volley/NetworkResponse;")
    instructions("incidents"())
    custom { immutableClassDef.sourceFile == "RedditStatusRequest.java" }
}

// Builds the Reddit outage alert message.
internal val BytecodePatchContext.statusMessageMethodMatch by composingFirstMethod {
    returnType("Ljava/lang/String;")
    parameterTypes()
    instructions(string { startsWith("You might be able to still use Sync") })
    custom { immutableClassDef.sourceFile == "RedditIsDownAlertDialogBottomSheet.java" }
}
