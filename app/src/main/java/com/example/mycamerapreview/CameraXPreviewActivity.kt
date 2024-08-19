package com.example.mycamerapreview

import android.content.ContentValues
import android.icu.text.SimpleDateFormat
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log

import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.core.content.ContentProviderCompat.requireContext
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenCreated
import com.example.mycamerapreview.databinding.ActivityCameraPreviewBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.nio.ByteBuffer
import java.util.Locale
import java.util.concurrent.ExecutorService

/** Helper type alias used for analysis use case callbacks */
typealias LumaListener = (luma: Double) -> Unit
class CameraXPreviewActivity : AppCompatActivity(){
    companion object{
        const val DEFAULT_QUALITY_IDX = 0
        val TAG = "CameraPreviewActivity"
        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
    private lateinit var binding: ActivityCameraPreviewBinding

    private var imageCapture: ImageCapture? = null

    private lateinit var outputDirectory: File
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var videoCapture: VideoCapture<Recorder>
    private var cameraIndex = 0
    private var qualityIndex = DEFAULT_QUALITY_IDX
    private var audioEnabled = true
    private var currentRecording: Recording? = null

    private val mainThreadExecutor by lazy { ContextCompat.getMainExecutor(application) }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityCameraPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        val windowInsetsController =
            WindowCompat.getInsetsController(window, window.decorView)
        // Configure the behavior of the hidden system bars.
        windowInsetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, windowInsets ->
            // You can hide the caption bar even when the other system bars are visible.
            // To account for this, explicitly check the visibility of navigationBars()
            // and statusBars() rather than checking the visibility of systemBars().
            windowInsetsController.hide(WindowInsetsCompat.Type.systemBars())
            ViewCompat.onApplyWindowInsets(view, windowInsets)
        }

        outputDirectory = getOutputDirectory()
        Log.d(TAG,"outputDirectory="+outputDirectory.absolutePath)
        cameraExecutor = java.util.concurrent.Executors.newSingleThreadExecutor()


        binding.takePhoto.setOnClickListener {
            takePhoto()
        }

        binding.recorderVideo.setOnClickListener {
            if(currentRecording==null) {
                startRecording()
            }else{
                stopRecording();
            }
        }

    }
    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull()?.let {
            File(it, resources.getString(R.string.app_name)).apply { mkdirs() } }
        return if (mediaDir != null && mediaDir.exists())
            mediaDir else filesDir
    }
    override fun onResume() {
        super.onResume()

    }

    override fun onStart() {
        super.onStart()
        Permission.checkPermission(this)
        startCamera()
    }
    private fun stopRecording(){
        val recording = currentRecording
        if (recording != null) {
            recording.stop()
            currentRecording = null
        }
    }

    private fun startRecording() {
        // create MediaStoreOutputOptions for our recorder: resulting our recording!
        val name = "CameraX-recording-" +
                java.text.SimpleDateFormat(FILENAME_FORMAT, Locale.US)
                    .format(System.currentTimeMillis()) + ".mp4"
        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            application.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        // configure Recorder and Start recording to the mediaStoreOutput.
        currentRecording = videoCapture.output
            .prepareRecording(application, mediaStoreOutput)
            .apply { if (audioEnabled) withAudioEnabled() }
            .start(mainThreadExecutor, captureListener)

        Log.i(TAG, "Recording started")
    }

    private val captureListener = Consumer<VideoRecordEvent> { event ->



    }


    private fun startCamera() {
        /*
        创建 ProcessCameraProvider 的实例。
        此实例用于将相机的生命周期绑定到生命周期所有者。
        由于 CameraX具有生命周期感知能力，所以这样可以省去打开和关闭相机的任务。
         */
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
/*
向 cameraProviderFuture 中添加监听器。
添加 Runnable 作为参数。我们将稍后为其填入数值。
添加 ContextCompat.getMainExecutor() 作为第二个参数。这将返回在主线程上运行的 Executor。
 */
        cameraProviderFuture.addListener(
            {
                /*
           添加 ProcessCameraProvider。此类用于将相机的生命周期绑定到应用进程内的 LifecycleOwner
            */
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // Preview
                /*
                初始化您的 Preview 对象，在该对象上调用 build，从取景器中获取表面提供程序，然后在预览中进行设置
                 */
            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.preview.surfaceProvider)
                }
                imageCapture = ImageCapture.Builder()
                    .build()


                //   创建 CameraSelector 对象并选择 DEFAULT_FRONT_CAMERA。
                // Select back camera as a default
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                // create the user required QualitySelector (video resolution): we know this is
                // supported, a valid qualitySelector will be created.
                val quality = Quality.SD
                val qualitySelector = QualitySelector.from(quality)

                val recorder = Recorder.Builder()
                    .setQualitySelector(qualitySelector)
                    .build()
                videoCapture = VideoCapture.withOutput(recorder)
                val imageAnalyzer = ImageAnalysis.Builder()
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, LuminosityAnalyzer { luma ->
                            Log.d(TAG, "Average luminosity: $luma")
                        })
                    }




            try {
                /*
                在该块中，确保任何内容都未绑定到您的 cameraProvider，然后将您的 cameraSelector 和预览对象绑定到 cameraProvider。
                 */
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera
                cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview,videoCapture, imageCapture,imageAnalyzer)

            } catch(exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun takePhoto() {
        // Get a stable reference of the modifiable image capture use case
        /*
        获取对 ImageCapture 用例的引用。如果用例为 null，则退出函数
         */
        val imageCapture = imageCapture ?: return

        // Create time-stamped output file to hold the image
        /*
        创建一个容纳图像的文件。添加时间戳，以避免文件名重复。
         */
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg")

        // Create output options object which contains file + metadata
        /*
        创建 OutputFileOptions 对象。您可以在此对象中指定有关输出方式的设置。如果您希望将输出内容保存在刚创建的文件中，则添加您的 photoFile。
         */
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Set up image capture listener, which is triggered after photo has
        // been taken
        /*
        对 imageCapture 对象调用 takePicture()。传入执行程序 outputOptions 以及在保存图像时使用的回调。
         */
        imageCapture.takePicture(
            outputOptions, ContextCompat.getMainExecutor(this), object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: ${exc.message}", exc)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    /*
                    拍照成功。将照片保存到您先前创建的文件中，显示一个消息框以告知用户操作成功，然后输出日志语句
                     */
                    val savedUri = Uri.fromFile(photoFile)
                    val msg = "Photo capture succeeded: $savedUri"
                    Toast.makeText(baseContext, msg, Toast.LENGTH_SHORT).show()
                    Log.d(TAG, msg)
                }
            })
    }

    private class LuminosityAnalyzer(private val listener: LumaListener) : ImageAnalysis.Analyzer {

        private fun ByteBuffer.toByteArray(): ByteArray {
            rewind()    // Rewind the buffer to zero
            val data = ByteArray(remaining())
            get(data)   // Copy the buffer into a byte array
            return data // Return the byte array
        }

        override fun analyze(image: ImageProxy) {

            val buffer = image.planes[0].buffer
            val data = buffer.toByteArray()
            val pixels = data.map { it.toInt() and 0xFF }
            val luma = pixels.average()

            listener(luma)

            image.close()
        }
    }

}