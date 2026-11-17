package app.revanced.patches.reddit.customclients.sync.syncforreddit.debug

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.patch.BytecodePatchContext

// Sync's internal logger. Its (String, String) log
// methods are compiled to a bare `return-void` in release builds.
internal val BytecodePatchContext.syncLoggerMethodMatch by composingFirstMethod {
    instructions("reddit_sync"())
    custom { immutableClassDef.sourceFile == "Logger.java" }
}

// onCreate is identified by its source file and startup diagnostic, not its class name.
internal val BytecodePatchContext.syncApplicationOnCreateMethodMatch by composingFirstMethod {
    instructions("Startup time first: "())
    custom { immutableClassDef.sourceFile == "RedditApplication.java" }
}
