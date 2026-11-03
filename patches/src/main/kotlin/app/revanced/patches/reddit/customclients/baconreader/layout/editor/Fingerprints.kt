package app.revanced.patches.reddit.customclients.baconreader.layout.editor

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.method
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

internal const val POST_ACTIVITY = "Lcom/onelouder/baconreader/PostActivity;"
internal const val COMPOSE_MESSAGE_ACTIVITY = "Lcom/onelouder/baconreader/ComposeMessageActivity;"
internal const val SUBMIT_ACTIVITY = "Lcom/onelouder/baconreader/SubmitActivity;"

/** The comment, reply and edit screen (PostCommentActivity, PostEditActivity): setContentView(layout). */
internal val BytecodePatchContext.postActivityOnCreateMethodMatch by composingFirstMethod {
    definingClass(POST_ACTIVITY)
    name("onCreate")
    returnType("V")
    instructions(method("setContentView"))
}

/** The message screen: setContentView(layout). */
internal val BytecodePatchContext.composeMessageOnCreateMethodMatch by composingFirstMethod {
    definingClass(COMPOSE_MESSAGE_ACTIVITY)
    name("onCreate")
    returnType("V")
    instructions(method("setContentView"))
}

/** The text post page of the submit screen: returns its view. */
internal val BytecodePatchContext.submitTextPageOnCreateViewMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/SubmitTextPage;")
    name("onCreateView")
    returnType("Landroid/view/View;")
    instructions(Opcode.RETURN_OBJECT())
}

/** The menus of the editors: menuInflater.inflate(menuId, menu). */
internal val BytecodePatchContext.postActivityMenuMethodMatch by composingFirstMethod {
    definingClass(POST_ACTIVITY)
    name("onCreateOptionsMenu")
    instructions(method("inflate"))
}

internal val BytecodePatchContext.composeMessageMenuMethodMatch by composingFirstMethod {
    definingClass(COMPOSE_MESSAGE_ACTIVITY)
    name("onCreateOptionsMenu")
    instructions(method("inflate"))
}

internal val BytecodePatchContext.submitMenuMethodMatch by composingFirstMethod {
    definingClass(SUBMIT_ACTIVITY)
    name("onCreateOptionsMenu")
    instructions(method("inflate"))
}
