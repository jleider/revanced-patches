package app.revanced.patches.reddit.customclients.baconreader.layout.avatars

import app.revanced.patcher.afterAtMost
import app.revanced.patcher.allOf
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.field
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.method
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

/** The superclass of Comment, also used for the inbox's private messages. */
internal const val MESSAGE_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/reddit/Message;"

/** Builds the author line of a comment: "author [flair] points time". */
internal val BytecodePatchContext.getCommentSubTextMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/parser/SubTextBuilder;")
    name("getCommentSubText")
    returnType("Landroid/text/Spannable;")
    instructions(Opcode.RETURN_OBJECT())
}

/** Sets the author line of a comment: this.commentSubText.setText(subText). */
internal val BytecodePatchContext.commentHolderUpdateViewMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/CommentHolder;")
    name("updateView")
    returnType("V")
    instructions(*setTextOfField("commentSubText"))
}

/**
 * Shows "Reply to [author] in r/... as [you]" and the replied comment or post on the reply screen:
 * this.subTextView.setText(text).
 */
internal val BytecodePatchContext.updateReplyTextMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/PostActivity;")
    name("updateReplyText")
    returnType("V")
    instructions(*setTextOfField("subTextView"))
}

/** Shows an inbox item (comment and post replies, mentions and private messages): this.labelView.setText(label). */
internal val BytecodePatchContext.inboxHolderUpdateViewMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/InboxHolder;")
    name("updateView")
    returnType("V")
    instructions(*setTextOfField("labelView"))
}

/** The TextView read from the field [fieldName], then its setText(CharSequence). */
private fun setTextOfField(fieldName: String) = arrayOf(
    allOf(Opcode.IGET_OBJECT(), field(fieldName)),
    afterAtMost(
        2,
        allOf(
            Opcode.INVOKE_VIRTUAL(),
            method { name == "setText" && parameterTypes.map { it.toString() } == listOf("Ljava/lang/CharSequence;") },
        ),
    ),
)
