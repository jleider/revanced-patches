package app.revanced.patches.reddit.customclients.baconreader.layout.tables

import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/baconreader/tables/ShowTablesPatch;"

@Suppress("unused")
val showTablesPatch = bytecodePatch(
    name = "Show tables",
    description = "Shows tables in comments and posts as a grid instead of \"header: value\" lines. " +
        "Wide tables can be expanded and scrolled sideways, and links in tables can be tapped.",
) {
    dependsOn(sharedExtensionPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.tables.showTablesPatch")

        // The app flattens tables into lines in RedditSpannerBase.appendTable.
        // Draw them as a grid instead, or let the app flatten them if they can not be parsed.
        appendTableMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { p0, p1, p2 }, $EXTENSION_CLASS_DESCRIPTOR->appendTable(Ljava/lang/Object;Landroid/text/SpannableStringBuilder;Ljava/lang/Object;)I
                move-result v0
                if-ltz v0, :original
                return v0
            """,
            ExternalLabel("original", appendTableMethod.getInstruction(0)),
        )

        // Fit the tables to the real width of the text, once the comment or post text is set.
        listOf(commentHolderUpdateViewMethodMatch, detailLinkFragmentUpdateSelftextMethodMatch).forEach { match ->
            val index = match[0]
            match.method.apply {
                val textViewRegister = getInstruction<FiveRegisterInstruction>(index).registerC
                addInstruction(
                    index + 1,
                    "invoke-static { v$textViewRegister }, $EXTENSION_CLASS_DESCRIPTOR->onTextSet(Landroid/widget/TextView;)V",
                )
            }
        }

        // The comment body has a touch listener: wrap it, so table touches (expand button,
        // sideways scrolling, links) are handled and the app's listener keeps working.
        commentHolderConstructorMethodMatch.method.apply {
            val setTouchListenerIndex = commentHolderConstructorMethodMatch[1]
            val setTouchListener = getInstruction<FiveRegisterInstruction>(setTouchListenerIndex)
            addInstruction(
                setTouchListenerIndex + 1,
                "invoke-static { v${setTouchListener.registerC}, v${setTouchListener.registerD} }, " +
                    "$EXTENSION_CLASS_DESCRIPTOR->installTouch(Landroid/view/View;Landroid/view/View\$OnTouchListener;)V",
            )
        }
    }
}
