package com.mediacodec.h264

import android.Manifest
import android.content.pm.PackageManager
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.core.app.ActivityCompat
import kotlinx.android.synthetic.main.activity_main.*

class MainActivity : AppCompatActivity() {
    val LOG_TAG = "HCamera2Manager2"
    private lateinit var mMediaRecordBtn: Button
    var bIsRcording = false
    private lateinit var mRecordPlayBtn: Button
    var bIsPlaying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //setContentView(R.layout.activity_main)
        setContentView(R.layout.activity_main)
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            Log.d(LOG_TAG,"check permission")
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA),1)
        }else{
            Log.d(LOG_TAG,"init camera")
            initCamera()
        }

        mMediaRecordBtn = findViewById(R.id.button_media_record)
        mMediaRecordBtn.setOnClickListener(mButtonListener)
        mRecordPlayBtn = findViewById(R.id.button_record_play)
        mRecordPlayBtn.setOnClickListener(mButtonListener)

    }

    private val mButtonListener = View.OnClickListener { view ->
        when (view.id) {
            R.id.button_media_record -> {
                Log.d(LOG_TAG,"Click : button_media_record")
                if(bIsRcording) {
                    HCamera2Manager.getInstance().stopRecord()
                    mMediaRecordBtn.text = "录制视频"
                    bIsRcording = false
                } else {
                    HCamera2Manager.getInstance().startRecord()
                    mMediaRecordBtn.text = "停止录制"
                    bIsRcording = true
                }
                mRecordPlayBtn.isEnabled = !bIsRcording
            }
            R.id.button_record_play -> {
                Log.d(LOG_TAG,"Click : button_record_play");
                if(bIsPlaying) {
                    HCamera2Manager.getInstance().stopPlay()
                    mRecordPlayBtn.text = "录音播放"
                    bIsPlaying = false
                } else {
                    HCamera2Manager.getInstance().startPlay()
                    mRecordPlayBtn.text = "停止录播"
                    bIsPlaying = true
                }
                mMediaRecordBtn.isEnabled = !bIsPlaying
            }
        }
    }

    private fun initCamera(){
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
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