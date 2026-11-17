package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.deadservices

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext

// CheckDeviantArtRequest(String, Listener, ErrorListener).
internal val BytecodePatchContext.deviantArtConstructorMethodMatch by composingFirstMethod {
    name("<init>")
    instructions("http://backend.deviantart.com/oembed?url="())
    custom { immutableClassDef.sourceFile == "CheckDeviantArtRequest.java" }
}
