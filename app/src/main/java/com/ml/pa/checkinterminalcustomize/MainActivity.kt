package com.ml.pa.checkinterminalcustomize

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.constraintlayout.widget.ConstraintLayout
import okhttp3.Response
import org.json.JSONObject
import java.net.InetAddress
import java.net.UnknownHostException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    private lateinit var btnScan: Button
    private lateinit var btnSetting: Button
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var logoView: ImageView
    private lateinit var btnOK: Button
    private lateinit var customAlertDialog: CardView
    private lateinit var alertTitle: TextView
    private lateinit var alertContent: TextView

    private val utils = Utils(this)
    private var hasGetData = false
    private var registrationDomain = ""
    private var checkpointCode = ""
    private var terminalID = ""
    private var checkInMode: Boolean = true
    private var kioskPassword = utils.DEFAULT_KIOSK_PASSWORD
    private var landingLandscape = ""
    private var landingPortrait = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        constraintLayout = findViewById(R.id.root_layout)
        alertTitle = findViewById(R.id.alert_title)
        alertContent = findViewById(R.id.alert_content)
        customAlertDialog = findViewById(R.id.custom_alert_dialog)
        customAlertDialog.visibility = View.GONE
        btnOK = findViewById(R.id.alert_ok)
        logoView = findViewById(R.id.logo_view)
        btnSetting = findViewById(R.id.btnSetting)
        btnSetting.setOnClickListener { goToSetting() }
        btnScan = findViewById(R.id.btnScan)
        btnScan.background.alpha = 180
        btnScan.setOnClickListener {
            utils.showAlertBox(customAlertDialog, btnOK, alertTitle, alertContent,
                "Loading",
                "Checking Internet Connection", {}, { dialog ->
                    val handler = Handler(Looper.getMainLooper())
                    handler.postDelayed({
                        dialog.visibility = View.GONE
                    }, 2000)
                })
            if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(
                    arrayOf(Manifest.permission.CAMERA),
                    utils.CAMERA_PERMISSION_CODE
                )
            } else {
                startScanner()
            }
        }
        val extra = intent.extras
        if (extra != null) {
            val updateValue = extra.getInt("updateValue")
            if (updateValue == 1) hasGetData = false
        }
        getValue()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String?>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == utils.CAMERA_PERMISSION_CODE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startScanner()
            } else {
                utils.showAlertBox(
                    customAlertDialog, btnOK, alertTitle, alertContent,
                    "Error",
                    "Please allow camera permission in setting for scanning!"
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        getValue()
    }

    override fun onDestroy() {
        super.onDestroy()
        hasGetData = false
    }

    private fun startScanner() {
        if (checkpointCode == "") {
            utils.showAlertBox(
                customAlertDialog,
                btnOK,
                alertTitle,
                alertContent,
                "Error",
                "Please key in check point code"
            )
        } else {
            runOnUiThread {
                thread {
                    var reachable = false
                    try {
                        if (InetAddress.getByName(registrationDomain).isReachable(5000)) {
                            reachable = true
                        }
                    } catch (e: UnknownHostException) {
                        utils.showAlertBox(
                            customAlertDialog, btnOK, alertTitle, alertContent,
                            "Print Badge Fail",
                            "Device IP Address is unreachable [B02]"
                        )
                    } catch (e: Exception) {
                        utils.showAlertBox(
                            customAlertDialog, btnOK, alertTitle, alertContent,
                            "Print Badge Fail",
                            "Device IP Address is unreachable [B01]"
                        )
                    }
                    runOnUiThread {
                        if (reachable) {
                            val intent = Intent(this, ContinuousCapture::class.java)
                            startActivity(intent)
                        } else {
                            utils.showAlertBox(
                                customAlertDialog, btnOK, alertTitle, alertContent,
                                "Print Badge Fail",
                                "Device IP Address is unreachable [B03]"
                            )
                        }
                    }
                }
            }
        }
    }

    private fun goToSetting() {
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

        val builder: android.app.AlertDialog.Builder = android.app.AlertDialog.Builder(this)
        builder.setCancelable(true)
        ll.addView(tvText)
        ll.addView(input)
        builder.setView(ll)
        builder.setPositiveButton(
            "OK"
        ) { dialog, _ ->
            if (input.text.toString() == kioskPassword) {
                dialog.dismiss()
                val intent = Intent(this, SettingActivity::class.java)
                startActivity(intent)
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
        ) { dialog, _ -> dialog.cancel() }
        builder.show()
    }

    fun getValue() {
        val sharedPref: SharedPreferences =
            getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
        registrationDomain = sharedPref.getString("registrationDomain", "") ?: ""
        checkpointCode = sharedPref.getString("checkpointCode", "") ?: ""
        kioskPassword =
            sharedPref.getString("kioskPassword", utils.DEFAULT_KIOSK_PASSWORD)
                ?: utils.DEFAULT_KIOSK_PASSWORD
        val logo = sharedPref.getString("logo", utils.DEFAULT_LOGO) ?: utils.DEFAULT_LOGO
        utils.setLogo(logo, logoView)
        terminalID = sharedPref.getString("terminalID", "") ?: ""
        checkInMode = sharedPref.getBoolean("checkInMode", true)
        landingPortrait = sharedPref.getString("landingPortrait", "") ?: ""
        landingLandscape = sharedPref.getString("landingLandscape", "") ?: ""
        val orientation: Int = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            utils.setBackgroundLayout(landingLandscape, constraintLayout)
        } else {
            utils.setBackgroundLayout(landingPortrait, constraintLayout)
        }

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
                    utils.showAlertBox(
                        customAlertDialog,
                        btnOK,
                        alertTitle,
                        alertContent,
                        "Error",
                        error
                    )
                }
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val orientation: Int = resources.configuration.orientation
        if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
            utils.setBackgroundLayout(landingLandscape, constraintLayout)
        } else {
            utils.setBackgroundLayout(landingPortrait, constraintLayout)
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
                val logo =
                    if (jsonData.has("logo")) jsonData.getString("logo") else utils.DEFAULT_LOGO
                utils.setLogo(logo, logoView)
                landingPortrait =
                    if (jsonData.has("landing_portrait")) jsonData.getString("landing_portrait") else ""
                landingLandscape =
                    if (jsonData.has("landing_landscape")) jsonData.getString("landing_landscape") else ""
                val scanPortrait =
                    if (jsonData.has("scan_portrait")) jsonData.getString("scan_portrait") else ""
                val scanLandscape =
                    if (jsonData.has("scan_landscape")) jsonData.getString("scan_landscape") else ""
                val orientation: Int = resources.configuration.orientation
                if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                    utils.setBackgroundLayout(landingLandscape, constraintLayout)
                } else {
                    utils.setBackgroundLayout(landingPortrait, constraintLayout)
                }
                val sharedPref: SharedPreferences =
                    getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
                val editor: SharedPreferences.Editor = sharedPref.edit()
                editor.putString("kioskPassword", kioskPassword)
                editor.putString("landingPortrait", landingPortrait)
                editor.putString("landingLandscape", landingLandscape)
                editor.putString("scanPortrait", scanPortrait)
                editor.putString("scanLandscape", scanLandscape)
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
}