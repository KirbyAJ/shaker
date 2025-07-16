package com.kirbyaj.shaker // Or your actual package name

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kirbyaj.shaker.ui.theme.ShakerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        setContent {
            ShakerTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreenWithServiceControl(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
}

@androidx.compose.runtime.Composable
fun MainScreenWithServiceControl(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    // var isServiceRunning by remember { mutableStateOf(false) } // You'd need a more robust way to check this

    androidx.compose.foundation.layout.Column(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Shake App: Background Service")
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            Log.d("MainActivity", "Start Service button clicked")
            val serviceIntent = Intent(context, ShakeDetectorService::class.java).apply {
                action = ShakeDetectorService.ACTION_START_SERVICE
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            // isServiceRunning = true // Update UI if needed
        }) {
            Text("Start Shake Listener")
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            Log.d("MainActivity", "Stop Service button clicked")
            val serviceIntent = Intent(context, ShakeDetectorService::class.java).apply {
                action = ShakeDetectorService.ACTION_STOP_SERVICE
            }
            context.startService(serviceIntent) // Can use startService to send commands too
            // isServiceRunning = false // Update UI if needed
        }) {
            Text("Stop Shake Listener")
        }

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(32.dp))

        Button(onClick = {
            Log.d("MainActivity", "Play Button Sound via Service clicked")
            val serviceIntent = Intent(context, ShakeDetectorService::class.java).apply {
                action = ShakeDetectorService.ACTION_PLAY_BUTTON_SOUND
            }
            // Service needs to be running for this to work if it's not started independently
            context.startService(serviceIntent)
        }) {
            Text("Play Sound (via Service)")
        }
    }
}

@Preview(showBackground = true)
@androidx.compose.runtime.Composable
fun DefaultPreview() {
    ShakerTheme {
        MainScreenWithServiceControl()
    }
}