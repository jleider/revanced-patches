package app.revanced.patches.reddit.customclients.baconreader.layout.comments

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

/** Requests the comments of a post: getComments(context, linkId, commentId, context, sort, limit, listener). */
internal val BytecodePatchContext.getCommentsMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/reddit/RedditApi;")
    name("getComments")
    returnType("V")
    parameterTypes(
        "Landroid/content/Context;",
        "Ljava/lang/String;",
        "Ljava/lang/String;",
        "I",
        "Ljava/lang/String;",
        "I",
        "Lcom/onelouder/baconreader/connectivity/Tasks\$OnCompleteListener;",
    )
}

/** Gives the comment list its items, from ThreadManager's data. */
internal val BytecodePatchContext.processDataMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/CommentsAdapter;")
    name("processData")
    returnType("V")
    instructions(Opcode.RETURN_VOID())
}
