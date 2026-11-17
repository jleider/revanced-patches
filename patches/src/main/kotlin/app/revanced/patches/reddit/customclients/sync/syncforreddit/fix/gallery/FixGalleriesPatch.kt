package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.gallery

import app.revanced.com.android.tools.smali.dexlib2.mutable.MutableMethod
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.util.getReference
import app.revanced.util.indexOfFirstInstructionOrThrow
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.Method
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.Instruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction
import com.android.tools.smali.dexlib2.iface.reference.MethodReference

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixGalleryPatch;"
private const val UNWRAP_POST_LISTING_METHOD_DESCRIPTOR = "$EXTENSION_CLASS_DESCRIPTOR->unwrapPostListing(Ljava/lang/String;)Ljava/lang/String;"

private fun Instruction.isStringFromBytes() = opcode == Opcode.INVOKE_DIRECT && getReference<MethodReference>()?.let {
    it.definingClass == "Ljava/lang/String;" && it.name == "<init>" &&
        it.parameterTypes.map(CharSequence::toString) == listOf("[B")
} == true

private fun Instruction.invokes(target: Method) = getReference<MethodReference>()?.let {
    it.definingClass == target.definingClass && it.name == target.name &&
        it.parameterTypes.map(CharSequence::toString) == target.parameterTypes.map(CharSequence::toString)
} == true

@Suppress("unused")
val fixGalleriesPatch = bytecodePatch(
    name = "Fix galleries",
    description = "Fixes loading image galleries.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        val galleryUrlMethod = galleryUrlMethodMatch.method

        // 1. Rewrite the request URL before every return.
        galleryUrlMethod.apply {
            val returns = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }.map { it.index }.reversed()
            if (returns.isEmpty()) throw PatchException("No return-object in gallery URL builder")
            returns.forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstructions(
                    index,
                    """
                        invoke-static { v$register }, $EXTENSION_CLASS_DESCRIPTOR->rewriteGalleryUrl(Ljava/lang/String;)Ljava/lang/String;
                        move-result-object v$register
                    """,
                )
            }
        }

        // Insert `body = unwrapPostListing(body)` right after `new String(bytes)` at [index].
        fun unwrapAfter(method: MutableMethod, index: Int) {
            val register = method.getInstruction<FiveRegisterInstruction>(index).registerC
            method.addInstructions(
                index + 1,
                """
                    invoke-static { v$register }, $UNWRAP_POST_LISTING_METHOD_DESCRIPTOR
                    move-result-object v$register
                """,
            )
        }

        // 2. Unwrap the [post, comments] array in the parser.
        galleryParseMethodMatch.method.apply {
            val index = indexOfFirstInstructionOrThrow { isStringFromBytes() }
            unwrapAfter(this, index)
        }

        // 3. MultiImageActivity.onDestroy rewrites the cached gallery response to store the last viewed
        //    position; it does `new JSONObject(new String(entry.data))`, which needs the unwrapped object.
        //    Anchor on the first String(byte[]) after the call to the gallery URL builder (the same method
        //    also handles imgur and eroshare caches).
        galleryActivityOnDestroyMethodMatch.method.apply {
            val instructions = implementation!!.instructions.toList()
            val callIndex = instructions.indexOfFirst { it.invokes(galleryUrlMethod) }
            if (callIndex < 0) throw PatchException("Gallery URL builder call not found in MultiImageActivity.onDestroy")
            val index = (callIndex + 1 until instructions.size).firstOrNull { instructions[it].isStringFromBytes() }
                ?: throw PatchException("String(byte[]) not found after gallery cache lookup")
            unwrapAfter(this, index)
        }
    }
}
