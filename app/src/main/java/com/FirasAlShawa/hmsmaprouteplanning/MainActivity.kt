package com.FirasAlShawa.hmsmaprouteplanning

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.huawei.hms.maps.HuaweiMap
import com.huawei.hms.maps.OnMapReadyCallback
import com.huawei.hms.maps.model.*
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import org.json.JSONObject
import java.io.IOException


class MainActivity : AppCompatActivity() , OnMapReadyCallback  ,
    View.OnClickListener {

    //Static Variables
    val Tag = "MainActivity"
    val MAPVIEW_BUNDLE_KEY = "mapViewBundleKey";

    //Map Variabeles
    lateinit var map : HuaweiMap
    val markers = mutableMapOf<String,Marker>()
    var mapBundle : Bundle? = null
    var polyline: Polyline? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        if(savedInstanceState != null){
            mapBundle = savedInstanceState.getBundle(MAPVIEW_BUNDLE_KEY)!!
        }
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)
        fromBtn.setOnClickListener(this)
        toBtn.setOnClickListener(this)
    }

    override fun onMapReady(p0: HuaweiMap?) {
        map = p0!!
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        map.isMyLocationEnabled = true
    }

    override fun onClick(v: View?) {
        var latlon = LatLng(map.cameraPosition.target.latitude,map.cameraPosition.target.longitude)

        when(v?.id){
            fromBtn.id ->{
                Log.i("Firas Marker" ,"From")
                if(markers.keys.contains("from"))
                    markers["from"]?.remove()

                if(polyline != null){
                    polyline?.remove()
                }

                val marker  = map.addMarker(MarkerOptions().position(latlon).title("From").icon(BitmapDescriptorFactory.fromResource(R.drawable.iconfrom)))
                markers["from"] = marker
                toBtn.setClickable(true);

                if(markers.containsKey("to")){
                    GetRoute();
                }
                Log.i(Tag,"${latlon.latitude},${latlon.longitude}");
            }
            toBtn.id -> {

                if(markers.keys.contains("to"))
                    markers["to"]?.remove()

                if(polyline != null){
                    polyline?.remove()
                }

                val marker =  map.addMarker(MarkerOptions().position(latlon).title("From").icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_to)))
                markers["to"] = marker

                Log.i(Tag,"${latlon.latitude},${latlon.longitude}");

                GetRoute();
            }
        }
    }


    fun GetRoute(){

        if(markers.containsKey("from") && markers.containsKey("to")) {

            Thread {
                val okHttpClient = OkHttpClient()
                val url =
                    "https://mapapi.cloud.huawei.com/mapApi/v1/routeService/driving?key=${getString(R.string.api_key)}";
                url

                var root = JSONObject().putOpt(
                    "origin", JSONObject()
                        .put("lat", markers["from"]?.position?.latitude)
                        .put("lng", markers["from"]?.position?.longitude)
                )
                    .putOpt(
                        "destination", JSONObject()
                            .put("lat", markers["to"]?.position?.latitude)
                            .put("lng", markers["to"]?.position?.longitude)
                    )


                val requestBody: RequestBody =
                    RequestBody.create("application/json".toMediaTypeOrNull(), root.toString());

                val request: Request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()


                okHttpClient.newCall(request).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace();
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val jsonObject = JSONObject(response.body?.string())

                        val routes = jsonObject.optJSONArray("routes")


                        if (null == routes || routes.length() == 0) {
                            runOnUiThread {
                                Toast.makeText(
                                    applicationContext,
                                    "Try again please",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                            return
                        }

                        val route = routes.getJSONObject(0)

                        val paths = route.optJSONArray("paths")

                        distanceText.text ="distance : ${paths.getJSONObject(0).optString("distanceText").toString()}"

                        var pathsList = arrayListOf<MutableList<LatLng>>()

                        for (i in 0 until paths.length()) {

                            val stepPathList: MutableList<LatLng> = ArrayList()
                            val path = paths.getJSONObject(i)
                            val steps = path.optJSONArray("steps")

                            for (j in 0 until steps.length()) {

                                val step = steps.getJSONObject(j)
                                val pipelines = step.optJSONArray("polyline")

                                for (k in 0 until pipelines.length()) {
                                    if (j > 0 && k == 0) {
                                        continue
                                    }

                                    val line = pipelines.getJSONObject(k)
                                    val lat = line.optDouble("lat")
                                    val lng = line.optDouble("lng")
                                    val latLng = LatLng(lat, lng)

                                    stepPathList.add(latLng)
                                }
                            }
                            pathsList.add(i, stepPathList)
                        }
                        drawMyRoute(pathsList);
                    }

                })

            }.start()
        }else{
            Toast.makeText(applicationContext,"set your origin point first!",Toast.LENGTH_SHORT).show();
        }
    }

    fun drawMyRoute(arg_paths: List<List<LatLng?>>?) {
        if (arg_paths == null || arg_paths.isEmpty() && arg_paths[0].isEmpty()) {
            Toast.makeText(applicationContext,"Sorry Try Again",Toast.LENGTH_SHORT).show();
            return
        }
        for (i in arg_paths.indices) {

            val insidePath = arg_paths[i]

            val options = PolylineOptions()
                .color(Color.BLUE).width(2f)

            for (latLng in insidePath) {
                options.add(latLng)
            }
            polyline = map.addPolyline(options)
        }
    }

}
