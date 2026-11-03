package app.revanced.patches.reddit.customclients.baconreader.layout.textposticon

import app.revanced.patcher.allOf
import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.definingClass
import app.revanced.patcher.field
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType
import com.android.tools.smali.dexlib2.Opcode

/** Post list rows: LinkHolder (list view) and LinkCardHolder (card view). */
internal const val LINK_HOLDER_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/adapters/LinkHolder;"
internal const val LINK_CARD_HOLDER_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/adapters/LinkCardHolder;"

/** The constructor, where "this.next = (ImageView) findViewById(R.id.next)" sets the expand button. */
internal val BytecodePatchContext.linkHolderConstructorMethodMatch by composingFirstMethod {
    definingClass(LINK_HOLDER_CLASS_DESCRIPTOR)
    name("<init>")
    instructions(setNextButton(LINK_HOLDER_CLASS_DESCRIPTOR))
}

internal val BytecodePatchContext.linkCardHolderConstructorMethodMatch by composingFirstMethod {
    definingClass(LINK_CARD_HOLDER_CLASS_DESCRIPTOR)
    name("<init>")
    instructions(setNextButton(LINK_CARD_HOLDER_CLASS_DESCRIPTOR))
}

private fun setNextButton(holder: String) = allOf(
    Opcode.IPUT_OBJECT(),
    field { definingClass == holder && name == "next" && type == "Landroid/widget/ImageView;" },
)

internal val BytecodePatchContext.linkHolderRotateSelftextArrowMethod by gettingFirstMethodDeclaratively {
    definingClass(LINK_HOLDER_CLASS_DESCRIPTOR)
    name("rotateSelftextArrow")
    returnType("V")
    parameterTypes("Z", "I")
}

internal val BytecodePatchContext.linkCardHolderRotateSelftextArrowMethod by gettingFirstMethodDeclaratively {
    definingClass(LINK_CARD_HOLDER_CLASS_DESCRIPTOR)
    name("rotateSelftextArrow")
    returnType("V")
    parameterTypes("Z", "I")
}
