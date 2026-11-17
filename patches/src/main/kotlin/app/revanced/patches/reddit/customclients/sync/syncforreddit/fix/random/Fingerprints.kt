package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.random

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

// RandomRequest.parseNetworkResponse: reads data.children[0].data.subreddit.
internal val BytecodePatchContext.randomParseMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    returnType("Lcom/android/volley/Response;")
    parameterTypes("Lcom/android/volley/NetworkResponse;")
    instructions("subreddit"())
    custom { immutableClassDef.sourceFile == "RandomRequest.java" }
}

// MainActivity.onCreate: an external reddit.com/r/<sub> link given as the "link" extra opens as a listing.
internal val BytecodePatchContext.mainActivityOnCreateMethodMatch by composingFirstMethod {
    name("onCreate")
    parameterTypes("Landroid/os/Bundle;")
    custom { immutableClassDef.sourceFile == "MainActivity.java" }
}

// LinkHandler's subreddit-link opener: shows the Random sheet for random/random_nsfw.
internal val BytecodePatchContext.openSubredditLinkMethodMatch by composingFirstMethod {
    returnType("V")
    parameterTypes("Landroid/content/Context;", "Ljava/lang/String;")
    instructions(
        "Opening subreddit link: "(),
        "random_nsfw"(),
    )
    custom { immutableClassDef.sourceFile == "LinkHandler.java" }
}
