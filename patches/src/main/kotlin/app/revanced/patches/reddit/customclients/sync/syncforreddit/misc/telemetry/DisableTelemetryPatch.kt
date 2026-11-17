package app.revanced.patches.reddit.customclients.sync.syncforreddit.misc.telemetry

import app.revanced.patcher.patch.bytecodePatch
import app.revanced.patcher.patch.resourcePatch
import app.revanced.util.returnEarly
import org.w3c.dom.Element

/**
 * Sync still ships the original developer's Firebase project. Crashlytics registers its uncaught
 * exception handler regardless of the in-app "Crashlytics" toggle (that toggle only gates Sync's
 * manual log calls), and Firebase Analytics is always on. These manifest flags are read by the
 * Firebase SDKs at startup (DataCollectionArbiter, measurement zzag) and stop all collection.
 * The "hmmm" Firebase callable (username, country, purchase token, installer) is cut at its sender.
 */
private val collectionFlags = mapOf(
    "firebase_crashlytics_collection_enabled" to "false",
    "firebase_analytics_collection_enabled" to "false",
    "firebase_analytics_collection_deactivated" to "true",
    "google_analytics_adid_collection_enabled" to "false",
    "google_analytics_default_allow_ad_personalization_signals" to "false",
)

private val disablePiracyReportPatch = bytecodePatch {
    apply {
        reportSuspectedPiracyMethodMatch.method.returnEarly()
    }
}

@Suppress("unused")
val disableTelemetryPatch = resourcePatch(
    name = "Disable telemetry",
    description = "Stops Crashlytics crash reports and Firebase Analytics from reaching the original developer's Firebase project.",
) {
    dependsOn(disablePiracyReportPatch)

    compatibleWith(
        "com.laurencedawson.reddit_sync",
        "com.laurencedawson.reddit_sync.pro",
        "com.laurencedawson.reddit_sync.dev",
    )

    apply {
        document("AndroidManifest.xml").use { document ->
            val application = document.getElementsByTagName("application").item(0) as Element
            val existing = document.getElementsByTagName("meta-data").let { nodes ->
                (0 until nodes.length).map { nodes.item(it) as Element }
                    .filter { it.parentNode == application }
                    .associateBy { it.getAttribute("android:name") }
            }
            collectionFlags.forEach { (name, value) ->
                val node = existing[name] ?: document.createElement("meta-data").also {
                    it.setAttribute("android:name", name)
                    application.appendChild(it)
                }
                node.setAttribute("android:value", value)
            }
        }
    }
}
