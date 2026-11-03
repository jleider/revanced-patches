package app.revanced.patches.reddit.customclients.baconreader.fix.imgur

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

internal val BytecodePatchContext.getImgurBaseUrlMethod by gettingFirstMethodDeclaratively {
    definingClass("Lcom/onelouder/baconreader/imageutils/ImgurV3Constants;")
    name("getBaseURL")
    returnType("Ljava/lang/String;")
    parameterTypes()
}

/** The upload adds the authorization to the connection: ImgurAuthorization.addToHttpURLConnection(connection). */
internal val BytecodePatchContext.imgurUploadMethodMatch by composingFirstMethod {
    definingClass("Lcom/onelouder/baconreader/imageutils/ImgurUpload;")
    name("run")
    returnType("Ljava/lang/String;")
    parameterTypes("[Ljava/lang/Void;")
    instructions(
        allOf(
            Opcode.INVOKE_VIRTUAL(),
            method {
                definingClass == "Lcom/onelouder/baconreader/imageutils/ImgurAuthorization;" &&
                    name == "addToHttpURLConnection"
            },
        ),
    )
}
