package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.multisub

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixCombinedSubredditsPatch;"

@Suppress("unused")
val fixCombinedSubredditFeedsPatch = bytecodePatch(
    name = "Fix combined subreddit feeds",
    description = "Fixes feeds that combine subreddits (r/a+b) redirecting to the Reddit homepage.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // p3 is the request URL, passed straight to Volley's Request constructor.
        redditRequestConstructorMatch.method.addInstructions(
            0,
            """
                invoke-static { p3 }, $EXTENSION_CLASS_DESCRIPTOR->fixUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p3
            """,
        )
    }
}
