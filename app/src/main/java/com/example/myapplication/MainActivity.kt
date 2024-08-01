package com.example.myapplication

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class MainActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private lateinit var googleMap: GoogleMap
    private lateinit var locationTextView: TextView

    private val locationReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val latitude = intent?.getDoubleExtra("latitude", 0.0)
            val longitude = intent?.getDoubleExtra("longitude", 0.0)
            if (latitude != null && longitude != null) {
                updateLocationOnMap(latitude, longitude)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mapView = findViewById(R.id.map)
        locationTextView = findViewById(R.id.locationTextView)

        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        val startServiceButton: Button = findViewById(R.id.startServiceButton)
        val stopServiceButton: Button = findViewById(R.id.stopServiceButton)

        startServiceButton.setOnClickListener {
            val intent = Intent(this, LocationService::class.java)
            startService(intent)
        }

        stopServiceButton.setOnClickListener {
            val intent = Intent(this, LocationService::class.java)
            stopService(intent)
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map
    }

    private fun updateLocationOnMap(latitude: Double, longitude: Double) {
        val location = LatLng(latitude, longitude)
        googleMap.clear()
        googleMap.addMarker(MarkerOptions().position(location).title("Current Location"))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(location, 15f))

        locationTextView.text = "Latitude: $latitude, Longitude: $longitude"
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
        LocalBroadcastManager.getInstance(this).registerReceiver(locationReceiver, IntentFilter("LocationUpdate"))
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(locationReceiver)
    }

    override fun onDestroy() {
        super.onDestroy()
        mapView.onDestroy()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
