package com.qiniu.qplayer2.ui.page.longvideo

import android.Manifest
import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.util.Log

import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.qiniu.qplayer2.R
import com.qiniu.qplayer2.repository.setting.PlayerSettingRespostory
import com.qiniu.qplayer2.ui.page.longvideo.service.buffering.PlayerBufferingServiceOwner
import com.qiniu.qplayer2.ui.page.longvideo.service.controlpanelcontainervisible.PlayerControlPanelContainerVisibleServiceOwner
import com.qiniu.qplayer2.ui.page.longvideo.service.network.PlayerNetworkServiceOwner
import com.qiniu.qplayer2.ui.page.longvideo.service.panorama.PlayerPanoramaTouchSeriviceOwner
import com.qiniu.qplayer2.ui.page.longvideo.service.shoot.PlayerShootVideoServiceOwner
import com.qiniu.qplayer2.ui.page.longvideo.service.subtitle.PlayerSubtitleServiceOwner
import com.qiniu.qplayer2.ui.page.longvideo.service.toast.PlayerToastServiceOwner
import com.qiniu.qplayer2.ui.page.longvideo.service.volume.PlayerVolumeServiceOwner
import com.qiniu.qplayer2ext.commonplayer.CommonPlayer
import com.qiniu.qplayer2ext.commonplayer.CommonPlayerConfig
import com.qiniu.qplayer2ext.commonplayer.data.CommonPlayerDataSource
import com.qiniu.qplayer2ext.commonplayer.data.DisplayOrientation
import com.qiniu.qplayer2ext.commonplayer.layer.control.ControlPanelConfig
import com.qiniu.qplayer2ext.commonplayer.layer.control.ControlPanelConfigElement
import com.qiniu.qplayer2ext.commonplayer.screen.ScreenType
import com.uuzuche.lib_zxing.activity.CaptureActivity
import com.uuzuche.lib_zxing.activity.CodeUtils
import org.json.JSONObject
import org.json.JSONArray
import org.json.JSONException
import java.io.File
import java.io.FileOutputStream
import java.io.FileWriter


class LongVideoActivity : AppCompatActivity() {

    private lateinit var mCommonPlayer: CommonPlayer<Any,
            LongLogicProvider, LongPlayableParams, LongVideoParams>

    private lateinit var mWakeLock: PowerManager.WakeLock

    private lateinit var mPlayerDataSource: CommonPlayerDataSource<LongPlayableParams, LongVideoParams>

    private val REQUEST_CODE_SCAN = 1
    private val CAMERA_PERMISSION_REQUEST_CODE = 100
    private var selectedValue = 0
    private val PERMISSION_REQUEST_CODE = 123
    private var outnewEntry = JSONObject()

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
        setContentView(R.layout.activity_long_video)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        acquireWakeLock();
        initCommonPlayer()
    }

    override fun onStart() {
        super.onStart()
    }
    override fun onDestroy() {
        mCommonPlayer.release()
        releaseWakeLock()
        super.onDestroy()
    }
    @SuppressLint("InvalidWakeLockTag")
    private fun acquireWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "keep_player_activate")
        mWakeLock.setReferenceCounted(false)
    }

    private fun releaseWakeLock() {
        mWakeLock.release()
    }

    private fun initCommonPlayer() {
        mPlayerDataSource = LongPlayerDataSourceFactory.create2()
        val config = CommonPlayerConfig.Builder<Any,
                LongLogicProvider, LongPlayableParams, LongVideoParams>()
            .addControlPanel(
                ControlPanelConfig(
                    LongControlPanelType.Normal.type,
                    arrayListOf(
                        ControlPanelConfigElement(
                            R.layout.control_panel_halfscreen_landscape_normal,
                            arrayListOf(ScreenType.HALF_SCREEN),
                            DisplayOrientation.LANDSCAPE),
                        ControlPanelConfigElement(
                            R.layout.control_panel_fullscreen_landscape_normal,
                            arrayListOf(ScreenType.FULL_SCREEN, ScreenType.REVERSE_FULL_SCREEN),
                            DisplayOrientation.LANDSCAPE)
                    )
                )
            )
            .addEnviroment(
                LongEnviromentType.LONG.type,
                LongPlayerEnviroment()
            )
            .setCommonPlayerScreenChangedListener(
                LongCommonPlayerScreenChangedListener(
                    this,
                    findViewById(R.id.video_container_FL)
                )
            )
            .setLogicProvider(LongLogicProvider(this))
            .setPlayerDataSource(mPlayerDataSource)
            .setContext(this)
            .addServiceOwner(PlayerControlPanelContainerVisibleServiceOwner())
            .addServiceOwner(PlayerToastServiceOwner())
            .addServiceOwner(PlayerBufferingServiceOwner())
            .addServiceOwner(PlayerNetworkServiceOwner())
            .addServiceOwner(PlayerPanoramaTouchSeriviceOwner())
            .addServiceOwner(PlayerShootVideoServiceOwner())
            .addServiceOwner(PlayerVolumeServiceOwner())
            .addServiceOwner(PlayerSubtitleServiceOwner())
            .setRootUIContanier(this, findViewById(R.id.video_container_FL))
            .enableControlPanel()
            .enableFunctionWidget()
            .enableGesture()
            .enableToast()
            .enableScreenRender(CommonPlayerConfig.ScreenRenderType.SURFACE_VIEW)
            .setDecodeType(PlayerSettingRespostory.decoderType)
            .setSeekMode(PlayerSettingRespostory.seekMode)
            .setBlindType(PlayerSettingRespostory.blindType)
            .setStartAction(PlayerSettingRespostory.startAction)
            .setSpeed(PlayerSettingRespostory.playSpeed)
            .setRenderRatio(PlayerSettingRespostory.ratioType)
            .setSubtitleEnable(PlayerSettingRespostory.subtitleEnable)
            .setSEIEnable(PlayerSettingRespostory.seiEnable)
//            .setSubtitleEnable(PlayerSettingRespostory.subtitleEnable)
            .build()

        mCommonPlayer = CommonPlayer(config)
        mPlayerDataSource.getVideoParamsList().getOrNull(0)?.also {
            mCommonPlayer.playerVideoSwitcher.switchVideo(it.id)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            } else {
                // 用户拒绝了摄像头权限
                Toast.makeText(this, "we need the permission to get suspect", Toast.LENGTH_LONG)
                    .show()
            }
        }
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    Log.e("曾召侠", "权限申请成功")
                    readAndWriteJsonFile(outnewEntry)
                } else {
                    Log.e("曾召侠", "权限申请失败")
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun onClickScanQRCode(view: View) {
        //检查并请求摄像头权限
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // 检查摄像头权限是否被授予
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.CAMERA),
                CAMERA_PERMISSION_REQUEST_CODE
            )
        } else {
            startActivityForResult(Intent(this, CaptureActivity::class.java), REQUEST_CODE_SCAN)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCAN && resultCode == CaptureActivity.RESULT_OK) {
            // 处理扫描结果
            if (data != null) {
                var extras = data?.extras;
                if (extras != null) {
                    var scanResult = extras.getString(CodeUtils.RESULT_STRING);
                    if (scanResult != null) {
                        Log.i("曾召侠", "扫描结果scanResult${scanResult}")
                    } else {
                        Log.i("曾召侠", "扫描结果为空")
                    }
                    showConfigDialog(scanResult)
                }
            }
        }
    }

    private fun showConfigDialog(scanResult: String?) {
        var dialog = Dialog(this)
        dialog.setContentView(R.layout.dialog_config)
        dialog.setTitle("配置")
        dialog.setCanceledOnTouchOutside(false)
        val configUrl = dialog.findViewById<EditText>(R.id.configUrl)
        val resolutionButton = dialog.findViewById<EditText>(R.id.resolution)
        val UrlName = dialog.findViewById<EditText>(R.id.name)
        val cancelButton = dialog.findViewById<Button>(R.id.cancelButton)
        val submitButton = dialog.findViewById<Button>(R.id.submitButton)
        val radioGroup = dialog.findViewById<RadioGroup>(R.id.radioGroup)
        configUrl.setText(scanResult)
        radioGroup.setOnCheckedChangeListener { _, checkedId ->
            selectedValue = when (checkedId) {
                R.id.VodButton -> 0
                R.id.LiveButton -> 1
                else -> -1 // 默认值或其他情况
            }
        }

        submitButton.setOnClickListener {
            //获取值
            var inputText = resolutionButton.text.toString().toIntOrNull()?:720

            //val textValue = UrlName?.text
            //val inputName = textValue ?: "test"
            //var inputName = UrlName.text ?:"test"
            val inputName = if(UrlName.text.isNullOrBlank()){
                "test"
            }else{
                UrlName.text
            }

            //创建json数组
            val NewjsonArray = JSONArray()
            //创建json对象
            val newEntry = JSONObject()
            newEntry.put("userType", "")
            newEntry.put("urlType", 0)
            newEntry.put("url", scanResult)
            newEntry.put("quality", inputText)
            newEntry.put("isSelected", 1)
            newEntry.put("backupUrl", "")
            newEntry.put("referer", "")
            NewjsonArray.put(newEntry)

            outnewEntry.put("isLive", selectedValue)
            outnewEntry.put("name", inputName)
            outnewEntry.put("streamElements", NewjsonArray)

            readAndWriteJsonFile(outnewEntry)
            dialog.dismiss()
        }
            cancelButton.setOnClickListener {
                dialog.dismiss() //取消对话框
            }
            dialog.show()

    }
    fun readAndWriteJsonFile(outnewEntry: JSONObject) {
        // 检查存储权限
        if (ContextCompat.checkSelfPermission(baseContext, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            == PackageManager.PERMISSION_GRANTED
        ) {
            Log.e("曾召侠", "存储权限已授予,开始写文件")
            val inputS = baseContext.resources.openRawResource(R.raw.urls)
            //val outputfile = File(baseContext.getExternalFilesDir(null),"urls01.json")
            //val outputStream = FileOutputStream(outputfile)
            // 写入文件
            var folderPath = "/data/data/com.qiniu.qplayer2/files/url"
            var fileName = "urls.json"
            val filePath = "$folderPath/$fileName"
            val folder = File(folderPath)
            //val outputFile = File(filePath)
            //val outputStream = FileOutputStream(filePath)
            if (!folder.exists()) {
                val created = folder.mkdirs()
                if (created) {
                    Log.i("曾召侠", "Folder not exist.and create")
                    println("Folder created successfully.")
                } else {
                    println("Failed to create folder.")
                }

//                val buffer = ByteArray(1024)
//                var read: Int
//                while (inputS.read(buffer).also { read = it } != -1) {
//                    outputStream.write(buffer, 0, read)
//                }
            } else {
                Log.i("曾召侠", "Folder already exists")
                println("Folder already exists.")
            }
            val outputFile = File(filePath)
            val outputStream = FileOutputStream(outputFile)
            //var inputStream: FileInputStream? = null
            Log.i("曾召侠", "开始写")


            try {
                //inputStream = FileInputStream(filePath)
                val buffer = ByteArray(1024)
                var read: Int
                while (inputS.read(buffer).also { read = it } != -1) {
                    outputStream.write(buffer, 0, read)
                }
                println("File copied successfully.")
                //打印copy后的文件内容
                //val copiedContent = outputStream.toString()
                //println("Copied file content: $copiedContent")

                //打印此时文件中的内容
                val file = File(filePath)
                val fileContent = file.readText()
                println(fileContent)

            } catch (e: Exception) {
                println("Error copying file: ${e.message}")
            } finally {
                outputStream.flush()
                inputS.close()
                outputStream.close()
            }
            // 读取现有的
            val fileNew = File(filePath)
            val jsonString = fileNew.readText()

            try {

                val jsonArrayNew = JSONArray(jsonString)
                jsonArrayNew.put(outnewEntry)


                val jsonArray_New = jsonArrayNew.toString()
                //val jsonArray_New1 = "$jsonArray_New\n$jsonString"

                val fileNew = File(filePath)
                val fileWriter = FileWriter(fileNew)
                fileWriter.write(jsonArray_New)

                //读取
                val fileContent = fileNew.readText()
                println(fileContent)


                fileNew.writeText(jsonArray_New)
                //FileWriter(filePath).use {it.write(jsonArray_New1)}
            }catch (e:JSONException){
                Log.e("曾召侠", "JSON内容解析错误: ${e.message}")
            }
        }else{
            Log.e("曾召侠", "存储权限未授予，去获取权限")
             ActivityCompat.requestPermissions(
            this,arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), PERMISSION_REQUEST_CODE)
        }
    }
}





