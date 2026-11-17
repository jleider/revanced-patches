package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.morecomments

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

internal val BytecodePatchContext.moreRequestConstructorMethodMatch by composingFirstMethod {
    name("<init>")
    instructions(string { contains("api/morechildren?") })
    custom { immutableClassDef.sourceFile == "OAuthCommentsMoreRequest.java" }
}

internal val BytecodePatchContext.parseMoreCommentsMethodMatch by composingFirstMethod {
    name("parseNetworkResponse")
    instructions(string { contains("things") })
    custom { immutableClassDef.sourceFile == "OAuthCommentsMoreRequest.java" }
}

internal val BytecodePatchContext.loadMoreCommentsMethodMatch by composingFirstMethod {
    instructions(string { contains("Requesting more: ") })
    custom { immutableClassDef.sourceFile == "CommentsPresenterImpl.java" }
}

// AutoValue_Post's ContentValues serializer: pairs each getter with its column name
// ("commentMoreIds" -> more-row children, "parent_id" -> parent fullname).
internal val BytecodePatchContext.serializePostMethodMatch by composingFirstMethod {
    instructions("commentMoreIds"())
    custom { immutableClassDef.sourceFile == "AutoValue_Post.java" }
}

// RedditRequest.getParams(): returns the request's form-parameter map field.
internal val BytecodePatchContext.getRequestParamsMethodMatch by composingFirstMethod {
    name("getParams")
    returnType("Ljava/util/Map;")
    parameterTypes()
    custom { immutableClassDef.sourceFile == "RedditRequest.java" }
}
