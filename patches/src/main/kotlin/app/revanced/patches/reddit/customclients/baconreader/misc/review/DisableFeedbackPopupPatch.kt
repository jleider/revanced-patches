package app.revanced.patches.reddit.customclients.baconreader.misc.review

import app.revanced.patcher.extensions.addInstructions
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val disableFeedbackPopupPatch = bytecodePatch(
    name = "Disable feedback popup",
    description = "Disables the \"Please take a few moments to provide feedback about BaconReader\" popup.",
) {
    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    apply {
        // Every screen reports its time to ReviewLegacyImpl, which shows the popup once enough time was spent.
        // Stop the time tracking and the popup.
        listOf(reviewResumeMethod, reviewPauseMethod, reviewShowMethod).forEach {
            it.addInstructions(0, "return-void")
        }
        reviewIsAvailableMethod.addInstructions(
            0,
            """
                const/4 v0, 0x0
                return v0
            """,
        )
    }
}
