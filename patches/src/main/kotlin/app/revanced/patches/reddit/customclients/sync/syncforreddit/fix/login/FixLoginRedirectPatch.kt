package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.login

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.PatchException
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixLoginRedirectPatch;"

@Suppress("unused")
val fixLoginRedirectPatch = bytecodePatch(
    name = "Fix login redirect",
    description = "Fixes login landing on the Reddit homepage instead of the app authorization page.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        buildAuthorizeUrlMethodMatch.method.apply {
            val returns = implementation!!.instructions.withIndex()
                .filter { it.value.opcode == Opcode.RETURN_OBJECT }.map { it.index }.reversed()
            if (returns.isEmpty()) throw PatchException("No return-object in authorize URL builder")
            returns.forEach { index ->
                val register = getInstruction<OneRegisterInstruction>(index).registerA
                addInstruction(
                    index,
                    "invoke-static { v$register }, $EXTENSION_CLASS_DESCRIPTOR->rememberAuthorizeUrl(Ljava/lang/String;)V",
                )
            }
        }

        loginOnProgressChangedMethodMatch.method.addInstruction(
            0,
            "invoke-static { p1, p2 }, $EXTENSION_CLASS_DESCRIPTOR->onProgressChanged(Landroid/webkit/WebView;I)V",
        )
    }
}
