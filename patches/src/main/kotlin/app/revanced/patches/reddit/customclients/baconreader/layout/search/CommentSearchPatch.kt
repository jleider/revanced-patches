package app.revanced.patches.reddit.customclients.baconreader.layout.search

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction
import com.android.tools.smali.dexlib2.iface.instruction.TwoRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/search/CommentSearchPatch;"

@Suppress("unused")
val commentSearchPatch = bytecodePatch(
    name = "Search comments",
    description = "Adds a search button to the sort bar of posts, to find, highlight and jump between the " +
        "comments with a text or user name, or show only those comments.",
) {
    dependsOn(sharedExtensionPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.search.commentSearchPatch")

        // The search button goes next to "COLLAPSE ALL", when the post's header finds it.
        setCollapseButtonMethodMatch.method.apply {
            val index = setCollapseButtonMethodMatch[0]
            val buttonRegister = getInstruction<TwoRegisterInstruction>(index).registerA
            addInstruction(
                index + 1,
                "invoke-static { p0, v$buttonRegister }, $EXTENSION_CLASS_DESCRIPTOR->" +
                    "addSearchButton(Ljava/lang/Object;Landroid/widget/TextView;)V",
            )
        }

        // "Show only matching comments" replaces the list's items when the list gets them.
        commentsAdapterProcessDataMethodMatch.method.addInstruction(
            commentsAdapterProcessDataMethodMatch[0],
            "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->onItemsProcessed(Ljava/lang/Object;)V",
        )

        // Highlights the matches, after the comment's text is set.
        commentHolderUpdateViewMethodMatch.method.apply {
            val setTextIndex = commentHolderUpdateViewMethodMatch[1]
            val bodyRegister = getInstruction<FiveRegisterInstruction>(setTextIndex).registerC
            addInstruction(
                setTextIndex + 1,
                "invoke-static { p0, v$bodyRegister }, $EXTENSION_CLASS_DESCRIPTOR->" +
                    "highlight(Ljava/lang/Object;Landroid/widget/TextView;)V",
            )
        }
    }
}

