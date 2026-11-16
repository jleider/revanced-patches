package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.multisub

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

// RedditRequest(Context, int method, String url, Listener, ErrorListener): base of every OAuth request (x8.a in v23.06.30).
internal val BytecodePatchContext.redditRequestConstructorMatch by composingFirstMethod {
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
