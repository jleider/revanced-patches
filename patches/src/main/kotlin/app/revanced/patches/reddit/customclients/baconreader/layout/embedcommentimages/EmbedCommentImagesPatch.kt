package app.revanced.patches.reddit.customclients.baconreader.layout.embedcommentimages

import app.revanced.patcher.CompositeMatch
import app.revanced.patcher.extensions.ExternalLabel
import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructionsWithLabels
import app.revanced.patcher.extensions.getInstruction
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.fix.imgur.fixImgurApiPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.PreferenceScreen
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.settingsPatch
import app.revanced.patches.shared.misc.settings.preference.NonInteractivePreference
import app.revanced.patches.shared.misc.settings.preference.SwitchPreference
import com.android.tools.smali.dexlib2.iface.instruction.FiveRegisterInstruction

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/embedcommentimages/EmbedCommentImagesPatch;"

@Suppress("unused")
val embedCommentImagesPatch = bytecodePatch(
    name = "Embed comment images",
    description = "Adds options to show GIFs, image links, Imgur albums and emotes in comments and posts as images, " +
        "play videos uploaded in comments and video links, show bare YouTube links with the video title and thumbnail, " +
        "and show bare X and Bluesky links as the post.",
) {
    dependsOn(sharedExtensionPatch, settingsPatch, addResourcesPatch, fixImgurApiPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.embedcommentimages.embedCommentImagesPatch")

        PreferenceScreen.IMAGES.addPreferences(
            SwitchPreference("revanced_baconreader_show_images"),
            SwitchPreference("revanced_baconreader_images_full_width"),
            SwitchPreference("revanced_baconreader_images_long_press_download"),
            SwitchPreference("revanced_baconreader_images_wifi_only"),
            SwitchPreference("revanced_baconreader_show_videos"),
            NonInteractivePreference("revanced_baconreader_images_gestures"),
        )
        PreferenceScreen.LINKS.addPreferences(
            SwitchPreference("revanced_baconreader_youtube_titles"),
            SwitchPreference("revanced_baconreader_youtube_thumbnails"),
            SwitchPreference("revanced_baconreader_x_posts"),
            SwitchPreference("revanced_baconreader_bluesky_posts"),
        )

        // While images are shown, stop the app from replacing the <img> tag with a "GIF" link.
        handleGifLinkInCommentMethod.addInstructionsWithLabels(
            0,
            """
                invoke-static { }, $EXTENSION_CLASS_DESCRIPTOR->skipGifLink()Z
                move-result v0
                if-eqz v0, :original
                return-void
            """,
            ExternalLabel("original", handleGifLinkInCommentMethod.getInstruction(0)),
        )

        /** Calls the extension right after the matched TextView.setText. */
        fun CompositeMatch.embedAfterSetText() = method.apply {
            val index = this@embedAfterSetText[0]
            val textViewRegister = getInstruction<FiveRegisterInstruction>(index).registerC
            addInstruction(
                index + 1,
                "invoke-static { v$textViewRegister }, $EXTENSION_CLASS_DESCRIPTOR->embed(Landroid/widget/TextView;)V",
            )
        }

        // The comment's markdown, to find the real emote images (p1 is the Comment).
        commentHolderUpdateViewMethodMatch.embedAfterSetText().addInstruction(
            0,
            "invoke-static { p1 }, $EXTENSION_CLASS_DESCRIPTOR->setCommentSource(Ljava/lang/Object;)V",
        )

        detailLinkFragmentUpdateSelftextMethodMatch.embedAfterSetText()
        // Card mode shows the post text in a list, one TextView per row: YouTube titles and thumbnails are
        // drawn in the text, but there is no room for images under it, so they stay links.
        selftextAdapterGetViewMethodMatch.embedAfterSetText()
        // Image, GIF and video posts show their text under the media.
        postMediaHandlerConstructorMethodMatch.embedAfterSetText()
    }
}
