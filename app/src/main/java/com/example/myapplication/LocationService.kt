package com.example.myapplication

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

class LocationService : Service() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationCallback: LocationCallback
    private val client = OkHttpClient()

    override fun onCreate() {
        super.onCreate()

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                for (location in locationResult.locations) {
                    val intent = Intent("LocationUpdate")
                    intent.putExtra("latitude", location.latitude)
                    intent.putExtra("longitude", location.longitude)
                    LocalBroadcastManager.getInstance(this@LocationService).sendBroadcast(intent)

                    sendLocationToServer(location.latitude, location.longitude)
                }
            }
        }

        startLocationUpdates()
    }

    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000
            fastestInterval = 5000
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channelId = "location_service_channel"
            val channel = NotificationChannel(
                channelId,
                "Location Service",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)

            val notification: Notification = NotificationCompat.Builder(this, channelId)
                .setContentTitle("Location Service")
                .setContentText("Running")
                .setSmallIcon(R.mipmap.ic_launcher)
                .build()

            startForeground(1, notification)
        }
    }

    private fun sendLocationToServer(latitude: Double, longitude: Double) {
        val url = "http://192.168.1.2:3000/location" // Substitua com a URL do seu servidor
        val json = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
        }
        val body = json.toString().toRequestBody()

        CoroutineScope(Dispatchers.IO).launch {
            val request = Request.Builder()
                .url(url)
                .post(body)
                .build()

            client.newCall(request).enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: IOException) {
                    // Log the error
                    android.util.Log.e("LocationService", "Failed to send location", e)

                    // Optionally, you could send a broadcast or notify the user in some way
                    val errorIntent = Intent("LocationUpdateError")
                    errorIntent.putExtra("error", "Failed to send location: ${e.message}")
                    LocalBroadcastManager.getInstance(this@LocationService).sendBroadcast(errorIntent)
                }

                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    if (!response.isSuccessful) {
                        // Log the error response
                        val errorMessage = response.body?.string() ?: "Unknown error"
                        android.util.Log.e("LocationService", "Failed to send location: $errorMessage")

                        // Optionally, you could send a broadcast or notify the user in some way
                        val errorIntent = Intent("LocationUpdateError")
                        errorIntent.putExtra("error", errorMessage)
                        LocalBroadcastManager.getInstance(this@LocationService).sendBroadcast(errorIntent)
                    }
                }
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
