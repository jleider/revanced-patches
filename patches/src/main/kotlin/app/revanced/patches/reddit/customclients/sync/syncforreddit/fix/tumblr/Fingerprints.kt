package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.tumblr

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

// TumblrRequest's URL builder (r8.j.a(String) in v23.06.30): api.tumblr.com/v2 with a revoked key.
internal val BytecodePatchContext.tumblrUrlMethodMatch by composingFirstMethod {
    returnType("Ljava/lang/String;")
    parameterTypes("Ljava/lang/String;")
    instructions(string { equals("https://api.tumblr.com/v2/blog/") })
    custom { immutableClassDef.sourceFile == "TumblrRequest.java" }
}

// TumblrRequest.parseNetworkResponse: reads response.posts[0].photos[].
internal val BytecodePatchContext.tumblrParseMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    returnType("Lcom/android/volley/Response;")
    parameterTypes("Lcom/android/volley/NetworkResponse;")
    instructions(string { equals("photos") })
    custom { immutableClassDef.sourceFile == "TumblrRequest.java" }
}
