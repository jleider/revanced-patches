package app.revanced.patches.reddit.customclients.baconreader.layout.embedcommentimages

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

private const val COMMENT_HOLDER_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/adapters/CommentHolder;"

internal val BytecodePatchContext.handleGifLinkInCommentMethod by gettingFirstMethodDeclaratively {
    definingClass(COMMENT_HOLDER_CLASS_DESCRIPTOR)
    name("handleGifLinkInComment")
    returnType("V")
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

/** In card mode, the post text is shown by this list adapter, one paragraph per row. */
internal val BytecodePatchContext.selftextAdapterGetViewMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/SelftextAdapter;")
    name("getView")
    returnType("Landroid/view/View;")
    instructions(setText())
}

/** Image, GIF and video posts: shows their text under the media, in an ExpandableTextView. */
internal val BytecodePatchContext.postMediaHandlerConstructorMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/PostMediaHandler;")
    name("<init>")
    instructions(setText(textViewClass = "Lcom/onelouder/baconreader/ExpandableTextView;"))
}

/**
 * TextView.setText(CharSequence[, BufferType]).
 *
 * @param bufferTypeParameter The type of the second parameter, or null for setText(CharSequence).
 * @param textViewClass The class of the TextView, or null for any.
 */
private fun setText(bufferTypeParameter: String? = null, textViewClass: String? = null) = allOf(
    Opcode.INVOKE_VIRTUAL(),
    method {
        name == "setText" &&
            (textViewClass == null || definingClass == textViewClass) &&
            parameterTypes.firstOrNull()?.toString() == "Ljava/lang/CharSequence;" &&
            parameterTypes.getOrNull(1)?.toString() == bufferTypeParameter
    },
)
