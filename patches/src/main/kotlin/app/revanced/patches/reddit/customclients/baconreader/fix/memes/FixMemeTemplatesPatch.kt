package app.revanced.patches.reddit.customclients.baconreader.fix.memes

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/memes/FixMemeTemplatesPatch;"

@Suppress("unused")
val fixMemeTemplatesPatch = bytecodePatch(
    name = "Fix meme templates",
    description = "Fixes the meme templates of the drawing screen not loading, with Imgflip's most used templates.",
) {
    dependsOn(sharedExtensionPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    apply {
        addResources("baconreader", "fix.memes.fixMemeTemplatesPatch")

        // The templates were on BaconReader's server, which answers 404 for all of them.
        // The grid now shows Imgflip's thumbnails.
        memeImagesStaticInitializerMethodMatch.method.apply {
            addInstructions(
                memeImagesStaticInitializerMethodMatch[0],
                """
                    invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->getTemplateUrls()[Ljava/lang/String;
                    move-result-object v0
                    sput-object v0, Lcom/handmark/drawchat/bitmapfun/provider/Images;->imageUrls:[Ljava/lang/String;
                """,
            )
        }

        // The app saves the grid's thumbnail as the background. Download the full template instead.
        memeGridItemClickMethod.addInstructionsWithLabels(
            0,
            """
                invoke-virtual { p0 }, Lcom/handmark/drawchat/bitmapfun/ui/ImageGridFragment;->getActivity()Landroidx/fragment/app/FragmentActivity;
                move-result-object v0
                invoke-virtual { p1, p3 }, Landroid/widget/AdapterView;->getItemAtPosition(I)Ljava/lang/Object;
                move-result-object v1
                invoke-static { v0, v1 }, $EXTENSION_CLASS_DESCRIPTOR->onTemplateClick(Landroid/app/Activity;Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :app_handles_click
                return-void
                :app_handles_click
                nop
            """,
        )
    }
}
