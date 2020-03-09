package com.example.transitionapi

import android.Manifest
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.android.synthetic.main.activity_main.*
import java.text.SimpleDateFormat
import java.util.*


/**
 * Demos enabling/disabling Activity Recognition transitions, e.g., starting or stopping a walk,
 * run, drive, etc.).
 */
class MainActivity : AppCompatActivity() {
    private val runningQOrLater = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
    private var activityTrackingEnabled = false
    private val activityTransitions: MutableList<ActivityTransition> by lazy {
        val transitions = mutableListOf<ActivityTransition>()
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.STILL)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                .build()
        )
        transitions.add(
            ActivityTransition.Builder()
                .setActivityType(DetectedActivity.WALKING)
                .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
                .build()
        )
        transitions
    }

    private lateinit var pendingIntent: PendingIntent
    private var transitionsReceiver: TransitionsReceiver = TransitionsReceiver()
    private var logFragment: LogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        logFragment = supportFragmentManager.findFragmentById(R.id.log_fragment) as LogFragment?
        activityTrackingEnabled = false

        val intent = Intent(TRANSITIONS_RECEIVER_ACTION)
        pendingIntent = PendingIntent.getBroadcast(this@MainActivity, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        // The receiver listens for the PendingIntent above that is triggered by the system when an
        // activity transition occurs.
        LocalBroadcastManager.getInstance(this).registerReceiver(
            transitionsReceiver,
            IntentFilter(TRANSITIONS_RECEIVER_ACTION)
        )
        printToScreen("App initialized.")
    }

    override fun onResume() {
        super.onResume()

        if (!activityTrackingEnabled) {
            initTracking()
        }
    }

    override fun onPause() { // TODO: Disable activity transitions when user leaves the app.
        if (activityTrackingEnabled) {
            disableActivityTransitions()
        }
        super.onPause()
    }

    override fun onStop() { // TODO: Unregister activity transition receiver when user leaves the app.
        if (transitionsReceiver != null) {
            LocalBroadcastManager.getInstance(this).unregisterReceiver(transitionsReceiver)
        }
        super.onStop()
    }

    /**
     * Registers callbacks for [ActivityTransition] events via a custom
     * [BroadcastReceiver]
     */
    private fun enableActivityTransitions() {
        Log.d(TAG, "enableActivityTransitions()")
        val request = ActivityTransitionRequest(activityTransitions)
        // Register for Transitions Updates.
        val task: Task<Void> = ActivityRecognition.getClient(this).requestActivityTransitionUpdates(request, pendingIntent)
        task.addOnSuccessListener {
            activityTrackingEnabled = true
            printToScreen("Transitions Api was successfully registered.")
        }
        task.addOnFailureListener { e ->
            printToScreen("Transitions Api could NOT be registered: $e")
            Log.e(TAG, "Transitions Api could NOT be registered: $e")
        }
    }

    /**
     * Unregisters callbacks for [ActivityTransition] events via a custom
     * [BroadcastReceiver]
     */
    private fun disableActivityTransitions() {
        Log.d(TAG, "disableActivityTransitions()")
        ActivityRecognition.getClient(this)
            .removeActivityTransitionUpdates(pendingIntent)
            .addOnSuccessListener {
                activityTrackingEnabled = false
                printToScreen("Transitions successfully unregistered.")
            }
            .addOnFailureListener { e ->
                printToScreen("Transitions could not be unregistered: $e")
                Log.e(TAG, "Transitions could not be unregistered: $e")
            }
    }

    /**
     * On devices Android 10 and beyond (29+), you need to ask for the ACTIVITY_RECOGNITION via the
     * run-time permissions.
     */
    private fun activityRecognitionPermissionApproved(): Boolean {
        return if (runningQOrLater) {
            PackageManager.PERMISSION_GRANTED == ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            true
        }
    }

    private fun initTracking() {
        if (activityRecognitionPermissionApproved()) {
            if (activityTrackingEnabled) {
                disableActivityTransitions()
            } else {
                enableActivityTransitions()
            }
        } else {
            val startIntent = Intent(this, PermissionRationalActivity::class.java)
            startActivity(startIntent)
        }
    }

    private fun printToScreen(message: String) {
        logFragment?.logView?.println(message)
        Log.d(TAG, message)
    }

    /**
     * Handles intents from from the Transitions API.
     */
    inner class TransitionsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive")
            if (!TextUtils.equals(TRANSITIONS_RECEIVER_ACTION, intent.action)) {
                printToScreen("Received an unsupported action in TransitionsReceiver: action = ${intent.action}")
                return
            }

            if (ActivityTransitionResult.hasResult(intent)) {
                ActivityTransitionResult.extractResult(intent)?.let { result ->
                    for (event in result.transitionEvents) {
                        val info =
                            "Transition: ${toActivityString(event.activityType)} (${toTransitionType(
                                event.transitionType
                            )}) ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
                        printToScreen(info)
                    }
                }

            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        // Action fired when transitions are triggered.
        private const val TRANSITIONS_RECEIVER_ACTION =
            "com.example.transitionapi.TRANSITIONS_RECEIVER_ACTION"

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
