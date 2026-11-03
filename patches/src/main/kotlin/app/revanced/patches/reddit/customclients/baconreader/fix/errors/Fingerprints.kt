package app.revanced.patches.reddit.customclients.baconreader.fix.errors

import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext

/** The task of every Reddit API request (an anonymous class in RedditApi.request). */
internal val BytecodePatchContext.redditRequestRunTaskMethod by gettingFirstMethodDeclaratively {
    name("runTask")
    instructions("bittersweet news"(String::contains))
}
