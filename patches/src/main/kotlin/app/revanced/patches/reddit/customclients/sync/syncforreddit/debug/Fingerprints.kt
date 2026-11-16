package app.revanced.patches.reddit.customclients.sync.syncforreddit.debug

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

// Sync's internal logger (obfuscated as wc.i in v23.06.30). Its (String, String) log
// methods are compiled to a bare `return-void` in release builds.
internal val BytecodePatchContext.syncLoggerMethodMatch by composingFirstMethod {
    instructions("reddit_sync"())
    custom { immutableClassDef.sourceFile == "Logger.java" }
}

// onCreate is identified by its source file and startup diagnostic, not its class name.
internal val BytecodePatchContext.syncApplicationCreateMatch by composingFirstMethod {
    instructions("Startup time first: "())
    custom { immutableClassDef.sourceFile == "RedditApplication.java" }
}
