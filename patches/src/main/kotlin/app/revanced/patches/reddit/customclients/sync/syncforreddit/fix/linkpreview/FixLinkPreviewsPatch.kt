package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.linkpreview

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixLinkPreviewsPatch;"

@Suppress("unused")
val fixLinkPreviewsPatch = bytecodePatch(
    name = "Fix link previews",
    description = "Builds website and Imgur album preview images on the device, because Sync's preview server is gone.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        imageClientMethodMatch.method.apply {
            // Right after `builder = builder.addInterceptor(userAgentInterceptor)`; no label there.
            val addIndex = implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_VIRTUAL &&
                    ((it as ReferenceInstruction).reference as MethodReference).name == "addInterceptor"
            }
            if (addIndex < 0) throw PatchException("addInterceptor not found in OkHttpHelper")
            val moveResult = addIndex + 1
            if (getInstruction(moveResult).opcode != Opcode.MOVE_RESULT_OBJECT) {
                throw PatchException("Unexpected instruction after addInterceptor")
            }
            val builder = getInstruction<OneRegisterInstruction>(moveResult).registerA
            addInstructions(
                moveResult + 1,
                """
                    invoke-static { v$builder }, $EXTENSION_CLASS_DESCRIPTOR->addInterceptor(Lokhttp3/OkHttpClient${'$'}Builder;)Lokhttp3/OkHttpClient${'$'}Builder;
                    move-result-object v$builder
                """,
            )
        }
    }
}
