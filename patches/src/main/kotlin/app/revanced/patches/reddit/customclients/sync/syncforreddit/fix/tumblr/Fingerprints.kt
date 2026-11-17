package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.tumblr

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

// TumblrRequest's URL builder: api.tumblr.com/v2 with a revoked key.
internal val BytecodePatchContext.tumblrUrlMethodMatch by composingFirstMethod {
    returnType("Ljava/lang/String;")
    parameterTypes("Ljava/lang/String;")
    instructions("https://api.tumblr.com/v2/blog/"())
    custom { immutableClassDef.sourceFile == "TumblrRequest.java" }
}

// TumblrRequest.parseNetworkResponse: reads response.posts[0].photos[].
internal val BytecodePatchContext.tumblrParseMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    returnType("Lcom/android/volley/Response;")
    parameterTypes("Lcom/android/volley/NetworkResponse;")
    instructions("photos"())
    custom { immutableClassDef.sourceFile == "TumblrRequest.java" }
}
