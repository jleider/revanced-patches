package app.revanced.patches.reddit.customclients.baconreader.misc.review

import app.revanced.patcher.definingClass
import app.revanced.patcher.gettingFirstMethodDeclaratively
import app.revanced.patcher.name
import app.revanced.patcher.patch.BytecodePatchContext
import app.revanced.patcher.returnType

private const val REVIEW_CLASS_DESCRIPTOR = "Lcom/onelouder/baconreader/review/ReviewLegacyImpl;"

/** Called from BaconReaderActivity.onResume: shows the feedback popup when enough time was spent in the app. */
internal val BytecodePatchContext.reviewResumeMethod by gettingFirstMethodDeclaratively {
    definingClass(REVIEW_CLASS_DESCRIPTOR)
    name("resume")
    returnType("V")
}

/** Called from BaconReaderActivity.onPause: adds the session time. */
internal val BytecodePatchContext.reviewPauseMethod by gettingFirstMethodDeclaratively {
    definingClass(REVIEW_CLASS_DESCRIPTOR)
    name("pause")
    returnType("V")
}

internal val BytecodePatchContext.reviewShowMethod by gettingFirstMethodDeclaratively {
    definingClass(REVIEW_CLASS_DESCRIPTOR)
    name("showReview")
    returnType("V")
}

internal val BytecodePatchContext.reviewIsAvailableMethod by gettingFirstMethodDeclaratively {
    definingClass(REVIEW_CLASS_DESCRIPTOR)
    name("isAvailable")
    returnType("Z")
}
