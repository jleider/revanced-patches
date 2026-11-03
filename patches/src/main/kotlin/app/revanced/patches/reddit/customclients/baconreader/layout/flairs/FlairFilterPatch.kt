package app.revanced.patches.reddit.customclients.baconreader.layout.flairs

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/flairs/FlairFilterPatch;"

@Suppress("unused")
val flairFilterPatch = bytecodePatch(
    name = "Flair filter",
    description = "Adds a \"Flair\" button to the sort bar of subreddits, to show only the posts with the chosen flairs.",
) {
    dependsOn(sharedExtensionPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.flairs.flairFilterPatch")

        // Show the control, with the flairs of the feed's query, whenever a feed is opened.
        openLinksetMethod.addInstruction(
            0,
            "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->onOpenLinkset(Landroid/app/Activity;)V",
        )

        // Remember the flairs of the loaded posts: the list for subreddits whose flairs are not public.
        addToLinksetMethod.addInstruction(
            0,
            "invoke-static { p0, p1 }, $EXTENSION_CLASS_DESCRIPTOR->onListingLoaded(Ljava/lang/Object;Ljava/lang/Object;)V",
        )
    }
}
