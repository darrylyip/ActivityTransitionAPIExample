package com.example.transitionapi

import android.Manifest
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PersistableBundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.OnRequestPermissionsResultCallback
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import java.text.SimpleDateFormat
import java.util.*


/**
 * Demos enabling/disabling Activity Recognition transitions, e.g., starting or stopping a walk,
 * run, drive, etc.).
 */
class MainActivity : AppCompatActivity(), OnRequestPermissionsResultCallback {
    private var mode = Mode.TRANSITION
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
        activityTrackingEnabled = savedInstanceState?.getBoolean("ACTIVITY_TRACKING_ENABLED") ?: false
        mode = if (savedInstanceState == null || savedInstanceState?.getSerializable("TRACKING_MODE") == null) Mode.TRANSITION else savedInstanceState.getSerializable("TRACKING_MODE") as Mode

        // setup intent to be handled by external BroadcastReceiver
//        val intent = Intent(this, TransitionsReceiver::class.java)
//        intent.action = TransitionsReceiver.INTENT_ACTION
//        pendingIntent = PendingIntent.getBroadcast(applicationContext, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        // setup local intent
        val intent = Intent(TransitionsReceiver.INTENT_ACTION)
        pendingIntent = PendingIntent.getBroadcast(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)

        printToScreen("App initialized: mode=${mode.name}, activityTrackingEnabled=$activityTrackingEnabled")
    }

    override fun onResume() {
        super.onResume()
        updateBtns()
    }

    override fun onStart() {
        super.onStart()
        registerReceiver(localTransitionsReceiver, IntentFilter(TransitionsReceiver.INTENT_ACTION))
    }

    override fun onPause() {
        if (activityTrackingEnabled) {
            disableActivityTransitions()
        }
        super.onPause()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putBoolean("ACTIVITY_TRACKING_ENABLED", activityTrackingEnabled)
        outState.putSerializable("TRACKING_MODE", mode)
        super.onSaveInstanceState(outState)
    }

    override fun onStop() {
        unregisterReceiver(localTransitionsReceiver)
        super.onStop()
    }

    /**
     * Registers callbacks for [ActivityTransition] events via a custom
     * [BroadcastReceiver]
     */
    private fun enableActivityTransitions() {
        Log.d(TAG, "enableActivityTransitions()")
        val task: Task<Void> = if (mode == Mode.TRANSITION) {
            // Register for Transitions Updates using Transition API
            val request = ActivityTransitionRequest(activityTransitions)
            ActivityRecognition.getClient(this).requestActivityTransitionUpdates(request, pendingIntent)
        } else {
            // Register for activity using Activity API
            ActivityRecognition.getClient(this).requestActivityUpdates(3000, pendingIntent)
        }
        task.addOnSuccessListener {
            activityTrackingEnabled = true
            updateBtns()
            printToScreen("Transitions Api was successfully registered.")
        }
        task.addOnFailureListener { e ->
            printToScreen("Transitions Api could NOT be registered: $e")
            Log.e(TAG, "Transitions Api could NOT be registered: $e")
            updateBtns()
        }
    }

    /**
     * Unregisters callbacks for [ActivityTransition] events via a custom
     * [BroadcastReceiver]
     */
    private fun disableActivityTransitions() {
        Log.d(TAG, "disableActivityTransitions()")

        getSharedPreferences(TRANSITION_PREFS, Context.MODE_PRIVATE)
            .edit().remove(CURRENT_ACTIVITY_TYPE).apply()

        val task: Task<Void> = if (mode == Mode.TRANSITION) {
            ActivityRecognition.getClient(this).removeActivityTransitionUpdates(pendingIntent)
        } else {
            ActivityRecognition.getClient(this).removeActivityUpdates(pendingIntent)
        }

        task.addOnSuccessListener {
            activityTrackingEnabled = false
            updateBtns()
            printToScreen("Transitions successfully unregistered.")
        }
        .addOnFailureListener { e ->
            printToScreen("Transitions could not be unregistered: $e")
            updateBtns()
            Log.e(TAG, "Transitions could not be unregistered: $e")
        }
    }

    /**
     * On devices Android 10 and beyond (29+), you need to ask for the ACTIVITY_RECOGNITION via the
     * run-time permissions.
     */
    private fun activityRecognitionPermissionApproved(): Boolean {
        return if (runningQOrLater) {
            PackageManager.PERMISSION_GRANTED == ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            )
        } else {
            true
        }
    }

    private fun initTracking() {
        printToScreen("MODE: ${mode.name}")
        if (activityRecognitionPermissionApproved()) {
            toggleTracking()
        } else {
            ActivityCompat.requestPermissions(
                this, arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                PERMISSION_REQUEST_ACTIVITY_RECOGNITION
            )
        }
    }

    private fun toggleTracking() {
        if (activityTrackingEnabled) {
            disableActivityTransitions()
        } else {
            enableActivityTransitions()
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val permissionResult = "Request code: $requestCode, Permissions: ${permissions.contentToString()}, Results: ${grantResults.contentToString()}"
        Log.d(TAG, "onRequestPermissionsResult(): $permissionResult")

        when (requestCode) {
            PERMISSION_REQUEST_ACTIVITY_RECOGNITION -> {
                // If request is cancelled, the result arrays are empty.
                if ((grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED)) {
                    toggleTracking()
                } else {
                    finish()
                }
                return
            }
            else -> {
                // Ignore all other requests.
            }
        }
    }

    fun onClickEnableOrDisableActivityRecognition(view: View) {
        mode = Mode.ACTIVITY
        initTracking()
    }

    fun onClickEnableOrDisableTransitionRecognition(view: View) {
        mode = Mode.TRANSITION
        initTracking()
    }

    private fun updateBtns() {
        if (activityTrackingEnabled) {
            if (mode == Mode.ACTIVITY) {
                btnActivity.isEnabled = true
                btnTransition.isEnabled = false
            } else {
                btnActivity.isEnabled = false
                btnTransition.isEnabled = true
            }
        } else {
            btnActivity.isEnabled = true
            btnTransition.isEnabled = true
        }

    }

    private fun printToScreen(message: String) {
        logFragment?.logView?.println(message)
        Log.d(TAG, message)
    }

    inner class LocalTransitionsReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
//            Log.d(TAG, "onReceive")
            if (!TextUtils.equals(TransitionsReceiver.INTENT_ACTION, intent.action)) {
                printToScreen("Received an unsupported action in TransitionsReceiver: action = ${intent.action}")
                return
            }

            val sharedPrefs = context.getSharedPreferences(TRANSITION_PREFS, Context.MODE_PRIVATE)
            val currentActivityType = sharedPrefs.getInt(CURRENT_ACTIVITY_TYPE, -1)
//            printToScreen("CURRENT ACTIVITY TYPE: ${toActivityString(currentActivityType)}")

            if (mode == Mode.TRANSITION) {
                if (ActivityTransitionResult.hasResult(intent)) {
                    val result = ActivityTransitionResult.extractResult(intent)
                    result?.run {
                        for (event in result.transitionEvents) {
                            val info =
                                "Transition: ${toActivityString(event.activityType)} (${toTransitionType(
                                    event.transitionType
                                )}) ${SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())}"
                            printToScreen(info)
                        }
                    }
                }
            } else {
                if (ActivityRecognitionResult.hasResult(intent)) {
                    val result = ActivityRecognitionResult.extractResult(intent)
                    result?.run {
                        // printToScreen("*** $result")
                        for (detectedActivity in result.probableActivities) {
                            when (detectedActivity.type) {
                                DetectedActivity.STILL -> {
                                    handleDetectedActivity(context, detectedActivity, sharedPrefs)
                                }
                                DetectedActivity.WALKING -> {
                                    handleDetectedActivity(context, detectedActivity, sharedPrefs)
                                }
                                else -> {}
                            }

                        }
                    }
                }
            }
        }

        private fun onStartedWalking(context: Context) {
            Log.d(TAG, "onStartedWalking ${SimpleDateFormat(
                "HH:mm:ss",
                Locale.US
            ).format(Date())}")
            val i = Intent(context, ActivityRecorderService::class.java)
            i.putExtra(ActivityRecorderService.EVENT, ActivityRecorderService.STARTED_WALKING)
            context.startService(i)
        }

        private fun onStoppedWalking(context: Context) {
            Log.d(TAG, "onStoppedWalking ${SimpleDateFormat(
                "HH:mm:ss",
                Locale.US
            ).format(Date())}")
            val i = Intent(context, ActivityRecorderService::class.java)
            i.putExtra(ActivityRecorderService.EVENT, ActivityRecorderService.STOPPED_WALKING)
            context.startService(i)
        }

        private fun handleDetectedActivity(context: Context, detectedActivity: DetectedActivity, sharedPrefs: SharedPreferences) {
            if (detectedActivity.confidence > 40) {
                val currentActivityType = sharedPrefs.getInt(CURRENT_ACTIVITY_TYPE, -1)
                val info =
                    "Activity: ${toActivityString(detectedActivity.type)} (${detectedActivity.confidence}) ${SimpleDateFormat(
                        "HH:mm:ss",
                        Locale.US
                    ).format(Date())}"
                printToScreen(info)

                if (currentActivityType < 0) {
                    // no currentActivityType
                    sharedPrefs.edit().putInt(CURRENT_ACTIVITY_TYPE, detectedActivity.type).commit()
                } else if (currentActivityType != detectedActivity.type) {
                    if (currentActivityType == DetectedActivity.STILL
                        && detectedActivity.type == DetectedActivity.WALKING) {
                        sharedPrefs.edit().putInt(CURRENT_ACTIVITY_TYPE, detectedActivity.type).commit()
                        // transition from still to walking
                        onStartedWalking(context)
                    } else if (currentActivityType == DetectedActivity.WALKING
                        && detectedActivity.type == DetectedActivity.STILL) {
                        sharedPrefs.edit().putInt(CURRENT_ACTIVITY_TYPE, detectedActivity.type).commit()
                        // transition from walking to still
                        onStoppedWalking(context)
                    } else {
                        // do nothing
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "MainActivity"

        private const val PERMISSION_REQUEST_ACTIVITY_RECOGNITION = 45

        private const val CURRENT_ACTIVITY_TYPE = "currentActivityType"

        private const val TRANSITION_PREFS = "transitionPrefs"

        private enum class Mode {
            ACTIVITY, TRANSITION
        }

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
