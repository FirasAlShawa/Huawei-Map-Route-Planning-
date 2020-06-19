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
        OriginBtn.setOnClickListener(this)
        DestinationBtn.setOnClickListener(this)
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
            OriginBtn.id ->{

            //check if we have Origin maker already pinned if true remove the pin from the map
               if(markers.keys.contains("Origin"))
                    markers["Origin"]?.remove()

            //check if we have already planned route if ture remove it
                if(polyline != null){
                    polyline?.remove()
                }

            //make new MarkerOptions and add the marker to the map
                val marker  = map.addMarker(MarkerOptions().position(latlon).title("Origin").icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_origin)))
                
            //Add the marker as Origin Marker
                markers["Origin"] = marker

            //Enable the Destination button
                DestinationBtn.setClickable(true);

            //if we have Destination already Get the new Route
                if(markers.containsKey("Destination")){
                    GetRoute();
                }
            }

            DestinationBtn.id -> {
            //check if we have Destination maker already pinned if true remove the pin from the map
                if(markers.keys.contains("Destination"))
                    markers["Destination"]?.remove()

            //check if we have already planned route if ture remove it
                if(polyline != null){
                    polyline?.remove()
                }

            //make new MarkerOptions and add the marker to the map
                val marker =  map.addMarker(MarkerOptions().position(latlon).title("Origin").icon(BitmapDescriptorFactory.fromResource(R.drawable.icon_destination)))
        
            //Add the marker as Destination Marker
                markers["Destination"] = marker
            
            //Get the Route
                GetRoute();
            }
        }
    }


    fun GetRoute(){

        //Check if we have Origin and Destination points if true we can make the request if false show the user some Alert
        if(markers.containsKey("Origin") && markers.containsKey("Destination")) {

            Thread {

                val okHttpClient = OkHttpClient()

                //Huawei driving Direction API URL
                val url =
                    "https://mapapi.cloud.huawei.com/mapApi/v1/routeService/driving?key=${getString(R.string.api_key)}";
                
                //Build you Request JSON Body as we did in postman
                var RequestJsonBody = JSONObject().putOpt(
                    "origin", JSONObject()
                        .put("lat", markers["Origin"]?.position?.latitude)
                        .put("lng", markers["Origin"]?.position?.longitude)
                    )
                    .putOpt(
                        "destination", JSONObject()
                            .put("lat", markers["Destination"]?.position?.latitude)
                            .put("lng", markers["Destination"]?.position?.longitude)
                    )

                //Set the request body 
                val requestBody: RequestBody =
                    RequestBody.create("application/json".toMediaTypeOrNull(), RequestJsonBody.toString());

                //Make new Request and add the headers as we did in postman and attach the requestBody
                val request: Request = Request.Builder()
                    .url(url)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .post(requestBody)
                    .build()

                //Call the Request and handle the onFailure and onResponse 
                okHttpClient.newCall(request).enqueue(object : Callback {

                    override fun onFailure(call: Call, e: IOException) {
                        e.printStackTrace();
                    }

                    override fun onResponse(call: Call, response: Response) {

                    //get the response body and convert it into string
                        val requestJsonObject = JSONObject(response.body?.string())
                    //get the "routes" JSON Array
                        val routes = requestJsonObject.optJSONArray("routes")

                    //if routes array is empty alert the uesrand the quit the function
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
                        
                        //get the index 0 JSON object of the "routes" JOSN Array
                        val route = routes.getJSONObject(0)

                        //get the "paths" JOSN Array
                        val paths = route.optJSONArray("paths")

                        //Read the "distanceText" value of the index 0 of "paths" JSON Array Oject
                        distanceText.text ="distance : ${paths.getJSONObject(0).optString("distanceText").toString()}"

                        //new List That will contain our directions
                        var pathsList = arrayListOf<MutableList<LatLng>>()

                        //iteriate on the paths array
                        for (i in 0 until paths.length()) {

                            //list that will contain the each path steps                            
                            val stepPathList: MutableList<LatLng> = ArrayList()
                            
                            //get the path Object
                            val path = paths.getJSONObject(i)

                            //Read the Steps JSON Array
                            val steps = path.optJSONArray("steps")

                            //iterate on the path's steps
                            for (j in 0 until steps.length()) {

                                val step = steps.getJSONObject(j)
                                //Read the "polyline" JSON array 
                                val polylines = step.optJSONArray("polyline")
                                
                                //iterate on the Steps's "polyline"
                                for (k in 0 until polylines.length()) {
                                    
                                    //the first polyline will be the last polyline so we skip it
                                    if (j > 0 && k == 0) {
                                        continue
                                    }

                                    val line = polylines.getJSONObject(k)
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

    fun drawMyRoute(DirectionPaths: List<List<LatLng?>>?) {

        //check if we do not have any paths
        if (DirectionPaths == null || DirectionPaths.isEmpty() && DirectionPaths[0].isEmpty()) {
            Toast.makeText(applicationContext,"Sorry Try Again",Toast.LENGTH_SHORT).show();
            return
        }

        //iterate on each paths direction polylines
        for (i in DirectionPaths.indices) {
            
            //get the direction 
            val insidePath = DirectionPaths[i]
            
            //setup the color and the width of ployline
            val options = PolylineOptions()
                .color(Color.BLUE).width(2f)
            
            //iterate on the directions points
            for (latLng in insidePath) {
                options.add(latLng)
            }

            //Add the polylines on the Map as ploylines 
            polyline = map.addPolyline(options)
        }
    }

}
