package app.revanced.patches.reddit.customclients.baconreader.layout.tables

import app.revanced.patcher.afterAtMost
import app.revanced.patcher.allOf
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.field
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.method
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

internal val BytecodePatchContext.appendTableMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/parser/RedditSpannerBase;")
    name("appendTable")
    returnType("I")
    parameterTypes(
        "Landroid/text/SpannableStringBuilder;",
        "Lcom/onelouder/baconreader/parser/RedditParser\$Element;",
    )
}

internal const val COMMENT_HOLDER_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/adapters/CommentHolder;"

/** Sets the touch listener of the comment body: this.commentBody, then setOnTouchListener. */
internal val BytecodePatchContext.commentHolderConstructorMethodMatch by composingFirstMethod {
    definingClass(COMMENT_HOLDER_CLASS_DESCRIPTOR)
    name("<init>")
    instructions(
        allOf(Opcode.IGET_OBJECT(), field("commentBody")),
        afterAtMost(4, allOf(Opcode.INVOKE_VIRTUAL(), method("setOnTouchListener"))),
    )
}

internal val BytecodePatchContext.commentHolderUpdateViewMethodMatch by composingFirstMethod {
    definingClass(COMMENT_HOLDER_CLASS_DESCRIPTOR)
    name("updateView")
    returnType("V")
    instructions(setText("Landroid/widget/TextView\$BufferType;"))
}

internal val BytecodePatchContext.detailLinkFragmentUpdateSelftextMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/DetailLinkFragment;")
    name("updateSelftext")
    returnType("V")
    instructions(setText())
}

/**
 * TextView.setText(CharSequence[, BufferType]).
 *
 * @param bufferTypeParameter The type of the second parameter, or null for setText(CharSequence).
 */
private fun setText(bufferTypeParameter: String? = null) = allOf(
    Opcode.INVOKE_VIRTUAL(),
    method {
        name == "setText" &&
            parameterTypes.firstOrNull()?.toString() == "Ljava/lang/CharSequence;" &&
            parameterTypes.getOrNull(1)?.toString() == bufferTypeParameter
    },
)
