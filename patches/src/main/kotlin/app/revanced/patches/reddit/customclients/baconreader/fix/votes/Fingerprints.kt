package app.revanced.patches.reddit.customclients.baconreader.fix.votes

import app.revanced.patcher.allOf
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.method
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

internal val BytecodePatchContext.voteMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/reddit/RedditApi;")
    name("vote")
    returnType("V")
    parameterTypes("Landroid/content/Context;", "I", "Ljava/lang/String;")
}

/** Saves the post that comes with the comments to the post list, with LinksetManager.update. */
internal val BytecodePatchContext.commentsLoadedMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/data/ThreadManager\$CommentsListener;")
    name("processInBackground")
    returnType("V")
    parameterTypes("Ljava/util/List;")
    instructions(
        allOf(
            Opcode.INVOKE_STATIC(),
            method { definingClass == "Lcom/onelouder/baconreader/data/LinksetManager;" && name == "update" },
        ),
    )
}
