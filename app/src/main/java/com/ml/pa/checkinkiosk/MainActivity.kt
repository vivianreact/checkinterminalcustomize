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
import com.example.tscdll.TscWifiActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanIntentResult
import com.journeyapps.barcodescanner.ScanOptions
import okhttp3.Response
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.concurrent.thread


class MainActivity : AppCompatActivity() {
    private val utils = Utils(this)
    private val tscDll = TscWifiActivity()

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
    private var scannerIP = utils.DEFAULT_IP_ADDRESS
    private var scannerPort = utils.DEFAULT_PORT
    private var kioskID = ""
    private var cameraFacing = false
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
                utils.run("https",
                    registrationDomain,
                    utils.GET_BADGE_API + result.contents,
                    { response -> runAndPrint(response) },
                    { error -> processPrintFail(error) }
                )
            }
        }

    private fun startScanner() {
        if (scannerIP == "") {
            utils.showAlertBox("Error", "Please setup your printer")
        } else {
            thread {
                var reachable = false
                try {
                    if (InetAddress.getByName(scannerIP).isReachable(1000)) {
                        reachable = true
                    }
                } catch (e: UnknownHostException) {
                    utils.showAlert("Device IP Address is unreachable [B02]: $e", true)
                    processPrintFail("Device IP Address is unreachable [B02]")
                } catch (e: Exception) {
                    utils.showAlert("Device IP Address is unreachable [B01]: $e", true)
                    processPrintFail("Device IP Address is unreachable [B01]")
                }
                runOnUiThread {
                    if (reachable) {
                        val options = ScanOptions()
                        options.setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                        options.setPrompt("Scan a barcode")
                        options.setCameraId(if (cameraFacing) 1 else 0) //0: back camera,1: front camera
                        options.captureActivity = CaptureActivityPortrait::class.java
                        options.setBeepEnabled(true)
                        options.setBarcodeImageEnabled(true)
                        barcodeLauncher.launch(options)
                    } else {
                        utils.showAlert("Device IP Address is unreachable [B03]", true)
                        processPrintFail("Device IP Address is unreachable [B03]")
                    }
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

    private fun runAndPrint(response: Response) {
        var printStatus = false
        var printErrorMessage = "Unable to print"

        try {
            val data = response.body()!!.string()
            val jsonResponse = JSONObject(data)
            val status =
                if (jsonResponse.has("status")) jsonResponse.getString("status") else "0"
            val message =
                if (jsonResponse.has("message")) jsonResponse.getString("message") else ""
            if (status == "1") {
                val jsonData = jsonResponse.getJSONObject("data")
                val urlString =
                    if (jsonData.has("url")) jsonData.getString("url") else null
                val registrationCode =
                    if (jsonData.has("code")) jsonData.getString("code") else null
                val paperWidth =
                    if (jsonData.has("paper_width")) jsonData.getString("paper_width")
                        .toInt() else 0
                val paperHeight =
                    if (jsonData.has("paper_height")) jsonData.getString("paper_height")
                        .toInt() else 0
                if (urlString != null && paperWidth == 0 && paperHeight == 0) {
                    printStatus = true

                    print(urlString, paperWidth, paperHeight)

                    utils.run("https",
                        registrationDomain,
                        utils.CHECK_IN_API + "&qrcode=$registrationCode&terminal=$kioskID&checkpoint=$checkpointCode&mode=$checkInMode",
                        { },
                        { error ->
                            printErrorMessage = "Unable to send log to server"
                            utils.showAlert(
                                "Unable to add print log on server due to $error ",
                                true
                            )
                        }
                    )
                } else {
                    printErrorMessage = "Invalid QR Code / Setting"
                }
            } else {
                printErrorMessage = message
            }
        } catch (e: Exception) {
            utils.showAlert("Could not run and print: $e", true)
        }

        if (!printStatus) {
            processPrintFail(printErrorMessage)
        }
    }

    private fun print(fileUrl: String, paperWidth: Int, paperHeight: Int) {
        var printStatus = false
        var printErrorMessage = "Unable to print! Please contact administrator"
        val speed = 5
        val density = 10
        val sensor = 3
        val sensorDistance = 0
        val sensorOffset = 0
        try {
            tscDll.openport(scannerIP, scannerPort)
            tscDll.setup(
                paperWidth,
                paperHeight,
                speed,
                density,
                sensor,
                sensorDistance,
                sensorOffset
            )
            tscDll.clearbuffer()
            val bitmap: Bitmap? = utils.getBitmapFromURL(fileUrl)
            if (bitmap != null) {
                tscDll.sendbitmap(0, 0, bitmap)
                tscDll.printlabel(1, 1)
                val printerStatus = tscDll.printerstatus()
                if (printerStatus == "00") {

                    printStatus = true
                } else {
                    printErrorMessage = "Unable to print badge"
                }
            } else {
                printErrorMessage = "Image is not valid"
            }

            tscDll.clearbuffer()
            tscDll.closeport(1000)
        } catch (e: NullPointerException) {
            printErrorMessage = "Printer offline"
            utils.showAlert("Printer offline to $e (Might be Invalid IP Address)", true)
        } catch (e: Exception) {
            printErrorMessage = "Unable to print due to setup problem [A01]"
            utils.showAlert("Unable to print due to $e", true)
        }
        if (printStatus) {
            processPrintSuccess()
        } else {
            processPrintFail(printErrorMessage)
        }
    }

    private fun processPrintingStatus() {
        runOnUiThread {
            btnHome.visibility = View.GONE
            statusPanel.visibility = View.VISIBLE
            statusTitle.text = "One Moment"
            statusDesc.text = "Printing Badge ..."
            btnRetry.visibility = View.GONE
        }
    }

    private fun processPrintSuccess() {
        runOnUiThread {
            btnHome.visibility = View.VISIBLE
            statusPanel.visibility = View.VISIBLE
            statusTitle.text = "Print Badge Success"
            statusDesc.text = "Badge is printed successfully"
        }
        val handler = Handler(Looper.getMainLooper())
        handler.postDelayed({
            runOnUiThread { statusPanel.visibility = View.GONE }
            startScanner()
        }, utils.RESCAN_TIME)
    }

    private fun processPrintFail(message: String) {
        utils.showAlert("Print Badge Failed: $message", true)
        runOnUiThread {
            btnHome.visibility = View.VISIBLE
            statusPanel.visibility = View.VISIBLE
            statusTitle.text = "Print Badge Fail"
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
        scannerIP = sharedPref.getString("scannerIP", utils.DEFAULT_IP_ADDRESS)!!
        scannerPort = sharedPref.getInt("scannerPort", utils.DEFAULT_PORT)
        kioskID = sharedPref.getString("kioskID", "") ?: ""
        kioskPassword =
            sharedPref.getString("kioskPassword", utils.DEFAULT_KIOSK_PASSWORD)
                ?: utils.DEFAULT_KIOSK_PASSWORD
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