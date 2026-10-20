package org.example.docuscanfree

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/** Saves a camera JPEG before any crop or enhancement. */
class RawCaptureActivity : AppCompatActivity() {
    private lateinit var previewView: PreviewView
    private lateinit var captureButton: Button
    private lateinit var status: TextView
    private var imageCapture: ImageCapture? = null
    private lateinit var cameraExecutor: ExecutorService
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) startCamera() else { Toast.makeText(this, "برای گرفتن عکس خام، مجوز دوربین لازم است.", Toast.LENGTH_LONG).show(); setResult(Activity.RESULT_CANCELED); finish() }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState); cameraExecutor = Executors.newSingleThreadExecutor()
        window.statusBarColor = android.graphics.Color.rgb(7, 27, 53)
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(12,12,12,12); setBackgroundColor(0xFF071B35.toInt()) }
        root.addView(TextView(this).apply { text="ثبت تصویر خام دوربین"; textSize=20f; gravity=Gravity.CENTER; setTextColor(-1); setPadding(8,8,8,12) })
        previewView = PreviewView(this).apply { scaleType=PreviewView.ScaleType.FIT_CENTER }; root.addView(previewView,LinearLayout.LayoutParams(-1,0,1f))
        status = TextView(this).apply { text="تصویر خام بدون برش ذخیره می‌شود."; textSize=14f; gravity=Gravity.CENTER; setTextColor(0xFFD9F2FF.toInt()); setPadding(8,10,8,10) }; root.addView(status)
        captureButton = Button(this).apply { text="ثبت عکس و افزودن به پروژه"; isAllCaps=false; setOnClickListener { captureRaw() } }; root.addView(captureButton)
        root.addView(Button(this).apply { text="بازگشت"; isAllCaps=false; setOnClickListener { setResult(Activity.RESULT_CANCELED); finish() } })
        setContentView(root)
        if(ContextCompat.checkSelfPermission(this,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED) startCamera() else permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    private fun startCamera() {
        val future=ProcessCameraProvider.getInstance(this)
        future.addListener({ try { val provider=future.get(); val preview=Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }; imageCapture=ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY).build(); provider.unbindAll(); provider.bindToLifecycle(this,CameraSelector.DEFAULT_BACK_CAMERA,preview,imageCapture); status.text="دوربین آماده است؛ تصویر خام قبل از ویرایش ذخیره می‌شود." } catch(e:Exception) { status.text="راه‌اندازی دوربین ناموفق بود: ${e.localizedMessage}" } },ContextCompat.getMainExecutor(this))
    }
    private fun captureRaw() {
        val capture=imageCapture ?: run { status.text="دوربین هنوز آماده نیست."; return }; captureButton.isEnabled=false
        val name="SHAB_RAW_${SimpleDateFormat("yyyyMMdd_HHmmss_SSS",Locale.US).format(System.currentTimeMillis())}.jpg"
        val dir=File(cacheDir,"raw-captures").apply { mkdirs() }; val file=File(dir,name); val options=ImageCapture.OutputFileOptions.Builder(file).build()
        status.text="در حال ذخیره عکس..."
        capture.takePicture(options,cameraExecutor,object:ImageCapture.OnImageSavedCallback {
            override fun onError(exception:ImageCaptureException) { runOnUiThread { captureButton.isEnabled=true; status.text="ذخیره عکس ناموفق بود: ${exception.localizedMessage}" } }
            override fun onImageSaved(output:ImageCapture.OutputFileResults) { runOnUiThread { if(!file.exists() || file.length()<1024) { captureButton.isEnabled=true; status.text="فایل عکس معتبر ایجاد نشد."; return@runOnUiThread }; setResult(Activity.RESULT_OK,Intent().putExtra(EXTRA_RAW_PATH,file.absolutePath).putExtra(EXTRA_RAW_NAME,name)); finish() } }
        })
    }
    override fun onDestroy() { super.onDestroy(); if(::cameraExecutor.isInitialized) cameraExecutor.shutdown() }
    companion object { const val EXTRA_RAW_PATH="org.example.docuscanfree.RAW_PATH"; const val EXTRA_RAW_NAME="org.example.docuscanfree.RAW_NAME" }
}
