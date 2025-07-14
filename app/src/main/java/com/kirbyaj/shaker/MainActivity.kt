package com.kirbyaj.shaker // Or your actual package name

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.media.MediaPlayer
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kirbyaj.shaker.ui.theme.ShakerTheme
import kotlin.math.sqrt

// Build with Gemini
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ShakerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun MainScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var buttonMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }

    // Initialize MediaPlayer for the button sound
    DisposableEffect(Unit) {
        Log.d("ButtonSound", "Attempting to create MediaPlayer for button")
        buttonMediaPlayer = try {
            MediaPlayer.create(context, R.raw.moan)
        } catch (e: Exception) {
            Log.e("ButtonSound", "Error creating MediaPlayer for button", e)
            null
        }

        if (buttonMediaPlayer == null) {
            Log.e("ButtonSound", "MediaPlayer creation for button failed! Check R.raw.button_sound.")
        } else {
            Log.d("ButtonSound", "MediaPlayer for button created successfully")
            buttonMediaPlayer?.setOnErrorListener { _, what, extra ->
                Log.e("ButtonSound", "MediaPlayer (button) error: what=$what, extra=$extra")
                true // Error handled
            }
            buttonMediaPlayer?.setOnCompletionListener {
                Log.d("ButtonSound", "MediaPlayer (button) playback completed.")
                // Optional: it.seekTo(0) to allow replaying immediately
            }
        }

        onDispose {
            Log.d("ButtonSound", "Releasing MediaPlayer for button")
            buttonMediaPlayer?.release()
            buttonMediaPlayer = null
        }
    }

    ShakeDetectorEffect(
        shakeSoundResourceId = R.raw.moan  // Pass the resource ID for shake sound
    ) {
        Log.d("MainScreen", "Shake detected from ShakeDetectorEffect!")
    }

    Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Greeting(name = "Shake or Press!")
        Spacer(modifier = Modifier.height(32.dp))
        Button(onClick = {
            Log.d("MainScreen", "Button clicked")
            if (buttonMediaPlayer?.isPlaying == true) {
                buttonMediaPlayer?.stop() // Stop if already playing
                try {
                    buttonMediaPlayer?.prepare() // Need to prepare again after stop
                    buttonMediaPlayer?.seekTo(0) // Rewind to start
                } catch (e: Exception) {
                    Log.e("ButtonSound", "Error preparing/seeking button MediaPlayer after stop", e)
                }
            }
            buttonMediaPlayer?.start()
            if (buttonMediaPlayer == null) {
                Log.e("MainScreen", "Attempted to play sound, but buttonMediaPlayer is null.")
            }
        }) {
            Text("Play Sound")
        }
    }
}

@Composable
fun ShakeDetectorEffect(
    shakeSoundResourceId: Int,
    onShake: () -> Unit = {}
) {
    val context = LocalContext.current
    var shakeMediaPlayer by remember { mutableStateOf<MediaPlayer?>(null) }
    var sensorManager by remember { mutableStateOf<SensorManager?>(null) }
    var accelerometer by remember { mutableStateOf<Sensor?>(null) }

    var acceleration = 0f
    var currentAcceleration = SensorManager.GRAVITY_EARTH
    var lastAcceleration = SensorManager.GRAVITY_EARTH
    val shakeThreshold = 15f

    val sensorEventListener = remember {
        object : SensorEventListener {
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
                        Log.d("ShakeDetector", "Shake detected! Acceleration: $acceleration")
                        if (shakeMediaPlayer != null) {
                            if (shakeMediaPlayer?.isPlaying == false) {
                                Log.d("ShakeDetector", "shakeMediaPlayer is not null and not playing, starting sound.")
                                try {
                                    shakeMediaPlayer?.start()
                                } catch (e: IllegalStateException) {
                                    Log.e("ShakeDetector", "Error starting shakeMediaPlayer", e)
                                }
                            } else {
                                Log.d("ShakeDetector", "shakeMediaPlayer is null or already playing.")
                            }
                        } else {
                            Log.e("ShakeDetector", "Shake detected, but shakeMediaPlayer is NULL!")
                        }
                        onShake()
                    }
                }
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
                /* ... */
            }
        }
    }

    DisposableEffect(shakeSoundResourceId) {
        Log.d("ShakeDetector", "DisposableEffect for shake entered. Sound ID: $shakeSoundResourceId")
        sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        if (accelerometer != null) {
            Log.d("ShakeDetector", "Accelerometer found, registering listener")
            sensorManager?.registerListener(
                sensorEventListener,
                accelerometer,
                SensorManager.SENSOR_DELAY_UI
            )
        } else {
            Log.e("ShakeDetector", "Accelerometer NOT found!")
        }

        Log.d("ShakeDetector", "Attempting to create shakeMediaPlayer with ID: $shakeSoundResourceId")
        shakeMediaPlayer = try {
            MediaPlayer.create(context, shakeSoundResourceId)
        } catch (e: Exception) {
            Log.e("ShakeDetector", "Error creating shakeMediaPlayer", e)
            null
        }

        if (shakeMediaPlayer == null) {
            Log.e("ShakeDetector", "shakeMediaPlayer creation failed! Check resource ID $shakeSoundResourceId and Logcat.")
        } else {
            Log.d("ShakeDetector", "shakeMediaPlayer created successfully")
            shakeMediaPlayer?.setOnErrorListener { _, what, extra ->
                Log.e("ShakeDetector", "shakeMediaPlayer error: what=$what, extra=$extra")
                true
            }
            shakeMediaPlayer?.setOnCompletionListener {
                Log.d("ShakeDetector", "shakeMediaPlayer playback completed")
                it.seekTo(0)
            }
        }

        onDispose {
            Log.d("ShakeDetector", "onDispose for shake, unregistering listener and releasing shakeMediaPlayer")
            sensorManager?.unregisterListener(sensorEventListener)
            shakeMediaPlayer?.release()
            shakeMediaPlayer = null
        }
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ShakerTheme {
        MainScreen()
    }
}