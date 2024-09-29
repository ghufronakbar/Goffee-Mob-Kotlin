package com.example.goffee

import CartAdapter
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.goffee.ApiService.ApiService
import com.google.android.material.button.MaterialButtonToggleGroup
import com.example.goffee.ApiService.TokenManager
import com.example.goffee.Models.CartItem
import com.example.goffee.Models.CartResponse
import com.example.goffee.api.RetrofitInstance
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.tasks.OnSuccessListener
import android.Manifest
import android.location.Geocoder
import android.location.Location
import android.os.Looper
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale


class CartPage : AppCompatActivity(), CartAdapter.OnAmountChangeListener {
    companion object {
        const val LOCATION_PERMISSION_REQUEST_CODE = 1000
        private const val MAP_REQUEST_CODE = 1
    }

    private lateinit var cartRecyclerView: RecyclerView
    private lateinit var totalAmount: TextView
    private lateinit var shppingAmount: TextView
    private lateinit var emptyCartText: TextView
    private lateinit var btn_back: ImageButton
    private lateinit var checkoutButton: TextView
    private lateinit var notes: TextView
    private lateinit var cartItems: MutableList<CartItem>
    private lateinit var alamatEditText: EditText
    private lateinit var toggleGroup: MaterialButtonToggleGroup
    private lateinit var shippingContainer: LinearLayout
    private lateinit var totalPriceLayout: RelativeLayout

    private lateinit var containerLocation: LinearLayout
    private lateinit var atauGunakan: TextView
    private lateinit var lokasiSekarang: TextView
    private lateinit var bukaPeta: TextView

    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private var shippingCost = 0
    private var latitude = ""
    private var longitude = ""

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_cart_page)

        emptyCartText = findViewById(R.id.emptyCartText)
        cartRecyclerView = findViewById(R.id.cartRecyclerView)
        totalAmount = findViewById(R.id.totalAmount)
        shppingAmount = findViewById(R.id.shppingAmount)
        checkoutButton = findViewById(R.id.checkoutButton)
        btn_back = findViewById(R.id.btn_back)
        alamatEditText = findViewById(R.id.alamat)
        notes = findViewById(R.id.notes)
        toggleGroup = findViewById(R.id.toggleGroup)
        shippingContainer = findViewById(R.id.shippingContainer)
        totalPriceLayout = findViewById(R.id.totalPriceLayout)

        containerLocation = findViewById(R.id.containerLocation)
        atauGunakan = findViewById(R.id.atauGunakan)
        lokasiSekarang = findViewById(R.id.lokasiSekarang)
        bukaPeta = findViewById(R.id.bukaPeta)

        checkoutButton.setOnClickListener {
            checkout()
        }

        lokasiSekarang.setOnClickListener {
            getCurrentLocation()
        }

        btn_back.setOnClickListener {
            onBackPressed()
        }

        bukaPeta.setOnClickListener {
            val intent = Intent(this, MapActivity::class.java)
            startActivityForResult(intent, MAP_REQUEST_CODE)
        }

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Cek permission terlebih dahulu
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        toggleGroup.addOnButtonCheckedListener { group, checkedId, isChecked ->
            if (checkedId == R.id.button_delivery && isChecked) {
                shppingAmount.text = "Rp 5000"
                shippingCost = 5000
                updateTotalAmount(cartItems)
                alamatEditText.visibility = View.VISIBLE
                shippingContainer.visibility = View.VISIBLE
                atauGunakan.visibility = View.VISIBLE
                containerLocation.visibility = View.VISIBLE
                val params = totalPriceLayout.layoutParams as LinearLayout.LayoutParams
                params.weight = 1.24f
                totalPriceLayout.layoutParams = params
            } else if (checkedId == R.id.button_pick_up && isChecked) {
                // Reset latitude, longitude, dan alamat seperti kondisi awal
                latitude = ""
                longitude = ""
                alamatEditText.text = null

                Log.d("Location", "Latitude: $latitude, Longitude: $longitude")
                shppingAmount.text = ""
                shippingCost = 0
                updateTotalAmount(cartItems)
                alamatEditText.visibility = View.GONE
                shippingContainer.visibility = View.GONE
                val params = totalPriceLayout.layoutParams as LinearLayout.LayoutParams
                params.weight = 1f
                totalPriceLayout.layoutParams = params
            }
        }


        cartRecyclerView.layoutManager = LinearLayoutManager(this)
        fetchCartItems()
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }


    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == MAP_REQUEST_CODE && resultCode == RESULT_OK) {
            // Dapatkan koordinat yang dikembalikan dari MapActivity
            val lat = data?.getDoubleExtra("latitude", 0.0) ?: 0.0
            val lng = data?.getDoubleExtra("longitude", 0.0) ?: 0.0

            // Set latitude dan longitude ke variabel global
            latitude = lat.toString()
            longitude = lng.toString()

            // Lakukan reverse geocoding di background thread
            CoroutineScope(Dispatchers.IO).launch {
                val geocoder = Geocoder(applicationContext, Locale.getDefault())
                try {
                    val addresses = geocoder.getFromLocation(lat, lng, 1)
                    if (addresses != null && addresses.isNotEmpty()) {
                        val address = addresses[0].getAddressLine(0) // Alamat lengkap

                        // Set text pada EditText di UI thread
                        withContext(Dispatchers.Main) {
                            alamatEditText.setText(address)
                            Log.d("Location", "Address: $address")
                            Toast.makeText(applicationContext, "Address: $address", Toast.LENGTH_SHORT).show()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("Location", "Reverse geocoding failed: ${e.message}")
                }
            }
        }
    }

    private fun checkout() {
        val address: String?
        val latitude: String?
        val longitude: String?

        // Jika checkedId == button_pick_up, set latitude, longitude, dan address menjadi null
        if (toggleGroup.checkedButtonId == R.id.button_pick_up) {
            address = null
            latitude = null
            longitude = null
        } else {
            // Jika button_delivery, validasi alamat dan titik lokasi
            if (alamatEditText.text.isNullOrBlank()) {
                Toast.makeText(this, "Silakan isi alamat pengiriman terlebih dahulu", Toast.LENGTH_SHORT).show()
                return
            }
            if (this.latitude.isBlank() || this.longitude.isBlank()) {
                Toast.makeText(this, "Silakan pilih titik lokasi terlebih dahulu", Toast.LENGTH_SHORT).show()
                return
            }
            address = alamatEditText.text.toString()
            latitude = this.latitude
            longitude = this.longitude
        }

        val token = TokenManager.getToken(this)
        val notes = notes.text.toString()

        val reqBody = ApiService.checkoutRequest(
            address = address,
            user_notes = notes,
            latitude = latitude,
            longitude = longitude
        )

        // Melakukan permintaan checkout hanya jika token tidak null
        if (token != null) {
            RetrofitInstance.api.checkout(token, reqBody)
                .enqueue(object : Callback<ApiService.checkoutResponse> {
                    override fun onResponse(
                        call: Call<ApiService.checkoutResponse>,
                        response: Response<ApiService.checkoutResponse>
                    ) {
                        if (response.isSuccessful) {
                            response.body()?.let {
                                val intent = Intent(this@CartPage, PaymentPage::class.java).apply {
                                    putExtra("total", it.total.toString())
                                    putExtra("bank_name", it.bank_name)
                                    putExtra("bank_account", it.bank_account)
                                    putExtra("id_history", it.iHistory.toString())
                                }
                                startActivity(intent)
                                finish()
                            }
                        } else {
                            Toast.makeText(
                                this@CartPage,
                                "Tidak ada menu di keranjang",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onFailure(call: Call<ApiService.checkoutResponse>, t: Throwable) {
                        Toast.makeText(this@CartPage, "Layanan tidak tersedia", Toast.LENGTH_SHORT).show()
                    }
                })
        }
    }


    private fun fetchCartItems() {
        val token = TokenManager.getToken(this)
        if (token != null) {
            RetrofitInstance.api.getCartItems(token)
                .enqueue(object : Callback<CartResponse> {
                    override fun onResponse(
                        call: Call<CartResponse>,
                        response: Response<CartResponse>
                    ) {
                        if (response.isSuccessful) {
                            response.body()?.let {
                                cartItems = it.values[0].cart_item.toMutableList()
                                if (cartItems.isEmpty()) {
                                    // Tampilkan TextView jika keranjang kosong
                                    emptyCartText.visibility = View.VISIBLE
                                    cartRecyclerView.visibility = View.GONE
                                    toggleGroup.visibility = View.GONE
                                } else {
                                    // Sembunyikan TextView jika keranjang tidak kosong
                                    emptyCartText.visibility = View.GONE
                                    cartRecyclerView.visibility = View.VISIBLE
                                    toggleGroup.visibility = View.VISIBLE
                                    cartRecyclerView.adapter = CartAdapter(cartItems, this@CartPage)
                                    updateTotalAmount(cartItems)
                                }
                            }
                        } else {
                            Toast.makeText(
                                this@CartPage,
                                "Failed to load cart items",
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }

                    override fun onFailure(call: Call<CartResponse>, t: Throwable) {
                        Toast.makeText(this@CartPage, "An error occurred", Toast.LENGTH_SHORT)
                            .show()
                    }
                })
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Izin diberikan
                getCurrentLocation()
            } else {
                // Izin ditolak
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }



    @SuppressLint("MissingPermission")
    private fun getCurrentLocation() {
        // Mengecek izin lokasi
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), LOCATION_PERMISSION_REQUEST_CODE)
            return
        }

        // Mencoba mendapatkan lokasi terakhir
        fusedLocationClient.lastLocation
            .addOnSuccessListener { location: Location? ->
                if (location != null) {
                    // Jika lokasi ditemukan
                    latitude = location.latitude.toString()
                    longitude = location.longitude.toString()

                    Log.d("Location", "Latitude: $latitude, Longitude: $longitude")

                    // Lakukan reverse geocoding di background thread
                    CoroutineScope(Dispatchers.IO).launch {
                        val geocoder = Geocoder(applicationContext, Locale.getDefault())
                        try {
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (addresses != null && addresses.isNotEmpty()) {
                                val address = addresses[0].getAddressLine(0) // Alamat lengkap

                                // Set text pada EditText di UI thread
                                withContext(Dispatchers.Main) {
                                    alamatEditText.setText(address)
                                    Log.d("Location", "Address: $address")
                                    Toast.makeText(applicationContext, "Lokasi mendapatkan lokasi", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Location", "Reverse geocoding failed: ${e.message}")
                        }
                    }
                } else {
                    // Jika lokasi tidak ditemukan, coba request update lokasi baru
                    requestNewLocationData()
                }
            }
            .addOnFailureListener { e ->
                Log.d("Location", "Gagal mendapatkan lokasi terakhir: ${e.message}")
                Toast.makeText(this, "Gagal mendapatkan lokasi", Toast.LENGTH_SHORT).show()
            }
    }


    @SuppressLint("MissingPermission")
    private fun requestNewLocationData() {
        val locationRequest = LocationRequest.create().apply {
            interval = 10000 // Interval permintaan lokasi (dalam milidetik)
            fastestInterval = 5000 // Interval tercepat untuk update lokasi
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }

        val locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                super.onLocationResult(locationResult)
                locationResult.locations.forEach { location ->
                    latitude = location.latitude.toString()
                    longitude = location.longitude.toString()

                    Log.d("Location", "Latitude: $latitude, Longitude: $longitude")

                    // Hentikan pembaruan lokasi setelah mendapatkan lokasi
                    fusedLocationClient.removeLocationUpdates(this)

                    // Lakukan reverse geocoding di background thread
                    CoroutineScope(Dispatchers.IO).launch {
                        val geocoder = Geocoder(applicationContext, Locale.getDefault())
                        try {
                            val addresses = geocoder.getFromLocation(location.latitude, location.longitude, 1)
                            if (addresses != null && addresses.isNotEmpty()) {
                                val address = addresses[0].getAddressLine(0) // Alamat lengkap

                                // Set text pada EditText di UI thread
                                withContext(Dispatchers.Main) {
                                    alamatEditText.setText(address)
                                    Toast.makeText(applicationContext, "Lokasi mendapatkan lokasi", Toast.LENGTH_SHORT).show()
                                    Toast.makeText(applicationContext, "Address: $address", Toast.LENGTH_SHORT).show()
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Location", "Reverse geocoding failed: ${e.message}")
                            Toast.makeText(applicationContext, "Gagal lokasi mendapatkan lokasi", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }

        // Mulai mendapatkan pembaruan lokasi
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
    }

    override fun onAmountChanged() {
        updateTotalAmount(cartItems)
    }

    private fun updateTotalAmount(cartItems: List<CartItem>) {
        var total = 0
        for (item in cartItems) {
            total += item.amount * item.price + shippingCost
        }
        totalAmount.text = "Rp $total"
        if (cartItems.isEmpty()) {
            // Tampilkan TextView jika keranjang kosong
            emptyCartText.visibility = View.VISIBLE
            cartRecyclerView.visibility = View.GONE
            toggleGroup.visibility = View.GONE
        } else {
            // Sembunyikan TextView jika keranjang tidak kosong
            emptyCartText.visibility = View.GONE
            cartRecyclerView.visibility = View.VISIBLE
            toggleGroup.visibility = View.VISIBLE
        }
    }
}
