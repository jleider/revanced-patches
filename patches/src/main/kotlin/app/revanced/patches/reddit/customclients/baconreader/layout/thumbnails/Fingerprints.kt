package app.revanced.patches.reddit.customclients.baconreader.layout.thumbnails

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

internal const val LINK_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/reddit/Link;"

internal val BytecodePatchContext.canShowThumbnailMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/utils/Utils;")
    name("canShowThumbnail")
    returnType("Z")
    parameterTypes(LINK_CLASS_DESCRIPTOR)
}

/** Loads the thumbnail of a list row with Glide's RequestBuilder.into(ImageView). */
internal val BytecodePatchContext.loadThumbnailMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/LinkProcessor;")
    name("loadThumbnail")
    returnType("V")
    parameterTypes("Landroid/content/Context;", LINK_CLASS_DESCRIPTOR, "Landroid/widget/ImageView;")
    instructions(
        allOf(
            Opcode.INVOKE_VIRTUAL(),
            method { name == "into" && parameterTypes.singleOrNull()?.toString() == "Landroid/widget/ImageView;" },
        ),
    )
}

internal val BytecodePatchContext.linkHolderUpdateViewMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/LinkHolder;")
    name("updateView")
    returnType("V")
    parameterTypes("Ljava/lang/Object;")
    instructions(readIsGallery())
}

internal val BytecodePatchContext.detailLinkFragmentResetHeaderMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/adapters/DetailLinkFragment;")
    name("resetHeader")
    returnType("V")
    parameterTypes()
    instructions(readIsGallery())
}

/** Reads Link.isGallery, which decides whether the app's gallery indicator is shown. */
private fun readIsGallery() = allOf(
    Opcode.IGET_OBJECT(),
    field { definingClass == LINK_CLASS_DESCRIPTOR && name == "isGallery" },
)
