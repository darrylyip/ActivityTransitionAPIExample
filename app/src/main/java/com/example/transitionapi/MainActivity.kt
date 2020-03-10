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
import android.view.View
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
    private var localTransitionsReceiver = LocalTransitionsReceiver()
    private var logFragment: LogFragment? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)
        logFragment = supportFragmentManager.findFragmentById(R.id.log_fragment) as LogFragment?
        activityTrackingEnabled = false

        // setup intent to be handled by external BroadcastReceiver
//        val intent = Intent(this, TransitionsReceiver::class.java)
//        intent.action = TransitionsReceiver.INTENT_ACTION
//        pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        // setup local intent
        val intent = Intent(TransitionsReceiver.INTENT_ACTION)
        pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        printToScreen("App initialized.")
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(localTransitionsReceiver, IntentFilter(TransitionsReceiver.INTENT_ACTION))
    }

//    override fun onResume() {
//        super.onResume()
//
//        if (!activityTrackingEnabled) {
//            initTracking()
//        }
//    }

    override fun onPause() {
        if (activityTrackingEnabled) {
            disableActivityTransitions()
        }
        super.onPause()
    }

    override fun onStop() {
//        LocalBroadcastManager.getInstance(this).unregisterReceiver(localTransitionsReceiver)
        unregisterReceiver(localTransitionsReceiver)
        super.onStop()
    }

    /**
     * Registers callbacks for [ActivityTransition] events via a custom
     * [BroadcastReceiver]
     */
    private fun enableActivityTransitions() {
        Log.d(TAG, "enableActivityTransitions()")

        // Register for Transitions Updates using Transition API
        // val request = ActivityTransitionRequest(activityTransitions)
        // val task: Task<Void> = ActivityRecognition.getClient(this).requestActivityTransitionUpdates(request, pendingIntent)
        // Register for activity using Activity API
        val task: Task<Void> = ActivityRecognition.getClient(this).requestActivityUpdates(3000, pendingIntent)
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
            // .removeActivityTransitionUpdates(pendingIntent)
            .removeActivityUpdates(pendingIntent)
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

    fun onClickEnableOrDisableActivityRecognition(view: View) {
        initTracking()
    }

    private fun printToScreen(message: String) {
        logFragment?.logView?.println(message)
        Log.d(TAG, message)
    }

    inner class LocalTransitionsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive")
            if (!TextUtils.equals(TransitionsReceiver.INTENT_ACTION, intent.action)) {
                printToScreen("Received an unsupported action in TransitionsReceiver: action = ${intent.action}")
                return
            }

//            if (ActivityTransitionResult.hasResult(intent)) {
//                ActivityTransitionResult.extractResult(intent)?.let { result ->
//                    for (event in result.transitionEvents) {
//                        val info =
//                            "Transition: ${toActivityString(event.activityType)} (${toTransitionType(
//                                event.transitionType
//                            )}) ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
//                        printToScreen(info)
//                    }
//                }
//            } else
            if (ActivityRecognitionResult.hasResult(intent)) {
                ActivityRecognitionResult.extractResult(intent)?.let { result ->
                    printToScreen("*** $result")
//                    for (detectedActivity in result.probableActivities) {
//                        val info =
//                            "Activity: ${toActivityString(detectedActivity.type)} (${detectedActivity.confidence}) ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
//                        printToScreen(info)
//                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        private fun toActivityString(activity: Int): String {
            return when (activity) {
                DetectedActivity.STILL -> "STILL"
                DetectedActivity.WALKING -> "WALKING"
                DetectedActivity.ON_FOOT -> "ON_FOOT"
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
