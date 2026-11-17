package app.revanced.patches.reddit.customclients.sync.syncforreddit.misc.telemetry

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

// BaseActivity reports suspected piracy: reports account, country, purchase token and installer
// to the developer's "hmmm" Firebase callable when Ultra looks unlicensed.
internal val BytecodePatchContext.reportSuspectedPiracyMethodMatch by composingFirstMethod {
    returnType("V")
    parameterTypes("Ljava/lang/String;")
    instructions("hmmm"())
    custom { immutableClassDef.sourceFile == "BaseActivity.java" }
}
