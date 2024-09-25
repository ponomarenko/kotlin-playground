package com.playground.app.ui.webview

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Activity.RESULT_OK
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.playground.app.MainActivity
import com.playground.app.databinding.FragmentWebviewBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date


class WebViewFragment : Fragment() {
    private var _binding: FragmentWebviewBinding? = null
    private lateinit var webView: WebView

    // file upload
    private var uploadCallback: ValueCallback<Array<Uri>>? = null
    private var imageUri: Uri? = null
    lateinit var currentPhotoPath: String

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWebviewBinding.inflate(inflater, container, false)
        val root: View = binding.root

        webView = binding.webview

        setUpWebView()

        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun setUpWebView() {
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                Log.d("Js Error", error.toString())
                super.onReceivedError(view, request, error)
            }

            override fun onUnhandledKeyEvent(view: WebView?, event: KeyEvent?) {
                Log.d("Js UnhandledKeyEvent", event.toString())
                super.onUnhandledKeyEvent(view, event)
            }
        }

        WebView.setWebContentsDebuggingEnabled(true)

        webView.settings.apply {
            allowFileAccess = true
            allowContentAccess = true
            domStorageEnabled = true
            javaScriptEnabled = true
            mediaPlaybackRequiresUserGesture = false
            useWideViewPort = true
            setSupportZoom(true)
        }

        webView.loadUrl("file:///android_asset/input-file-capture.html")
        // webView.loadUrl("https://ponomarenko.github.io/30-seconds-of-web/media-devices")

        webView.addJavascriptInterface(
            WebAppInterface(MainActivity.applicationContext().applicationContext),
            "appClient"
        )

        webView.webChromeClient = MyWebChromeClient()

    }

    inner class WebAppInterface(private val mContext: Context) {
        @JavascriptInterface
        fun getAndroidVersion(): Int {
            return Build.VERSION.SDK_INT
        }

        @JavascriptInterface
        fun showToast() {
            Toast.makeText(mContext, "Toast from android", Toast.LENGTH_SHORT).show()
        }
    }

    inner class MyWebChromeClient : WebChromeClient() {
        override fun onShowFileChooser(
            webView: WebView?,
            filePathCallback: ValueCallback<Array<Uri>>?, // TODO: make it backward compatible
            fileChooserParams: FileChooserParams?
        ): Boolean {
            uploadCallback = filePathCallback
            createChooserIntent()
            return true
        }

        override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
            Log.d("WebView", consoleMessage.message())
            return true
        }

        override fun onPermissionRequest(request: PermissionRequest) {
            request.grant(request.resources)
        }
    }

    private fun createChooserIntent() {
        var photoFile: File? = null
        val authorities: String = MainActivity.applicationContext().packageName + ".fileprovider"

        try {
            photoFile = createImageFile()
            imageUri = FileProvider.getUriForFile(
                MainActivity.applicationContext().applicationContext, authorities,
                photoFile!!
            )
        } catch (e: IOException) {
            e.printStackTrace()
        }

        // camera intent
        val captureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // camera intent includes handling the output file
        captureIntent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri)
        // gallery intent
        val photoIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        // picker intent, includes gallery intent
        val chooserIntent = Intent.createChooser(photoIntent, "File chooser")
        // we include the camera intent in the picker intent
        chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf<Parcelable>(captureIntent))
        // launch the intent
        resultLauncher.launch(chooserIntent)
    }

    // new activityResult handling
    var resultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                if (uploadCallback != null) {
                    // process image upload to the webview
                    processImageUpload(result.data)
                } else {
                    Toast.makeText(
                        MainActivity.applicationContext(),
                        "An error occurred while uploading the file",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }

    // creates the file in order to upload the camera photo to the webview
    @Throws(IOException::class)
    private fun createImageFile(): File? {
        // create an image file name
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss").format(Date())
        val storageDir: File =
            MainActivity.applicationContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)!!
        return File.createTempFile(
            "JPEG_${timeStamp}", // prefix
            ".jpg", // suffix
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun processImageUpload(data: Intent?) {
        if (data != null) {
            val results: Array<Uri>
            val uriData: Uri? = data.data

            if (uriData != null) {
                arrayOf(uriData).also { results = it }
                // pass the data to the webview
                uploadCallback!!.onReceiveValue(results)
            } else {
                uploadCallback!!.onReceiveValue(null)
            }
        } else {
            if (imageUri != null) {
                uploadCallback!!.onReceiveValue(arrayOf(imageUri!!))
            }
        }
    }
}