package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.tumblr

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.ReferenceInstruction
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
            val index = implementation!!.instructions.indexOfFirst {
                it.opcode == Opcode.INVOKE_DIRECT &&
                    ((it as ReferenceInstruction).reference as MethodReference).let { ref ->
                        ref.definingClass == "Ljava/lang/String;" && ref.name == "<init>" &&
                            ref.parameterTypes.map(CharSequence::toString) == listOf("[B")
                    }
            }
            if (index < 0) throw PatchException("String(byte[]) not found in TumblrRequest parser")
            val call = getInstruction<FiveRegisterInstruction>(index)
            val body = call.registerC
            val scratch = call.registerD
            addInstructions(
                index + 1,
                """
                    invoke-virtual { p0 }, Lcom/android/volley/Request;->getUrl()Ljava/lang/String;
                    move-result-object v$scratch
                    invoke-static { v$body, v$scratch }, $EXTENSION_CLASS_DESCRIPTOR->convert(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/String;
                    move-result-object v$body
                """,
            )
        }
    }
}
