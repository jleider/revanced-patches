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

// MainActivity.onCreate: an external reddit.com/r/<sub> link given as the "link" extra opens as a listing.
internal val BytecodePatchContext.mainActivityOnCreateMethodMatch by composingFirstMethod {
    name("onCreate")
    parameterTypes("Landroid/os/Bundle;")
    custom { immutableClassDef.sourceFile == "MainActivity.java" }
}

// LinkHandler's subreddit-link opener (y7.a.T in v23.06.30): shows the Random sheet for random/random_nsfw.
internal val BytecodePatchContext.openSubredditLinkMethodMatch by composingFirstMethod {
    returnType("V")
    parameterTypes("Landroid/content/Context;", "Ljava/lang/String;")
    instructions(
        string { equals("Opening subreddit link: ") },
        string { equals("random_nsfw") },
    )
    custom { immutableClassDef.sourceFile == "LinkHandler.java" }
}
