package app.revanced.patches.reddit.customclients.baconreader.layout.editor

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.OneRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/editor/MarkdownEditorPatch;"

@Suppress("unused")
val markdownEditorPatch = bytecodePatch(
    name = "Markdown editor",
    description = "Makes the text box of comments, replies, edits, messages and text posts as big as possible, " +
        "adds a bar with Reddit's formats and a character counter above it, and shows the formatting while typing.",
) {
    dependsOn(sharedExtensionPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.editor.markdownEditorPatch")

        // The editors: right after their layout is set (the extension waits for onCreate to finish).
        listOf(postActivityOnCreateMethodMatch, composeMessageOnCreateMethodMatch).forEach { match ->
            match.method.addInstruction(
                match[0] + 1,
                "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->onEditorScreen(Landroid/app/Activity;)V",
            )
        }
        submitTextPageOnCreateViewMethodMatch.method.apply {
            val returnIndex = submitTextPageOnCreateViewMethodMatch[0]
            val viewRegister = getInstruction<OneRegisterInstruction>(returnIndex).registerA
            addInstruction(
                returnIndex,
                "invoke-static { v$viewRegister }, $EXTENSION_CLASS_DESCRIPTOR->onEditorView(Landroid/view/View;)V",
            )
        }

        // The app's format button ("A") is replaced by the bar: right after the menu is inflated (the menu's
        // register is reused for the return value at the end).
        listOf(postActivityMenuMethodMatch, composeMessageMenuMethodMatch, submitMenuMethodMatch).forEach { match ->
            match.method.apply {
                val inflateIndex = match[0]
                val menuRegister = getInstruction<FiveRegisterInstruction>(inflateIndex).registerE
                addInstruction(
                    inflateIndex + 1,
                    "invoke-static { v$menuRegister }, $EXTENSION_CLASS_DESCRIPTOR->hideFormatButton(Landroid/view/Menu;)V",
                )
            }
        }
    }
}
