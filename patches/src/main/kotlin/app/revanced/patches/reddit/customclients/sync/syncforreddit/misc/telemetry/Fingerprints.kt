package app.revanced.patches.reddit.customclients.sync.syncforreddit.misc.telemetry

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

// BaseActivity.p0(String) in v23.06.30: reports account, country, purchase token and installer
// to the developer's "hmmm" Firebase callable when Ultra looks unlicensed.
internal val BytecodePatchContext.reportSuspectedPiracyMethodMatch by composingFirstMethod {
    returnType("V")
    parameterTypes("Ljava/lang/String;")
    instructions(string { equals("hmmm") })
    custom { immutableClassDef.sourceFile == "BaseActivity.java" }
}
