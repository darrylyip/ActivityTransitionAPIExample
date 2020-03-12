package com.example.transitionapi

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*


class ActivityRecorderService : Service() {
    private var isRecording = false

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "onCreate")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: startId = $startId, isRecording = $isRecording")

        val sharedPrefs = getSharedPreferences(TRANSITION_PREFS, Context.MODE_PRIVATE)
        val lastRecordedAt = sharedPrefs.getLong(LAST_RECORDED_AT, -1)

        Log.d(TAG, "-- lastRecordedAt: ${timeToDateStr(lastRecordedAt)}")

        intent?.let {
            when (val event = it.getIntExtra(EVENT, -1)) {
                STARTED_WALKING -> startRecording()
                STOPPED_WALKING -> stopRecording()
                else -> throw IllegalArgumentException("EVENT is invalid: $event")
            }
        }
        //WALK START EVENT - Ignore if already recording
        // Call startForeground with notification to show user - need design from Design team
        // Create Task
        // Initialize RecorderManager
        // Record 30 seconds of walk data
        // Stop recording
        // Save to Bridge
        // Stop service

        //WALK END EVENT - Ignore if recording is already done
        // Should not need to call startForeground as service is either already running or it wasn't and
        // this is just a NO-OP and a call to stopService.

        // Stop recording
        // Save to bridge - any recording is worth saving
        // Stop service


        return START_REDELIVER_INTENT
    }

    private fun timeToDateStr(dateInMs: Long): String {
        return if (dateInMs > 0) {
            SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date(dateInMs))
        } else {
            ""
        }
    }


    private fun startRecording() {
        Log.d(TAG, "onStartedWalking ${SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date())}")
        isRecording = true
        startForeground()
        CoroutineScope(Dispatchers.Default).launch {
            delay(30000)
            stopRecording()
        }
    }

    private fun startForeground() {
        createNotificationChannel()
        val notification: Notification = NotificationCompat.Builder(this, getString(R.string.channel_id))
            .setContentTitle(getText(R.string.notification_title))
            .setContentText(getText(R.string.notification_message))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setTicker(getText(R.string.ticker_text))
            .build()
        startForeground(FOREGROUND_NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Create the NotificationChannel
            val name = getString(R.string.channel_name)
            val descriptionText = getString(R.string.channel_description)
            val importance = NotificationManager.IMPORTANCE_LOW
            val mChannel = NotificationChannel(getString(R.string.channel_id), name, importance)
            mChannel.description = descriptionText
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(mChannel)
        }
    }

    private fun stopRecording() {
        if (isRecording) {
            Log.d(
                TAG,
                "onStoppedWalking ${SimpleDateFormat(TIME_PATTERN, Locale.US).format(Date())}"
            )
            isRecording = false
        }
        stopSelf()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy")
        super.onDestroy()
    }
    companion object {
        private const val TAG = "ActivityRecorderService"

        private const val TRANSITION_PREFS = "transitionPrefs"

        private const val LAST_RECORDED_AT = "lastRecordedAt"

        private const val TIME_PATTERN = "HH:mm:ss"

        private const val FOREGROUND_NOTIFICATION_ID = 100

        const val EVENT = "EVENT"

        const val STARTED_WALKING = 1

        const val STOPPED_WALKING = 0
    }
}