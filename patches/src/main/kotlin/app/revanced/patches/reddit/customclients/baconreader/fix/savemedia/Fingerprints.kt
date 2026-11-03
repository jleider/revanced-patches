package app.revanced.patches.reddit.customclients.baconreader.fix.savemedia

import app.revanced.patcher.after
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

private const val SAVE_IMAGE_SERVICE_CLASS = "Lcom/onelouder/baconreader/imageutils/SaveImageService;"

internal val BytecodePatchContext.downloadToFileMethod by gettingFirstMethodDeclaratively {
    definingClass(SAVE_IMAGE_SERVICE_CLASS)
    name("getSingleFile")
    returnType("Z")
    parameterTypes("Ljava/lang/String;", "Landroidx/documentfile/provider/DocumentFile;")
}

/**
 * Downloads into the chosen folder: getSingleFile(url, folder, name), which calls getSingleFile(url, file) and
 * reads its result twice (a first try, then a retry with the full url).
 */
internal val BytecodePatchContext.downloadIntoFolderMethodMatch by composingFirstMethod {
    definingClass(SAVE_IMAGE_SERVICE_CLASS)
    name("getSingleFile")
    returnType("V")
    parameterTypes("Ljava/lang/String;", "Landroidx/documentfile/provider/DocumentFile;", "Ljava/lang/String;")
    instructions(
        downloadToFile(),
        after(Opcode.MOVE_RESULT()),
        downloadToFile(),
        after(Opcode.MOVE_RESULT()),
    )
}

private fun downloadToFile() = allOf(
    Opcode.INVOKE_DIRECT(),
    method { definingClass == SAVE_IMAGE_SERVICE_CLASS && name == "getSingleFile" && returnType == "Z" },
)
