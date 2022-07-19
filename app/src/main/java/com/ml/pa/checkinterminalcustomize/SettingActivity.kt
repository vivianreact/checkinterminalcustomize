package com.ml.pa.checkinterminalcustomize

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView

class SettingActivity : AppCompatActivity() {
    private val utils = Utils(this)

    private lateinit var btnSave: Button
    private lateinit var tfRegistrationDomain: EditText
    private lateinit var tfCheckpointCode: EditText
    private lateinit var switchCheckInMode: Switch
    private lateinit var switchCameraFacing: Switch
    private lateinit var tfTerminalID: EditText
    private lateinit var btnOK: Button
    private lateinit var customAlertDialog: CardView
    private lateinit var alertTitle: TextView
    private lateinit var alertContent: TextView
    private lateinit var logoView: ImageView
    private var registrationDomain = ""
    private var checkpointCode = ""
    private var terminalID = ""
    private var logo = ""
    private var checkInMode: Boolean = true
    private var cameraFacing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        tfRegistrationDomain = findViewById(R.id.registration_domain)
        tfCheckpointCode = findViewById(R.id.checkpoint_code)
        tfTerminalID = findViewById(R.id.terminal_id)
        switchCheckInMode = findViewById(R.id.checkin_mode)
        switchCameraFacing = findViewById(R.id.camera_facing)
        alertTitle = findViewById(R.id.alert_title)
        alertContent = findViewById(R.id.alert_content)
        customAlertDialog = findViewById(R.id.custom_alert_dialog)
        customAlertDialog.visibility = View.GONE
        logoView = findViewById(R.id.logo_view)
        btnOK = findViewById(R.id.alert_ok)
        btnOK.setOnClickListener { customAlertDialog.visibility = View.GONE}

        btnSave = findViewById(R.id.btnSave)
        btnSave.setOnClickListener { setValue() }
    }

    override fun onStart() {
        super.onStart()
        getValue()
    }

    private fun getValue() {
        //default data put here
         var defRegistrationDomain = "expo.inspiresmexpo.com"
//         var defRegistrationDomain = "registration.frontdesk.my"
         var defCheckpointCode = "6QLMFC" //default data put here
//         var defCheckpointCode = "AU2ZSS" //default data put here
         var defTerminalID = "A101" //default data put here
         var defCheckInMode = true // true means it is check in mode
         var defCameraFacing = false // true means it is Front Camera

        val sharedPref: SharedPreferences =
            getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
        registrationDomain = sharedPref.getString("registrationDomain", defRegistrationDomain) ?:defRegistrationDomain
        checkpointCode = sharedPref.getString("checkpointCode", defCheckpointCode)?:defCheckpointCode
        terminalID = sharedPref.getString("terminalID", defTerminalID) ?: defTerminalID
        checkInMode = sharedPref.getBoolean("checkInMode", defCheckInMode)
        cameraFacing = sharedPref.getBoolean("cameraFacing", defCameraFacing)
        logo = sharedPref.getString("logo", utils.DEFAULT_LOGO) ?: utils.DEFAULT_LOGO
        utils.setLogo(logo, logoView)

        tfRegistrationDomain.setText(registrationDomain)
        tfCheckpointCode.setText(checkpointCode)
        tfTerminalID.setText(terminalID)
        switchCheckInMode.isChecked = checkInMode
        switchCameraFacing.isChecked = cameraFacing
    }

    fun setValue() {
        registrationDomain = utils.getTextFieldString(tfRegistrationDomain)
        checkpointCode = utils.getTextFieldString(tfCheckpointCode)
        terminalID = utils.getTextFieldString(tfTerminalID)
        checkInMode = switchCheckInMode.isChecked
        cameraFacing = switchCameraFacing.isChecked
       if (registrationDomain == "" || checkpointCode == "" || terminalID == "") {
            utils.showAlertBox(customAlertDialog,btnOK,alertTitle, alertContent,"ERROR", "Please fill in all the fields")
            return
        }

        val sharedPref: SharedPreferences =
            getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPref.edit()
        editor.putString("registrationDomain", registrationDomain)
        editor.putString("checkpointCode", checkpointCode)
        editor.putString("terminalID", terminalID)
        editor.putBoolean("checkInMode", checkInMode)
        editor.putBoolean("cameraFacing", cameraFacing)
        editor.putString("logo", logo)
        editor.apply()

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("updateValue", 1)
        startActivity(intent)
    }
}
