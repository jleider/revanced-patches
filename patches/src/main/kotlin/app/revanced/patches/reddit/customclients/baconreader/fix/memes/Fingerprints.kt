package app.revanced.patches.reddit.customclients.baconreader.fix.memes

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

internal val BytecodePatchContext.memeImagesStaticInitializerMethodMatch by composingFirstMethod {
    definingClass("Lcom/handmark/drawchat/bitmapfun/provider/Images;")
    name("<clinit>")
    instructions(Opcode.RETURN_VOID())
}

internal val BytecodePatchContext.memeGridItemClickMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/handmark/drawchat/bitmapfun/ui/ImageGridFragment;")
    name("onItemClick")
    returnType("V")
    parameterTypes("Landroid/widget/AdapterView;", "Landroid/view/View;", "I", "J")
}
