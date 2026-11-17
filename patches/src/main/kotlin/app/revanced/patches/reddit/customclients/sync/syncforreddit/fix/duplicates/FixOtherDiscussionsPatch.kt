package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.duplicates

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.extension.sharedExtensionPatch
import app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.multisub.redditRequestConstructorMethodMatch

private const val EXTENSION_CLASS_DESCRIPTOR = "Lapp/revanced/extension/syncforreddit/FixOtherDiscussionsPatch;"

@Suppress("unused")
val fixOtherDiscussionsPatch = bytecodePatch(
    name = "Fix other discussions",
    description = "Finds other posts of the same link through Reddit search, because the old lookup now redirects to a blocked page.",
) {
    dependsOn(sharedExtensionPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        // RedditRequest(Context, int, String url, ...): p3 is the URL. The rewritten URL is on
        // oauth.reddit.com, so OAuthPostsRequest.getHeaders() no longer strips the bearer token.
        redditRequestConstructorMethodMatch.method.addInstructions(
            0,
            """
                invoke-static { p3 }, $EXTENSION_CLASS_DESCRIPTOR->rewriteUrl(Ljava/lang/String;)Ljava/lang/String;
                move-result-object p3
            """,
        )
    }
}
