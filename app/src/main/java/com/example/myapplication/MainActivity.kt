package com.example.myapplication

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.Executors
import kotlin.concurrent.scheduleAtFixedRate

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var locationTextView: TextView
    private lateinit var startServiceButton: Button
    private lateinit var stopServiceButton: Button
    private lateinit var map: GoogleMap
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // Tempo de conexão
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // Tempo de leitura
        .writeTimeout(30, java.util.concurrent.TimeUnit.SECONDS) // Tempo de escrita
        .build()
    private var executor = Executors.newSingleThreadScheduledExecutor()

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val latitude = intent?.getDoubleExtra("latitude", 0.0) ?: 0.0
            val longitude = intent?.getDoubleExtra("longitude", 0.0) ?: 0.0
            val latLng = LatLng(latitude, longitude)
            map.clear()
            map.addMarker(MarkerOptions().position(latLng).title("Você está aqui"))
            map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, 15f))
            locationTextView.text = "Latitude: $latitude, Longitude: $longitude"

            // Cancel any existing scheduled task before starting a new one
            executor.shutdownNow()
            executor = Executors.newSingleThreadScheduledExecutor()
            executor.scheduleAtFixedRate({
                sendLocationToServer(latitude, longitude)
            }, 0, 10, java.util.concurrent.TimeUnit.SECONDS)
        }
    }

    private val locationErrorReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val error = intent?.getStringExtra("error") ?: "Unknown error"
            android.widget.Toast.makeText(this@MainActivity, "Error updating location: $error", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        locationTextView = findViewById(R.id.locationTextView)
        startServiceButton = findViewById(R.id.startServiceButton)
        stopServiceButton = findViewById(R.id.stopServiceButton)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        val mapFragment = supportFragmentManager.findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            if (permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
            ) {
                startLocationService()
            } else {
                locationTextView.text = "Permissão negada"
            }
        }

        when {
            ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(
                this, Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED -> {
                startLocationService()
            }
            else -> {
                requestPermissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

        startServiceButton.setOnClickListener {
            startLocationService()
        }

        stopServiceButton.setOnClickListener {
            stopLocationService()
        }

        // Register for location update broadcasts
        LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, IntentFilter("LocationUpdate"))

        // Register for location error broadcasts
        LocalBroadcastManager.getInstance(this).registerReceiver(locationErrorReceiver, IntentFilter("LocationUpdateError"))
    }

    private fun startLocationService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(Intent(this, LocationService::class.java))
        } else {
            startService(Intent(this, LocationService::class.java))
        }
    }

    private fun stopLocationService() {
        stopService(Intent(this, LocationService::class.java))
    }

    private fun sendLocationToServer(latitude: Double, longitude: Double) {
        val json = JSONObject().apply {
            put("latitude", latitude)
            put("longitude", longitude)
        }

        val requestBody = RequestBody.create("application/json; charset=utf-8".toMediaTypeOrNull(), json.toString())
        val request = Request.Builder()
            .url("http://192.168.1.2:3000/location")  // Certifique-se de que a URL está correta
            .post(requestBody)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                // Handle failure
                e.printStackTrace()  // Log the error for debugging
            }

            override fun onResponse(call: Call, response: Response) {
                if (response.isSuccessful) {
                    // Handle success
                    val responseBody = response.body?.string()  // Get the response body as a string
                    Log.d("MainActivity", "Location successfully sent. Response: $responseBody")
                } else {
                    // Handle unsuccessful response
                    Log.d("MainActivity", "Failed to send location. Response code: ${response.code}")
                }
            }
        })
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
    }
}
