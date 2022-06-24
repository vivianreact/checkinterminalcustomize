package com.ml.pa.checkinterminal

import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Typeface
import android.graphics.drawable.AnimationDrawable
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import okhttp3.Response
import org.json.JSONObject


class MainActivity : AppCompatActivity() {
    private val utils = Utils(this)

    private lateinit var btnScan: Button
    private lateinit var btnSetting: Button
    private lateinit var constraintLayout: ConstraintLayout
    private lateinit var logoView: ImageView

    private var registrationDomain = ""
    private var checkpointCode = ""
    private var terminalID = ""
    private var checkInMode: Boolean = true
    private var cameraFacing: Boolean = false

    private var kioskPassword = utils.DEFAULT_KIOSK_PASSWORD
    private var logo = utils.DEFAULT_LOGO

    private var hasGetData = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        constraintLayout = findViewById(R.id.root_layout)
        val animationDrawable = constraintLayout.background as AnimationDrawable
        animationDrawable.setExitFadeDuration(4000)
        animationDrawable.start()

        logoView = findViewById(R.id.logo_view)

        btnScan = findViewById(R.id.btnScan)
        btnScan.setOnClickListener { startScanner() }
        btnScan.background.alpha = 180

        btnSetting = findViewById(R.id.btnSetting)
        btnSetting.setOnClickListener {
            goToSetting()
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
    }

    private fun startScanner() {
        if (checkpointCode == "") {
            utils.showAlertBox("Error", "Please setup your printer")
        } else {
            runOnUiThread {
                val intent = Intent(this, ContinuousCapture::class.java)
                startActivity(intent)
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
        llParam.setMargins(20,20,20,0)

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
        llParam2.setMargins(20,10,20,0)
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
                utils.showAlertBox("Wrong Password!", "Please contact administrator")
            }
        }
        builder.setNegativeButton(
            "Cancel"
        ) { dialog, which -> dialog.cancel() }
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
        terminalID = sharedPref.getString("terminalID", "") ?: ""
        checkInMode = sharedPref.getBoolean("checkInMode", true)
        cameraFacing = sharedPref.getBoolean("cameraFacing", false)
        logo = sharedPref.getString("logo", utils.DEFAULT_LOGO) ?: utils.DEFAULT_LOGO
        utils.setLogo(logo,logoView)

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
                utils.setLogo(logo,logoView)
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
}