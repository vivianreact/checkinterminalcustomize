package com.ml.pa.checkinterminalcustomize

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.*
import android.widget.*
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.android.BeepManager
import com.journeyapps.barcodescanner.*
import org.json.JSONObject
import java.util.*

class ContinuousCapture : Activity() {
    private val utils = Utils(this)
    private lateinit var progressDialog: AlertDialog
    private lateinit var barcodeView: DecoratedBarcodeView
    private lateinit var beepManager: BeepManager
    private lateinit var progress: ProgressBar
    private lateinit var viewfinderView: ViewfinderView
    private lateinit var btnHome: Button
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var logoView: ImageView
    private lateinit var btnOK: Button
    private lateinit var customAlertDialog: CardView
    private lateinit var alertTitle: TextView
    private lateinit var alertContent: TextView

    private var lastText: String = ""
    private var registrationDomain = ""
    private var checkpointCode = ""
    private var checkInMode: Boolean = true
    private var kioskPassword = utils.DEFAULT_KIOSK_PASSWORD
    private var terminalID = ""
    private var cameraFacing = false
    private var isScanning = false
    private var showProgress = false
    private var barcodeInitialized = false
    private var scanLandscape = ""
    private var scanPortrait = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.continuous_scan)
        viewfinderView = findViewById(R.id.zxing_viewfinder_view)
        constraintLayout = findViewById(R.id.root_layout)
        progress = ProgressBar(this)
        beepManager = BeepManager(this)
        alertTitle = findViewById(R.id.alert_title)
        alertContent = findViewById(R.id.alert_content)
        customAlertDialog = findViewById(R.id.custom_alert_dialog)
        customAlertDialog.visibility = View.GONE
        btnOK = findViewById(R.id.alert_ok)
        logoView = findViewById(R.id.logo_view)
        btnHome = findViewById(R.id.btn_home)
        btnHome.setOnClickListener { setupHomeButton() }
        getValue()
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), utils.CAMERA_PERMISSION_CODE)
        } else {
            setupBarcode()
        }
        setupProgressBar()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == utils.CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupBarcode()
            } else {
                onBackPressed()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (barcodeInitialized)
            barcodeView.resume()
    }

    override fun onPause() {
        super.onPause()
        barcodeView.pause()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        return barcodeView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event)
    }

    private val callback: BarcodeCallback = object : BarcodeCallback {
        override fun barcodeResult(result: BarcodeResult) {
            if (isScanning) {
                return
            }

            lastText = result.text
            beepManager.playBeepSoundAndVibrate()

            if (lastText != null) {
                processPrintingStatus()
                runAndPrint(lastText)
            }
        }
    }

    private fun setupProgressBar() {
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER
        val llParam = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llParam.gravity = Gravity.CENTER
        llParam.topMargin = 50
        llParam.bottomMargin = 30
        ll.layoutParams = llParam

        val progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        progressBar.setPadding(0, 30, 0, 0)
        progressBar.layoutParams = llParam
        progressBar.indeterminateTintList = ColorStateList.valueOf(getColor(R.color.sweet_blue))

        val tvText = TextView(this)
        tvText.text = "Scanning"
        tvText.textSize = 18f
        llParam.topMargin = 0
        llParam.bottomMargin = 50
        llParam.gravity = Gravity.CENTER
        tvText.layoutParams = llParam

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        ll.addView(progressBar)
        ll.addView(tvText)
        builder.setView(ll)
        progressDialog = builder.create()
    }

    private fun setupHomeButton() {
        val ll = LinearLayout(this)
        ll.orientation = LinearLayout.VERTICAL
        ll.gravity = Gravity.CENTER
        val llParam = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llParam.setMargins(20, 20, 20, 0)

        val tvText = TextView(this)
        tvText.text = "Please Enter Password"
        tvText.setTypeface(null, Typeface.BOLD)
        tvText.textSize = 18f
        tvText.layoutParams = llParam

        val input = EditText(this)
        val llParam2 = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        llParam2.setMargins(20, 10, 20, 0)
        input.layoutParams = llParam2
        input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD

        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setCancelable(true)
        ll.addView(tvText)
        ll.addView(input)
        builder.setView(ll)
        builder.setPositiveButton(
            "OK"
        ) { dialog, _ ->
            if (input.text.toString() == kioskPassword) {
                dialog.dismiss()
                onBackPressed()
            } else {
                utils.showAlertBox(
                    customAlertDialog,
                    btnOK,
                    alertTitle,
                    alertContent,
                    "Wrong Password!",
                    "Please contact administrator"
                )
            }
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
        builder.show()
    }

    private fun setupBarcode() {
        if (!barcodeInitialized) {
            barcodeView = findViewById(R.id.zxing_barcode_scanner)
            val formats: Collection<BarcodeFormat> =
                Arrays.asList(BarcodeFormat.QR_CODE, BarcodeFormat.CODE_39)
            barcodeView.barcodeView.decoderFactory = DefaultDecoderFactory(formats)
            barcodeView.initializeFromIntent(intent)
            barcodeView.decodeContinuous(callback)
            barcodeView.cameraSettings.requestedCameraId = if (cameraFacing) 1 else 0
            barcodeInitialized = true
        }
    }

    private fun runAndPrint(registrationCode: String) {
        val checkInModeString = if (checkInMode) "in" else "out"
        utils.run("https",
            registrationDomain,
            utils.CHECK_IN_API + "&qrcode=$registrationCode&terminal=$terminalID&checkpoint=$checkpointCode&mode=$checkInModeString",
            { response ->
                try {
                    val data = response.body()!!.string()
                    val jsonResponse = JSONObject(data)
                    val status =
                        if (jsonResponse.has("status")) jsonResponse.getString("status") else "0"
                    val title =
                        if (jsonResponse.has("title")) jsonResponse.getString("title") else ""
                    val error =
                        if (jsonResponse.has("error")) jsonResponse.getString("error") else ""
                    val heading =
                        if (jsonResponse.has("heading")) jsonResponse.getString("heading") else ""
                    val message =
                        if (jsonResponse.has("message")) jsonResponse.getString("message") else ""
                    if (status == "1") {
                        processPrintSuccess(title, heading + "\n\n" + message)
                    } else {
                        processPrintFail(title, heading + "\n\n" + message + "\n" + error)
                    }
                } catch (e: Exception) {
                    processPrintFail("FAIL", "Unable to get data")
                    utils.showAlert("Could not check in due to : $e", true)
                }
            },
            { error ->
                processPrintFail("FAIL", "Unable to check in")
                utils.showAlert("Unable to check in due to $error ", true)
            }
        )
    }

    private fun processPrintingStatus() {
        runOnUiThread {
            progressDialog.show()
            showProgress = true
            isScanning = true
        }
    }

    private fun processPrintSuccess(sTitle: String, message: String) {
        runOnUiThread {
            if (showProgress) {
                progressDialog.dismiss()
                showProgress = false
            }
            utils.showAlertBox(customAlertDialog, btnOK, alertTitle, alertContent,
                sTitle,
                message, {}, { dialog ->
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed({
                        isScanning = false
                        dialog.visibility = View.GONE
                    }, utils.RESCAN_TIME)
                }
            )
        }
    }

    private fun processPrintFail(sTitle: String, message: String) {
        utils.showAlert("Check In Failed: $message", true)
        runOnUiThread {
            if (showProgress) {
                progressDialog.dismiss()
                showProgress = false
            }
            utils.showAlertBox(
                customAlertDialog,
                btnOK,
                alertTitle,
                alertContent,
                sTitle,
                message,
                { isScanning = false },
                {})
        }
    }

    private fun getValue() {
        val defRegistrationDomain = "expo.inspiresmexpo.com"
        val defCameraFacing = false // true means it is Front Camera
        val sharedPref: SharedPreferences =
            getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
        checkInMode = sharedPref.getBoolean("checkInMode", true)
        terminalID = sharedPref.getString("terminalID", "") ?: ""
        checkpointCode = sharedPref.getString("checkpointCode", "") ?: ""
        kioskPassword =
            sharedPref.getString("kioskPassword", utils.DEFAULT_KIOSK_PASSWORD)
                ?: utils.DEFAULT_KIOSK_PASSWORD
        registrationDomain = sharedPref.getString("registrationDomain", defRegistrationDomain)
            ?: defRegistrationDomain
        cameraFacing = sharedPref.getBoolean("cameraFacing", defCameraFacing)
        val logo = sharedPref.getString("logo", utils.DEFAULT_LOGO) ?: utils.DEFAULT_LOGO
        utils.setLogo(logo, logoView)
        scanPortrait = sharedPref.getString("scanPortrait", "") ?: ""
        scanLandscape = sharedPref.getString("scanLandscape", "") ?: ""
        val orientation: Int = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            utils.setBackgroundLayout(scanLandscape, constraintLayout)
        } else {
            utils.setBackgroundLayout(scanPortrait, constraintLayout)
        }
    }
}