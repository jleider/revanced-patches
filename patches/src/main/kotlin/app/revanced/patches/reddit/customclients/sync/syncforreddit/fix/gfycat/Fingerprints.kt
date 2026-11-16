package app.revanced.patches.reddit.customclients.sync.syncforreddit.fix.gfycat

import app.revanced.patcher.*
import app.revanced.patcher.patch.BytecodePatchContext

internal val BytecodePatchContext.parserMatch by composingFirstMethod {
    parameterTypes("Landroid/content/Context;", "I", "Lorg/json/JSONObject;", "Ljava/util/ArrayList;", "I", "Z", "Z", "Ljava/lang/String;")
    instructions(string { this == "video_preview" })
    custom { immutableClassDef.sourceFile == "PostParserHelper.java" }
}
internal val BytecodePatchContext.viewerMatch by composingFirstMethod {
    parameterTypes("Ljava/lang/String;")
    instructions(string { this == "Detected reddit streaming video" }, string { this == "Detected Gfycat image" })
    custom { immutableClassDef.sourceFile == "ImageViewerFragment.java" }
}
internal val BytecodePatchContext.autoplayMatch by composingFirstMethod {
    instructions(string { contains("image?url=redgifs-") }, string { contains("image?url=gfycat-") })
    custom { immutableClassDef.sourceFile == "SimpleExoPlayerTextureView.java" }
}
internal val BytecodePatchContext.serializerMatch by composingFirstMethod {
    instructions(string { this == "video_preview" })
    custom { immutableClassDef.sourceFile == "AutoValue_Post.java" }
}
internal val BytecodePatchContext.queueMatch by composingFirstMethod {
    name("add")
    instructions(string { this == "add-to-queue" })
    custom { immutableClassDef.sourceFile == "RequestQueue.java" }
}
