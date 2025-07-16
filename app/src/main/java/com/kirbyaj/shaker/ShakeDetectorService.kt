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
import android.service.autofill.Field
import android.util.Log
import androidx.compose.foundation.layout.size
import androidx.compose.ui.geometry.isEmpty
import androidx.compose.ui.input.key.type
import androidx.core.app.NotificationCompat
import kotlin.io.path.name
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.random.nextInt

class ShakeDetectorService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var shakeMediaPlayer: MediaPlayer? = null
    private var buttonMediaPlayer: MediaPlayer? = null

    private var acceleration = 0f
    private var currentAcceleration = SensorManager.GRAVITY_EARTH
    private var lastAcceleration = SensorManager.GRAVITY_EARTH
    private val shakeThreshold = 4f

    private var wakeLock: PowerManager.WakeLock? = null // Declare WakeLock variable
    private val shakeSoundResourceIds = listOf(
        R.raw.moaning_1,
        R.raw.moaning_2,
        R.raw.moaning_3,
        R.raw.moaning_4,
        R.raw.moaning_5
    )

    private var currentShakeSoundResId: Int = 0
    private var currentButtonSoundResId: Int = 0

    companion object {
        const val NOTIFICATION_CHANNEL_ID = "ShakeDetectorChannel"
        const val NOTIFICATION_ID = 1
        const val ACTION_START_SERVICE = "ACTION_START_SERVICE"
        const val ACTION_STOP_SERVICE = "ACTION_STOP_SERVICE"
        const val ACTION_PLAY_BUTTON_SOUND = "ACTION_PLAY_BUTTON_SOUND"
        private const val WAKE_LOCK_TAG = "ShakeDetectorService::WakeLock"
    }

    @Volatile
    var isServiceRunning: Boolean = false
        private set


    override fun onCreate() {
        super.onCreate()
        Log.d("ShakeDetectorService", "onCreate")

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
        wakeLock?.setReferenceCounted(false)

        // Initialize MediaPlayers - they will pick their first random sound here
        initializeShakeMediaPlayer()
        initializeButtonMediaPlayer()

        createNotificationChannel()
    }

    private fun getRandomShakeSoundResId(): Int {
        if (shakeSoundResourceIds.isEmpty()) {
            Log.e("ShakeDetectorService", "Shake sound resource ID list is empty! Using fallback.")
            return R.raw.moaning_1 // Fallback if list is somehow empty (should not happen with hardcoded list)
        }
        return shakeSoundResourceIds.random() // Kotlin's convenient way to get a random element
    }

    private fun getRandomButtonSoundResId(): Int {
        return getRandomShakeSoundResId()
    }

    private fun initializeShakeMediaPlayer() {
        shakeMediaPlayer?.release()
        currentShakeSoundResId = getRandomShakeSoundResId()

        Log.d("ShakeDetectorService", "Initializing shake MediaPlayer with sound ID: $currentShakeSoundResId")
        try {
            shakeMediaPlayer = MediaPlayer.create(this, currentShakeSoundResId)
            shakeMediaPlayer?.setOnErrorListener { mp, what, extra ->
                Log.e("ShakeDetectorService", "Shake MediaPlayer error (ID: $currentShakeSoundResId): what=$what, extra=$extra")
                // Attempt to re-initialize with another sound from the list
                initializeShakeMediaPlayer()
                true
            }
            shakeMediaPlayer?.setOnCompletionListener {
                Log.d("ShakeDetectorService", "Shake MediaPlayer (ID: $currentShakeSoundResId) playback completed.")
                // To make it play a NEW random sound on the NEXT shake after completion:
                initializeShakeMediaPlayer()
            }
        } catch (e: Exception) {
            Log.e("ShakeDetectorService", "Error creating shake MediaPlayer with ID $currentShakeSoundResId", e)
            shakeMediaPlayer = null
        }
    }

    private fun initializeButtonMediaPlayer() {
        buttonMediaPlayer?.release()
        currentButtonSoundResId = getRandomButtonSoundResId()

        Log.d("ShakeDetectorService", "Initializing button MediaPlayer with sound ID: $currentButtonSoundResId")
        try {
            buttonMediaPlayer = MediaPlayer.create(this, currentButtonSoundResId)
            buttonMediaPlayer?.setOnErrorListener { mp, what, extra ->
                Log.e("ShakeDetectorService", "Button MediaPlayer error (ID: $currentButtonSoundResId): what=$what, extra=$extra")
                initializeButtonMediaPlayer()
                true
            }
            buttonMediaPlayer?.setOnCompletionListener {
                Log.d("ShakeDetectorService", "Button MediaPlayer (ID: $currentButtonSoundResId) playback completed.")
                // If you want the button sound to be different next time automatically after completion
                // initializeButtonMediaPlayer()
            }
        } catch (e: Exception) {
            Log.e("ShakeDetectorService", "Error creating button MediaPlayer with ID $currentButtonSoundResId", e)
            buttonMediaPlayer = null
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("ShakeDetectorService", "onStartCommand, Action: ${intent?.action}")
        when (intent?.action) {
            ACTION_START_SERVICE -> {
                if (!isServiceRunning) {
                    // Sounds are initialized in onCreate. If you want a new random shake sound
                    // picked every time the service is explicitly started (not just created),
                    // you can call initializeShakeMediaPlayer() here.
                    // initializeShakeMediaPlayer() // This would make it pick a new one now.

                    startForegroundServiceNotification()
                    if (accelerometer != null) {
                        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_UI)
                        if (wakeLock?.isHeld == false) wakeLock?.acquire()
                        isServiceRunning = true
                        Log.d("ShakeDetectorService", "Service marked as running.")
                    } else {
                        Log.e("ShakeDetectorService", "Accelerometer not available.")
                        stopSelf()
                    }
                } else {
                    Log.d("ShakeDetectorService", "Service already running.")
                    startForegroundServiceNotification() // Ensure it's in foreground
                }
            }
            ACTION_STOP_SERVICE -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
            ACTION_PLAY_BUTTON_SOUND -> {
                Log.d("ShakeDetectorService", "Play button sound action received")
                // Pick a new random sound for the button EACH time it's pressed
                initializeButtonMediaPlayer()
                playButtonSoundInternal()
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

                // The shakeMediaPlayer is already initialized (and re-initialized on completion)
                // to have a random sound ready.
                if (shakeMediaPlayer?.isPlaying == false) {
                    try {
                        shakeMediaPlayer?.start()
                        // The onCompletionListener will call initializeShakeMediaPlayer()
                        // to prepare for the *next* shake.
                    } catch (e: java.lang.IllegalStateException) {
                        Log.e("ShakeDetectorService", "Error starting shake MediaPlayer (ID: $currentShakeSoundResId)", e)
                        // Attempt to recover if it was in a bad state
                        initializeShakeMediaPlayer()
                        shakeMediaPlayer?.start()
                    }
                } else if (shakeMediaPlayer == null) {
                    Log.w("ShakeDetectorService", "shakeMediaPlayer was null on shake, re-initializing.")
                    initializeShakeMediaPlayer()
                    shakeMediaPlayer?.start()
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


    private fun playButtonSoundInternal() {
        if (buttonMediaPlayer?.isPlaying == true) {
            buttonMediaPlayer?.stop() // Stop and re-prepare if already playing
            try {
                buttonMediaPlayer?.prepare() // Not strictly necessary if re-creating, but good for reset
                buttonMediaPlayer?.seekTo(0)
            } catch (e: java.lang.Exception) {
                Log.e("ShakeDetectorService", "Error preparing/seeking button MediaPlayer (ID: $currentButtonSoundResId)", e)
            }
        }
        try {
            buttonMediaPlayer?.start()
        } catch (e: IllegalStateException) {
            Log.e("ShakeDetectorService", "Error starting button MediaPlayer (ID: $currentButtonSoundResId)", e)
            initializeButtonMediaPlayer() // Try to recover
            buttonMediaPlayer?.start()
        }

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

        if (wakeLock?.isHeld == true) {
            wakeLock?.release()
        }
        wakeLock = null
        isServiceRunning = false
        Log.d("ShakeDetectorService", "Resources released.")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null // Not using binding in this example
    }

    private fun createNotificationChannel() {
        val serviceChannel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            "Shake Detector Service Channel",
            NotificationManager.IMPORTANCE_HIGH
        )
        val manager = getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(serviceChannel)
        Log.d("ShakeDetectorService", "Notification channel created.")
    }
}
