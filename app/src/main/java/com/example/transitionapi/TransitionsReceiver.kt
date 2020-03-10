package com.example.transitionapi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.text.TextUtils
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import java.text.SimpleDateFormat
import java.util.*

/**
 * Handles intents from from the Transitions API.
 */
class TransitionsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "onReceive")
        if (!TextUtils.equals(INTENT_ACTION, intent.action)) {
            Log.d(TAG,"Received an unsupported action in TransitionsReceiver: action = ${intent.action}")
            return
        }

        if (ActivityTransitionResult.hasResult(intent)) {
            ActivityTransitionResult.extractResult(intent)?.let { result ->
                for (event in result.transitionEvents) {
                    val info =
                        "Transition: ${toActivityString(event.activityType)} (${toTransitionType(
                            event.transitionType
                        )}) ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
                    Log.d(TAG, info)
                }
            }

        }
    }

    companion object {
        private const val TAG = "TransitionsReceiver"

        // Action fired when transitions are triggered.
        const val INTENT_ACTION = "com.example.transitionapi.TRANSITIONS_RECEIVER_ACTION"

        private fun toActivityString(activity: Int): String {
            return when (activity) {
                DetectedActivity.STILL -> "STILL"
                DetectedActivity.WALKING -> "WALKING"
                else -> "UNKNOWN"
            }
        }

        private fun toTransitionType(transitionType: Int): String {
            return when (transitionType) {
                ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
                ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
                else -> "UNKNOWN"
            }
        }
    }
}