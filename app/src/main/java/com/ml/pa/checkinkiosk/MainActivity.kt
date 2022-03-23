package com.ml.pa.checkinkiosk

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import okhttp3.Response
import org.json.JSONObject
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    private val utils = Utils(this)

    private lateinit var btnScan: Button
    private lateinit var btnSetting: Button
    private lateinit var btnSetting2: Button
    private lateinit var btnHome: Button
    private lateinit var btnRetry: Button
    private lateinit var logoView: ImageView
    private lateinit var logoView2: ImageView
    private lateinit var statusTitle: TextView
    private lateinit var statusDesc: TextView
    private lateinit var statusPanel: LinearLayout

    private var registrationDomain = ""
    private var checkpointCode = ""
    private var terminalID = ""
    private var checkInMode: Boolean = false
    private var cameraFacing: Boolean = false

    private var kioskPassword = utils.DEFAULT_KIOSK_PASSWORD
    private var logo = utils.DEFAULT_LOGO

    private var hasShownSetting = false
    private var hasGetData = false
    private var clickCount = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusTitle = findViewById(R.id.status_title)
        statusDesc = findViewById(R.id.status_desc)
        statusPanel = findViewById(R.id.status_panel)
        btnRetry = findViewById(R.id.btnRetry)

        logoView = findViewById(R.id.logo_view)
        logoView2 = findViewById(R.id.logo_view2)
        btnScan = findViewById(R.id.btnScan)
        btnSetting = findViewById(R.id.btnSetting)
        btnSetting2 = findViewById(R.id.btnSetting2)
        btnHome = findViewById(R.id.btnHome)
        btnScan.setOnClickListener { startScanner() }
        btnRetry.setOnClickListener {
            startScanner()
        }
        btnSetting.setOnClickListener {
            setClickTimeOut { goToSetting() }
        }
        btnSetting2.setOnClickListener {
            setClickTimeOut { goToSetting() }
        }
        btnHome.setOnClickListener {
            statusPanel.visibility = View.GONE
        }

        val extra = intent.extras
        if (extra != null) {
            val updateValue = extra.getInt("updateValue")
            if (updateValue == 1) hasGetData = false
        }
        getValue()
    }

    override fun onResume() {
        super.onResume()
        getValue()
    }

    override fun onDestroy() {
        super.onDestroy()
        hasGetData = false
        hasShownSetting = false
    }

    private var barcodeLauncher =
        registerForActivityResult(ScanContract()) { result: ScanIntentResult ->
            if (result.contents != null) {
                processPrintingStatus()
                runAndPrint(result.contents)
            }
        }

    private fun startScanner() {
        if (checkpointCode == "") {
            utils.showAlertBox("Error", "Please setup your printer")
        } else {
            thread {

                runOnUiThread {

                    val options = ScanOptions()
                    options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    options.setPrompt("Scan a barcode")
                    options.setCameraId(if (cameraFacing) 1 else 0) //0: back camera,1: front camera
                    options.captureActivity = CaptureActivityPortrait::class.java
                    options.setBeepEnabled(true)
                    options.setBarcodeImageEnabled(true)
                    barcodeLauncher.launch(options)

                }
            }
        }
    }

    private fun goToSetting() {
        if (!hasShownSetting) {
            hasShownSetting = true
            val linearLayout = LinearLayout(this)
            linearLayout.orientation = LinearLayout.VERTICAL
            val layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            )
            layoutParams.setMargins(30, 20, 30, 30)
            val input = EditText(this)
            input.textSize = 20f
            input.hint = "Please Enter Password"
            input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            linearLayout.addView(input, layoutParams)

            val builder: AlertDialog.Builder = AlertDialog.Builder(this)
            builder.setTitle("Administrator")
            builder.setView(linearLayout)
            builder.setPositiveButton("OK") { dialog, _ ->
                if (input.text.toString() == kioskPassword) {
                    dialog.dismiss()
                    val intent = Intent(this, SettingActivity::class.java)
                    startActivity(intent)
                } else {
                    utils.showAlertBox("Wrong Password!", "Please contact administrator")
                }
            }
            builder.setNegativeButton("Cancel") { dialog, _ -> dialog.cancel() }
            builder.setOnDismissListener { hasShownSetting = false }
            builder.show()
        }
    }

    private fun getServerData(response: Response) {
        try {
            val data = response.body()!!.string()
            val jsonResponse = JSONObject(data)
            val status =
                if (jsonResponse.has("status")) jsonResponse.getString("status") else "0"
            val message =
                if (jsonResponse.has("message")) jsonResponse.getString("message") else ""
            if (status == "1") {
                val jsonData = jsonResponse.getJSONObject("data")
                kioskPassword =
                    if (jsonData.has("kiosk_password")) jsonData.getString("kiosk_password") else utils.DEFAULT_KIOSK_PASSWORD
                logo =
                    if (jsonData.has("logo")) jsonData.getString("logo") else utils.DEFAULT_LOGO
                setLogo()
                val sharedPref: SharedPreferences =
                    getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
                val editor: SharedPreferences.Editor = sharedPref.edit()
                editor.putString("kioskPassword", kioskPassword)
                editor.putString("logo", logo)
                editor.apply()
            } else {
                utils.showAlert(message)
            }
        } catch (e: Exception) {
            utils.showAlert("Could not get server data: $e", true)
            utils.showAlert("Unable to get data")
        }
    }

    private fun runAndPrint(registrationCode: String) {

        var checkInModeString = if (checkInMode) "in" else "out"
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
                        processPrintSuccess(title, heading + "\n" + message)
                    } else {
                        processPrintFail(title, heading + "\n" + message+"\n"+error)
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
            btnHome.visibility = View.GONE
            statusPanel.visibility = View.VISIBLE
            statusTitle.text = "One Moment"
            statusDesc.text = "Loading ..."
            btnRetry.visibility = View.GONE
        }
    }

    private fun processPrintSuccess(sTitle: String, message: String) {
        runOnUiThread {
            btnHome.visibility = View.VISIBLE
            statusPanel.visibility = View.VISIBLE
            statusTitle.text = sTitle
            statusDesc.text = message
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            runOnUiThread { statusPanel.visibility = View.GONE }
            startScanner()
        }, utils.RESCAN_TIME)
    }

    private fun processPrintFail(sTitle: String, message: String) {
        utils.showAlert("Print Badge Failed: $message", true)
        runOnUiThread {
            btnHome.visibility = View.VISIBLE
            statusPanel.visibility = View.VISIBLE
            statusTitle.text = sTitle
            statusDesc.text = message
            btnRetry.visibility = View.VISIBLE
        }
    }

    private fun setLogo() {
        if (logo != utils.DEFAULT_LOGO) {
            thread {
                val mIcon11: Bitmap? = utils.getBitmapFromURL(logo)
                runOnUiThread {
                    logoView.setImageBitmap(mIcon11)
                    logoView2.setImageBitmap(mIcon11)
                }
            }
        } else {
            logoView.setImageResource(R.drawable.applogo)
            logoView2.setImageResource(R.drawable.applogo)
        }
    }

    private fun setClickTimeOut(listener: () -> Unit) {
        clickCount++
        if (clickCount >= 5) {
            listener.invoke()
            clickCount = 0
        }

        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            clickCount = 0
        }, 3000)
    }

    fun getValue() {
        val sharedPref: SharedPreferences =
            getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
        registrationDomain = sharedPref.getString("registrationDomain", "") ?: ""
        checkpointCode = sharedPref.getString("checkpointCode", "") ?: ""
        terminalID = sharedPref.getString("terminalID", "") ?: ""
        kioskPassword =
            sharedPref.getString("kioskPassword", utils.DEFAULT_KIOSK_PASSWORD)
                ?: utils.DEFAULT_KIOSK_PASSWORD
        checkInMode = sharedPref.getBoolean("checkInMode", true)
        cameraFacing = sharedPref.getBoolean("cameraFacing", false)
        logo = sharedPref.getString("logo", utils.DEFAULT_LOGO) ?: utils.DEFAULT_LOGO
        setLogo()

        if (!hasGetData) {
            if (registrationDomain == "") {
                utils.showAlert("Registration Domain is empty", true)
                goToSetting()
                return
            }
            utils.run("https", registrationDomain, utils.SETUP_URL,
                { response ->
                    getServerData(response)
                    hasGetData = true
                }, { error ->
                    utils.showAlertBox("Error", error)
                }
            )
        }
    }
}