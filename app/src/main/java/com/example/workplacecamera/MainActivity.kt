package com.example.workplacecamera

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.ViewGroup
import android.view.animation.AlphaAnimation
import android.view.animation.Animation
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.ZoomState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.viewpager2.widget.ViewPager2
import com.example.workplacecamera.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.min

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private lateinit var cameraExecutor: ExecutorService
    private var isTorchOn: Boolean = false

    private val aspectRatios = listOf("4:3", "1:1", "16:9")
    private var currentRatioIndex = 0

    private lateinit var scaleGestureDetector: ScaleGestureDetector

    private val workplaceImageUris = mutableListOf<Uri>()
    private var galleryAdapter: GalleryAdapter? = null

    // Multiple permissions launcher (Camera + Storage for imported photos)
    private val permissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val cameraGranted = perms[Manifest.permission.CAMERA] == true
        if (cameraGranted) {
            initCameraProvider()
            syncAndLoadAllWorkPhotos()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
            finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        checkAndRequestPermissions()

        setupGesturesAndControls()
        setupNoteClearButton()
        setupInAppViewer()
        setupAspectRatioToggle()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.galleryOverlay.visibility == View.VISIBLE) {
                    closeInAppViewer()
                } else {
                    finish()
                }
            }
        })
    }

    private fun checkAndRequestPermissions() {
        val permissionsNeeded = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            // Android 11 (Oppo ColorOS) requires this to read non-app created files
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED && permissionsNeeded.size == 1) {
            initCameraProvider()
            syncAndLoadAllWorkPhotos()
        } else {
            permissionsLauncher.launch(permissionsNeeded.toTypedArray())
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (binding.galleryOverlay.visibility != View.VISIBLE) {
            when (keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP, KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    triggerShutterFlash()
                    takePhotoWithTimestamp()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun setupAspectRatioToggle() {
        binding.btnAspectRatio.setOnClickListener {
            currentRatioIndex = (currentRatioIndex + 1) % aspectRatios.size
            val ratioName = aspectRatios[currentRatioIndex]
            binding.btnAspectRatio.text = ratioName
            updateViewfinderMasking(ratioName)
            bindCameraUseCases()
            Toast.makeText(this, "Aspect Ratio: $ratioName", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateViewfinderMasking(ratioName: String) {
        binding.viewFinder.post {
            val viewWidth = binding.cameraContainer.width
            val viewHeight = binding.cameraContainer.height

            when (ratioName) {
                "1:1" -> {
                    val activeHeight = viewWidth
                    val totalMask = (viewHeight - activeHeight).coerceAtLeast(0)
                    val maskSize = totalMask / 2

                    binding.maskTop.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, maskSize
                    ).apply { gravity = android.view.Gravity.TOP }

                    binding.maskBottom.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, maskSize
                    ).apply { gravity = android.view.Gravity.BOTTOM }

                    binding.maskTop.visibility = View.VISIBLE
                    binding.maskBottom.visibility = View.VISIBLE
                }
                "4:3" -> {
                    val targetHeight = (viewWidth * 4) / 3
                    val totalMask = (viewHeight - targetHeight).coerceAtLeast(0)
                    val maskSize = totalMask / 2

                    binding.maskTop.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, maskSize
                    ).apply { gravity = android.view.Gravity.TOP }

                    binding.maskBottom.layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, maskSize
                    ).apply { gravity = android.view.Gravity.BOTTOM }

                    binding.maskTop.visibility = View.VISIBLE
                    binding.maskBottom.visibility = View.VISIBLE
                }
                "16:9" -> {
                    binding.maskTop.visibility = View.GONE
                    binding.maskBottom.visibility = View.GONE
                }
            }
        }
    }

    private fun setupNoteClearButton() {
        binding.btnClearNote.setOnClickListener {
            binding.etBatchName.text?.clear()
        }

        binding.etBatchName.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                binding.btnClearNote.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun setupGesturesAndControls() {
        scaleGestureDetector = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                val cam = camera ?: return false
                val currentZoom = cam.cameraInfo.zoomState.value?.zoomRatio ?: 1f
                val delta = detector.scaleFactor
                cam.cameraControl.setZoomRatio(currentZoom * delta)
                return true
            }
        })

        binding.viewFinder.setOnTouchListener { _, event ->
            scaleGestureDetector.onTouchEvent(event)
            if (event.action == MotionEvent.ACTION_UP && !scaleGestureDetector.isInProgress) {
                focusOnPoint(event.x, event.y)
            }
            true
        }

        binding.captureButtonFrame.setOnClickListener {
            triggerShutterFlash()
            takePhotoWithTimestamp()
        }

        binding.btnFlash.setOnClickListener {
            toggleTorch()
        }

        binding.ivRecentThumbnail.setOnClickListener {
            openInAppViewer()
        }
    }

    private fun setupInAppViewer() {
        binding.btnGalleryClose.setOnClickListener {
            closeInAppViewer()
        }

        binding.viewPagerGallery.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateViewerCounter(position)
            }
        })

        binding.btnSharePhoto.setOnClickListener {
            val position = binding.viewPagerGallery.currentItem
            if (position in workplaceImageUris.indices) {
                val uri = workplaceImageUris[position]
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Share Sample"))
            }
        }

        binding.btnDeletePhoto.setOnClickListener {
            val position = binding.viewPagerGallery.currentItem
            if (position in workplaceImageUris.indices) {
                val uriToDelete = workplaceImageUris[position]
                try {
                    // Handle deletion for both content URIs and direct file URIs
                    if (uriToDelete.scheme == "file") {
                        val file = File(uriToDelete.path ?: "")
                        if (file.exists()) file.delete()
                    } else {
                        contentResolver.delete(uriToDelete, null, null)
                    }

                    workplaceImageUris.removeAt(position)
                    galleryAdapter?.notifyItemRemoved(position)
                    Toast.makeText(this, "Sample deleted", Toast.LENGTH_SHORT).show()

                    if (workplaceImageUris.isEmpty()) {
                        closeInAppViewer()
                        binding.ivRecentThumbnail.setImageDrawable(null)
                    } else {
                        val nextPos = min(position, workplaceImageUris.size - 1)
                        binding.viewPagerGallery.setCurrentItem(nextPos, false)
                        updateViewerCounter(nextPos)
                        loadLatestThumbnail()
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, "Delete failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun openInAppViewer() {
        syncAndLoadAllWorkPhotos()
        if (workplaceImageUris.isEmpty()) {
            Toast.makeText(this, "No WorkPlace samples found", Toast.LENGTH_SHORT).show()
            return
        }

        galleryAdapter = GalleryAdapter(this, workplaceImageUris)
        binding.viewPagerGallery.adapter = galleryAdapter
        binding.viewPagerGallery.setCurrentItem(0, false)
        updateViewerCounter(0)

        binding.galleryOverlay.visibility = View.VISIBLE
    }

    private fun closeInAppViewer() {
        binding.galleryOverlay.visibility = View.GONE
        binding.viewPagerGallery.adapter = null
    }

    private fun updateViewerCounter(position: Int) {
        if (workplaceImageUris.isNotEmpty()) {
            binding.tvGalleryCounter.text = "${position + 1} of ${workplaceImageUris.size}"
        }
    }

    // Loads BOTH app-clicked photos and manually moved October photos
    private fun syncAndLoadAllWorkPhotos() {
        workplaceImageUris.clear()

        // 1. Direct Physical File Check inside Pictures/WorkPlace
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "WorkPlace")
        if (dir.exists() && dir.isDirectory) {
            val physicalFiles = dir.listFiles { file ->
                file.isFile && (file.extension.equals("jpg", true) || 
                                file.extension.equals("jpeg", true) || 
                                file.extension.equals("png", true))
            }?.sortedByDescending { it.lastModified() } // Sort newest to oldest

            if (!physicalFiles.isNullOrEmpty()) {
                for (file in physicalFiles) {
                    workplaceImageUris.add(Uri.fromFile(file))
                }
            }
        }

        // 2. Fallback MediaStore query if physical folder yielded nothing
        if (workplaceImageUris.isEmpty()) {
            val projection = arrayOf(MediaStore.Images.Media._ID)
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%Pictures/WorkPlace%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            val cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                while (it.moveToNext()) {
                    val id = it.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    workplaceImageUris.add(contentUri)
                }
            }
        }

        loadLatestThumbnail()
    }

    private fun loadLatestThumbnail() {
        if (workplaceImageUris.isNotEmpty()) {
            try {
                contentResolver.openInputStream(workplaceImageUris[0])?.use { stream ->
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    val bitmap = BitmapFactory.decodeStream(stream, null, options)
                    bitmap?.let {
                        val thumb = Bitmap.createScaledBitmap(it, 128, 128, true)
                        binding.ivRecentThumbnail.setImageBitmap(thumb)
                    }
                }
            } catch (e: Exception) {
                Log.e("WorkPlace", "Failed loading thumbnail", e)
            }
        }
    }

    private fun initCameraProvider() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            cameraProvider = cameraProviderFuture.get()
            bindCameraUseCases()
            updateViewfinderMasking(aspectRatios[currentRatioIndex])
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCameraUseCases() {
        val provider = cameraProvider ?: return
        val currentRatio = aspectRatios[currentRatioIndex]

        val cameraXRatio = if (currentRatio == "16:9") {
            AspectRatio.RATIO_16_9
        } else {
            AspectRatio.RATIO_4_3
        }

        val preview = Preview.Builder()
            .setTargetAspectRatio(cameraXRatio)
            .build().also {
                it.setSurfaceProvider(binding.viewFinder.surfaceProvider)
            }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setTargetAspectRatio(cameraXRatio)
            .build()

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            provider.unbindAll()
            camera = provider.bindToLifecycle(this, cameraSelector, preview, imageCapture)

            camera?.cameraInfo?.zoomState?.observe(this) { state: ZoomState? ->
                state?.let {
                    binding.tvZoomRatio.text = String.format(Locale.US, "%.1fx", it.zoomRatio)
                }
            }
        } catch (e: Exception) {
            Log.e("WorkPlace", "Camera bind failed: ${e.message}", e)
        }
    }

    private fun focusOnPoint(x: Float, y: Float) {
        val cam = camera ?: return

        binding.focusRing.x = x - (binding.focusRing.width / 2)
        binding.focusRing.y = y - (binding.focusRing.height / 2)
        binding.focusRing.alpha = 1f
        binding.focusRing.animate()
            .scaleX(1.3f).scaleY(1.3f)
            .setDuration(200)
            .withEndAction {
                binding.focusRing.animate()
                    .scaleX(1f).scaleY(1f).alpha(0f)
                    .setDuration(350)
                    .start()
            }.start()

        val factory = binding.viewFinder.meteringPointFactory
        val point = factory.createPoint(x, y)
        val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE)
            .setAutoCancelDuration(3, TimeUnit.SECONDS)
            .build()

        cam.cameraControl.startFocusAndMetering(action)
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        isTorchOn = !isTorchOn
        cam.cameraControl.enableTorch(isTorchOn)
        binding.btnFlash.imageTintList = android.content.res.ColorStateList.valueOf(
            if (isTorchOn) Color.parseColor("#00E676") else Color.WHITE
        )
    }

    private fun triggerShutterFlash() {
        val flash = AlphaAnimation(1f, 0f).apply {
            duration = 100
            repeatCount = 1
            repeatMode = Animation.REVERSE
        }
        binding.viewFinder.startAnimation(flash)
    }

    private fun takePhotoWithTimestamp() {
        val capture = imageCapture ?: return
        val batchTag = binding.etBatchName.text.toString().trim()
        val currentRatio = aspectRatios[currentRatioIndex]

        capture.takePicture(
            cameraExecutor,
            object : ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureSuccess(imageProxy: ImageProxy) {
                    processAndSaveWithTimestamp(imageProxy, batchTag, currentRatio)
                }

                override fun onError(exc: ImageCaptureException) {
                    runOnUiThread {
                        Toast.makeText(this@MainActivity, "Capture failed: ${exc.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        )
    }

    private fun processAndSaveWithTimestamp(imageProxy: ImageProxy, batchTag: String, ratio: String) {
        try {
            val bitmap = imageProxyToBitmap(imageProxy)
            val rotationDegrees = imageProxy.imageInfo.rotationDegrees
            imageProxy.close()

            var processedBitmap = rotateBitmap(bitmap, rotationDegrees)

            if (ratio == "1:1") {
                val size = min(processedBitmap.width, processedBitmap.height)
                val xOffset = (processedBitmap.width - size) / 2
                val yOffset = (processedBitmap.height - size) / 2
                processedBitmap = Bitmap.createBitmap(processedBitmap, xOffset, yOffset, size, size)
            }

            val finalBitmap = drawTimestampOverlay(processedBitmap, batchTag)

            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val prefix = if (batchTag.isNotEmpty()) "${batchTag}_" else "WORK_"
            val fileName = "${prefix}${timeStamp}.jpg"

            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Pictures/WorkPlace")
                }
            }

            val uri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            uri?.let {
                contentResolver.openOutputStream(it)?.use { stream ->
                    finalBitmap.compress(Bitmap.CompressFormat.JPEG, 92, stream)
                }
            }

            val thumb = Bitmap.createScaledBitmap(finalBitmap, 128, 128, true)

            runOnUiThread {
                binding.ivRecentThumbnail.setImageBitmap(thumb)
                Toast.makeText(this@MainActivity, "Saved: $fileName", Toast.LENGTH_SHORT).show()
                syncAndLoadAllWorkPhotos()
            }
        } catch (e: Exception) {
            Log.e("WorkPlace", "Failed processing image", e)
        }
    }

    private fun imageProxyToBitmap(image: ImageProxy): Bitmap {
        val plane = image.planes[0]
        val buffer = plane.buffer
        val bytes = ByteArray(buffer.remaining())
        buffer.get(bytes)
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        if (degrees == 0) return bitmap
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun drawTimestampOverlay(src: Bitmap, batchTag: String): Bitmap {
        val result = src.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(result)

        val timestamp = SimpleDateFormat("yyyy-MM-dd  HH:mm:ss", Locale.US).format(Date())
        val tagPrefix = if (batchTag.isNotEmpty()) "[$batchTag] " else ""
        val text = "$tagPrefix$timestamp"

        val textSize = (result.width * 0.030f)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            this.textSize = textSize
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            setShadowLayer(4f, 2f, 2f, Color.BLACK)
        }

        val textWidth = paint.measureText(text)
        val x = result.width - textWidth - (result.width * 0.04f)
        val y = result.height - (result.height * 0.04f)

        val bgPaint = Paint().apply {
            color = Color.parseColor("#8A000000")
            style = Paint.Style.FILL
        }
        val padding = textSize * 0.45f
        val rect = RectF(x - padding, y - textSize, x + textWidth + padding, y + padding)
        canvas.drawRoundRect(rect, 10f, 10f, bgPaint)

        canvas.drawText(text, x, y, paint)
        return result
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}
