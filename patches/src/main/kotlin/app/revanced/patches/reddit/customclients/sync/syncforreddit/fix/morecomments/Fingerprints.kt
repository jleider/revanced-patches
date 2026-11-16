package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.morecomments

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

internal val BytecodePatchContext.moreConstructorMatch by composingFirstMethod {
    name("<init>")
    instructions(string { contains("api/morechildren?") })
    custom { immutableClassDef.sourceFile == "OAuthCommentsMoreRequest.java" }
}

internal val BytecodePatchContext.moreParseMatch by composingFirstMethod {
    name("parseNetworkResponse")
    instructions(string { contains("things") })
    custom { immutableClassDef.sourceFile == "OAuthCommentsMoreRequest.java" }
}

internal val BytecodePatchContext.moreCallerMatch by composingFirstMethod {
    instructions(string { contains("Requesting more: ") })
    custom { immutableClassDef.sourceFile == "CommentsPresenterImpl.java" }
}
