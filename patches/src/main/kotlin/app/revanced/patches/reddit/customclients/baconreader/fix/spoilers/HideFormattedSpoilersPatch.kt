package app.revanced.patches.reddit.customclients.baconreader.fix.spoilers

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch

private const val ELEMENT = "Lcom/onelouder/baconreader/parser/RedditParser\$Element;"

@Suppress("unused")
val hideFormattedSpoilersPatch = bytecodePatch(
    name = "Hide formatted spoilers",
    description = "Hides spoilers (>!...!<) that contain formatting, links or code behind the spoiler box, " +
        "instead of showing them in plain sight.",
) {
    compatibleWith(
        "com.onelouder.baconreader"("6.1.4"),
        "com.onelouder.baconreader.premium"("6.1.4"),
    )

    apply {
        // The app only hides a spoiler (<span class="md-spoiler-text">) whose content is a single plain text.
        // Any spoiler with content is one: the box then covers all of its formatted content.
        isInternalSpoilerMethod.addInstructions(
            0,
            """
                const-string v0, "class"
                invoke-virtual { p1, v0 }, $ELEMENT->getAttribute(Ljava/lang/String;)Ljava/lang/String;
                move-result-object v0
                const-string v1, "md-spoiler-text"
                invoke-virtual { v1, v0 }, Ljava/lang/String;->equals(Ljava/lang/Object;)Z
                move-result v0
                if-eqz v0, :not_spoiler
                iget-object v0, p1, $ELEMENT->children:Ljava/util/List;
                if-eqz v0, :not_spoiler
                invoke-interface { v0 }, Ljava/util/List;->isEmpty()Z
                move-result v0
                if-nez v0, :not_spoiler
                const/4 v0, 0x1
                return v0
                :not_spoiler
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
