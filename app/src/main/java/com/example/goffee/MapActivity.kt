package com.example.goffee

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import android.content.Intent
import android.content.pm.PackageManager
import android.view.View
import android.widget.Button
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import android.Manifest
import android.location.Location
import android.util.Log

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var mMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Mendapatkan instance FusedLocationProviderClient
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Mendapatkan SupportMapFragment dan notifikasi saat peta siap digunakan
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.map) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Ketika pengguna selesai memilih lokasi
        findViewById<Button>(R.id.confirm_button).setOnClickListener {
            // Dapatkan posisi tengah layar
            val centerLatLng = mMap.cameraPosition.target

            // Kirim hasil lokasi kembali ke activity sebelumnya
            val resultIntent = Intent().apply {
                putExtra("latitude", centerLatLng.latitude)
                putExtra("longitude", centerLatLng.longitude)
            }
            setResult(RESULT_OK, resultIntent)
            finish()
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mMap = googleMap

        // Coba dapatkan lokasi sekarang
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            fusedLocationClient.lastLocation
                .addOnSuccessListener { location: Location? ->
                    if (location != null) {
                        // Lokasi berhasil ditemukan
                        val currentLatLng = LatLng(location.latitude, location.longitude)
                        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(currentLatLng, 15f))
                    } else {
                        // Lokasi tidak ditemukan, gunakan lokasi fallback
                        setFallbackLocation()
                    }
                }
                .addOnFailureListener {
                    // Gagal mendapatkan lokasi, gunakan lokasi fallback
                    setFallbackLocation()
                }
        } else {
            // Jika tidak ada izin, gunakan lokasi fallback
            setFallbackLocation()
        }
    }

    // Set lokasi fallback ke Yogyakarta
    private fun setFallbackLocation() {
        val yogyakartaLatLng = LatLng(-7.797068, 110.370529) // Koordinat Yogyakarta
        mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(yogyakartaLatLng, 15f))
        Log.d("MapActivity", "Using fallback location: Yogyakarta")
    }
}