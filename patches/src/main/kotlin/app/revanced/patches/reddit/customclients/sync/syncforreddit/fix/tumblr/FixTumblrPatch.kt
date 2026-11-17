package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.tumblr

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixTumblrPatch;"

@Suppress("unused")
val fixTumblrPatch = bytecodePatch(
    name = "Fix Tumblr",
    description = "Fixes opening Tumblr posts. Their images open in the image viewer; posts without images open in the browser.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        tumblrUrlMethodMatch.method.addInstructions(
            0,
            """
                invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->apiUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p0
                return-object p0
            """,
        )

        // Right after `body = new String(response.data)`; the byte-array register is free again.
        tumblrParseMethodMatch.method.apply {
            val index = indexOfFirstInstructionOrThrow {
                opcode == Opcode.INVOKE_DIRECT &&
                    getReference<MethodReference>()!!.let { reference ->
                        reference.definingClass == "Ljava/lang/String;" && reference.name == "<init>" &&
                            reference.parameterTypes.map(CharSequence::toString) == listOf("[B")
                    }
            }
            val call = getInstruction<FiveRegisterInstruction>(index)
            val bodyRegister = call.registerC
            val scratchRegister = call.registerD
            addInstructions(
                index + 1,
                """
                    invoke-virtual { p0 }, Lcom/android/volley/Request;->getUrl()Ljava/lang/String;
                    move-result-object v$scratchRegister
                    invoke-static { v$bodyRegister, v$scratchRegister }, $EXTENSION_CLASS_DESCRIPTOR->convert(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$bodyRegister
                """,
            )
        }
    }
}
