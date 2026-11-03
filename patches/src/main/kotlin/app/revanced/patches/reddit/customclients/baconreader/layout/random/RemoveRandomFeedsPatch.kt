package app.revanced.patches.reddit.customclients.baconreader.layout.random

import app.revanced.patcher.extensions.replaceInstruction
import app.revanced.patcher.patch.bytecodePatch

@Suppress("unused")
val removeRandomFeedsPatch = bytecodePatch(
    name = "Remove Random feeds",
    description = "Removes Random and My Random from the feed list. " +
        "Reddit removed r/random and r/myrandom in December 2024, so they never load.",
) {
    compatibleWith(
        "com.onelouder.baconreader",
        "com.onelouder.baconreader.premium",
    )

    apply {
        feedAdapterResetMethodMatch.let { match ->
            // list.add(feed): its result is not used.
            match.indices.forEach { indices -> match.method.replaceInstruction(indices[1], "nop") }
        }
    }
}
