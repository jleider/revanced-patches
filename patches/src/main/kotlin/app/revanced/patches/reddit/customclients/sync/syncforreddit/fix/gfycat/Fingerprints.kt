package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.gfycat

import app.revanced.patcher.composingFirstMethod
import app.revanced.patcher.custom
import app.revanced.patcher.immutableClassDef
import app.revanced.patcher.instructions
import app.revanced.patcher.invoke
import app.revanced.patcher.name
import app.revanced.patcher.parameterTypes
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.string

internal val BytecodePatchContext.parsePostMethodMatch by composingFirstMethod {
    parameterTypes("Landroid/content/Context;", "I", "Lorg/json/JSONObject;", "Ljava/util/ArrayList;", "I", "Z", "Z", "Ljava/lang/String;")
    instructions("video_preview"())
    custom { immutableClassDef.sourceFile == "PostParserHelper.java" }
}

internal val BytecodePatchContext.viewImageMethodMatch by composingFirstMethod {
    parameterTypes("Ljava/lang/String;")
    instructions("Detected reddit streaming video"(), "Detected Gfycat image"())
    custom { immutableClassDef.sourceFile == "ImageViewerFragment.java" }
}

internal val BytecodePatchContext.autoplayVideoMethodMatch by composingFirstMethod {
    instructions(string { contains("image?url=redgifs-") }, string { contains("image?url=gfycat-") })
    custom { immutableClassDef.sourceFile == "SimpleExoPlayerTextureView.java" }
}

internal val BytecodePatchContext.serializePostMethodMatch by composingFirstMethod {
    instructions("video_preview"())
    custom { immutableClassDef.sourceFile == "AutoValue_Post.java" }
}

internal val BytecodePatchContext.addRequestMethodMatch by composingFirstMethod {
    name("add")
    instructions("add-to-queue"())
    custom { immutableClassDef.sourceFile == "RequestQueue.java" }
}
