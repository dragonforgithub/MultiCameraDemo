package com.mediacodec.h264

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    val LOG_TAG = "AV-BOX"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        setContentView(R.layout.activity_main)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_TAG,"check permission");
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),1)
        }else{
            Log.d(LOG_TAG,"init camera");
            initCamera()
        }
    }

    private fun initCamera(){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            HCamera2Manager.getInstance().initCamera(camera1_surfaceView,applicationContext)//top surface view
            HCamera2Manager2.getInstance().initCamera(camera2_surfaceView,applicationContext)//top surface view
            HCamera2Manager3.getInstance().initCamera(camera3_surfaceView,applicationContext)//top surface view
            HCamera2Manager4.getInstance().initCamera(camera4_surfaceView,applicationContext)//top surface view
            HCamera2Manager5.getInstance().initCamera(camera5_surfaceView,applicationContext)//top surface view
            HCamera2Manager6.getInstance().initCamera(camera6_surfaceView,applicationContext)//top surface view
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == 1 && grantResults[0]==PackageManager.PERMISSION_DENIED){
            initCamera()
        }
    }
}