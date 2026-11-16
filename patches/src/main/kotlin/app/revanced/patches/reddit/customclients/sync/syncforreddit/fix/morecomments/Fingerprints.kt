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

// AutoValue_Post's ContentValues serializer: pairs each getter with its column name
// ("commentMoreIds" -> more-row children, "parent_id" -> parent fullname).
internal val BytecodePatchContext.postSerializerMatch by composingFirstMethod {
    instructions(string { this == "commentMoreIds" })
    custom { immutableClassDef.sourceFile == "AutoValue_Post.java" }
}

// RedditRequest.getParams(): returns the request's form-parameter map field.
internal val BytecodePatchContext.requestParamsMatch by composingFirstMethod {
    name("getParams")
    returnType("Ljava/util/Map;")
    parameterTypes()
    custom { immutableClassDef.sourceFile == "RedditRequest.java" }
}
