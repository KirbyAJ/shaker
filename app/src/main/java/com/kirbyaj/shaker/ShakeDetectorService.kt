package com.kirbyaj.shaker // Or your actual package name

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlin.math.sqrt

class ShakeDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var shakeMediaPlayer: MediaPlayer? = null
    private var buttonMediaPlayer: MediaPlayer? = null

    private var acceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private val shakeThreshold = 5f

    private var wakeLock: PowerManager.WakeLock? = null // Declare WakeLock variable

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ShakeDetectorChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_PLAY_BUTTON_SOUND = "ACTION_PLAY_BUTTON_SOUND"
        private const val WAKE_LOCK_TAG = "ShakeDetectorService::WakeLock"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("ShakeDetectorService", "onCreate")
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        // Initialize PowerManager and WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        // Create a partial wake lock that keeps the CPU running
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        wakeLock?.setReferenceCounted(false) // Optional: manage release explicitly

        // Initialize MediaPlayers (as before)
        shakeMediaPlayer = try {
            MediaPlayer.create(this, R.raw.moan) // Replace
        } catch (e: Exception) {
            Log.e("ShakeDetectorService", "Error creating shake MediaPlayer", e)
            null
        }
        shakeMediaPlayer?.setOnErrorListener { _, what, extra ->
            Log.e("ShakeDetectorService", "Shake MediaPlayer error: what=$what, extra=$extra")
            true
        }
        // ... (shakeMediaPlayer onCompletionListener)

        buttonMediaPlayer = try {
            MediaPlayer.create(this, R.raw.moan) // Replace
        } catch (e: Exception) {
            Log.e("ShakeDetectorService", "Error creating button MediaPlayer", e)
            null
        }
        buttonMediaPlayer?.setOnErrorListener { _, what, extra ->
            Log.e("ShakeDetectorService", "Button MediaPlayer error: what=$what, extra=$extra")
            true
        }
        // ... (buttonMediaPlayer onCompletionListener)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ShakeDetectorService", "onStartCommand, Action: ${intent?.action}")

        when (intent?.action) {
            ACTION_START_SERVICE -> {
                startForegroundServiceNotification()
                if (accelerometer != null) {
                    sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
                    Log.d("ShakeDetectorService", "Accelerometer listener registered")
                    // Acquire the wake lock when the service starts listening
                    if (wakeLock?.isHeld == false) {
                        wakeLock?.acquire(20*60*1000) // You can specify a timeout here if needed
                        Log.d("ShakeDetectorService", "WakeLock acquired")
                    }
                } else {
                    Log.e("ShakeDetectorService", "Accelerometer not available, stopping service.")
                    stopSelf()
                }
            }
            ACTION_STOP_SERVICE -> {
                Log.d("ShakeDetectorService", "Stopping service via action.")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf() // onDestroy will be called, which releases the wake lock
            }
            ACTION_PLAY_BUTTON_SOUND -> {
                Log.d("ShakeDetectorService", "Play button sound action received")
                playButtonSound()
            }
        }
        return START_STICKY
    }

    private fun startForegroundServiceNotification() {
        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, pendingIntentFlags)

        val notification: Notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Shake Detector Active")
            .setContentText("Listening for device shakes.")
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Replace with your app's icon
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW) // Or higher if needed
            .build()

        startForeground(NOTIFICATION_ID, notification)
        Log.d("ShakeDetectorService", "Service started in foreground.")
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event?.sensor?.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            lastAcceleration = currentAcceleration
            currentAcceleration = sqrt((x * x + y * y + z * z).toDouble()).toFloat()
            val delta = currentAcceleration - lastAcceleration
            acceleration = acceleration * 0.9f + delta

            if (acceleration > shakeThreshold) {
                Log.d("ShakeDetectorService", "Shake detected! Acceleration: $acceleration")
                if (shakeMediaPlayer?.isPlaying == false) {
                    try {
                        shakeMediaPlayer?.start()
                    } catch (e: java.lang.IllegalStateException) {
                        Log.e("ShakeDetectorService", "Error starting shake MediaPlayer", e)
                    }
                }
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Not used
    }

    private fun playButtonSound() {
        if (buttonMediaPlayer?.isPlaying == true) {
            buttonMediaPlayer?.stop()
            try {
                buttonMediaPlayer?.prepare() // Required after stop
                buttonMediaPlayer?.seekTo(0)
            } catch (e: java.lang.Exception) {
                Log.e("ShakeDetectorService", "Error preparing/seeking button MediaPlayer", e)
            }
        }
        buttonMediaPlayer?.start()
        if (buttonMediaPlayer == null) {
            Log.e("ShakeDetectorService", "Button MediaPlayer is null, cannot play sound.")
        }
    }


    override fun onDestroy() {
        super.onDestroy()
        Log.d("ShakeDetectorService", "onDestroy")
        sensorManager.unregisterListener(this)

        shakeMediaPlayer?.release()
        shakeMediaPlayer = null
        buttonMediaPlayer?.release()
        buttonMediaPlayer = null

        // Release the wake lock when the service is destroyed
        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
            Log.d("ShakeDetectorService", "WakeLock released")
        }
        wakeLock = null

        Log.d("ShakeDetectorService", "Resources released.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not using binding in this example
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Shake Detector Service Channel",
                NotificationManager.IMPORTANCE_LOW // Or IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(serviceChannel)
            Log.d("ShakeDetectorService", "Notification channel created.")
        }
    }
}
