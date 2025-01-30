package org.wikipedia.events

import android.content.Context
import android.os.Bundle
import android.util.Log
import com.google.firebase.analytics.FirebaseAnalytics
import org.wikipedia.settings.Prefs

class EventHandler(applicationContext: Context) {

    private val TAG = "ENVOY_LOG" // "EventHandler"

    private val firebaseAnalytics = FirebaseAnalytics.getInstance(applicationContext)

    fun logEvent(eventTag: String, eventBundle: Bundle) {
        // if (Prefs.isFirebaseLoggingEnabled) {
        // TEMP
        if (true) {
            Log.d(TAG, "firebase logging on, log event: " + eventTag)
            firebaseAnalytics.logEvent(eventTag, eventBundle)
        } else {
            Log.d(TAG, "firebase logging off, don't log event: " + eventTag)
        }
    }
}