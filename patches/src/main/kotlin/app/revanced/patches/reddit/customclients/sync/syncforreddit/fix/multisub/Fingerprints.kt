package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.multisub

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext

// RedditRequest(Context, int method, String url, Listener, ErrorListener): base of every OAuth request.
internal val BytecodePatchContext.redditRequestConstructorMethodMatch by composingFirstMethod {
    name("<init>")
    parameterTypes(
        "Landroid/content/Context;",
        "I",
        "Ljava/lang/String;",
        "Lcom/android/volley/Response\$Listener;",
        "Lcom/android/volley/Response\$ErrorListener;",
    )
    custom { immutableClassDef.sourceFile == "RedditRequest.java" }
}
