package app.revanced.patches.reddit.customclients.baconreader.layout.comments

import app.revanced.patcher.extensions.addInstruction
import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.all.misc.resources.addResources
import app.revanced.patches.all.misc.resources.addResourcesPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.PreferenceScreen
import app.revanced.patches.reddit.customclients.baconreader.misc.settings.settingsPatch
import app.revanced.patches.shared.misc.settings.preference.ListPreference
import app.revanced.patches.shared.misc.settings.preference.NonInteractivePreference

private const val EXTENSION_CLASS_DESCRIPTOR =
    "Lapp/revanced/extension/baconreader/comments/LoadMoreCommentsPatch;"

@Suppress("unused")
val loadMoreCommentsPatch = bytecodePatch(
    name = "Load more comments",
    description = "Adds options to load up to 500 comments when opening a post instead of about 200, and to " +
        "load the \"load more comments\" with few replies automatically.",
) {
    dependsOn(sharedExtensionPatch, settingsPatch, addResourcesPatch)

    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        addResources("baconreader", "layout.comments.loadMoreCommentsPatch")

        PreferenceScreen.COMMENTS.addPreferences(
            // Not the setting's key: the settings screen only syncs switches, texts and lists with settings.
            // The preference reads and saves the setting itself.
            NonInteractivePreference(
                key = "revanced_baconreader_comment_limit_preference",
                titleKey = "revanced_baconreader_comment_limit_title",
                summaryKey = null,
                tag = "app.revanced.extension.baconreader.comments.CommentLimitPreference",
                selectable = true,
            ),
            ListPreference("revanced_baconreader_auto_load_more_comments"),
        )

        // The app sends no limit (0), so Reddit returns its default of about 200 comments: the setting instead.
        getCommentsMethod.addInstructions(
            0,
            """
                invoke-static { p5 }, $EXTENSION_CLASS_DESCRIPTOR->commentLimit(I)I
                move-result p5
            """,
        )

        // After the comment list gets its items, load the small "load more comments".
        processDataMethodMatch.method.addInstruction(
            processDataMethodMatch[0],
            "invoke-static { p0 }, $EXTENSION_CLASS_DESCRIPTOR->onItemsProcessed(Ljava/lang/Object;)V",
        )
    }
}
