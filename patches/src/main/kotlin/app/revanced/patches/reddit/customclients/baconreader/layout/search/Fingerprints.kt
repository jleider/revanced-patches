package app.revanced.patches.reddit.customclients.baconreader.layout.search

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

internal const val DETAIL_LINK_FRAGMENT_DESCRIPTOR = "Lcom/onelouder/baconreader/adapters/DetailLinkFragment;"

/** The post's header sets up its "COLLAPSE ALL" button: this.collapseButton = button. */
internal val BytecodePatchContext.setCollapseButtonMethodMatch by composingFirstMethod {
    definingClass(DETAIL_LINK_FRAGMENT_DESCRIPTOR)
    instructions(
        allOf(
            Opcode.IPUT_OBJECT(),
            field { definingClass == DETAIL_LINK_FRAGMENT_DESCRIPTOR && name == "collapseButton" },
        ),
    )
}

/** Sets the text of a comment of the post: this.commentBody.setText(text, bufferType). */
internal val BytecodePatchContext.commentHolderUpdateViewMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/CommentHolder;")
    name("updateView")
    returnType("V")
    instructions(
        allOf(Opcode.IGET_OBJECT(), field("commentBody")),
        afterAtMost(2, allOf(Opcode.INVOKE_VIRTUAL(), method("setText"))),
    )
}

/** Gives the comment list its items, from ThreadManager's data. */
internal val BytecodePatchContext.commentsAdapterProcessDataMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/CommentsAdapter;")
    name("processData")
    returnType("V")
    instructions(Opcode.RETURN_VOID())
}
