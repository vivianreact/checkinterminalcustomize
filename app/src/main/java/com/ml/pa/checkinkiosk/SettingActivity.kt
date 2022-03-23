package com.ml.pa.checkinkiosk

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

@SuppressLint("UseSwitchCompatOrMaterialCode")
class SettingActivity : AppCompatActivity() {
    private val utils = Utils(this)

    private lateinit var btnSave: Button
    private lateinit var tfRegistrationDomain: EditText
    private lateinit var tfCheckpointCode: EditText
    private lateinit var switchCheckInMode: Switch
    private lateinit var switchCameraFacing: Switch
    private lateinit var tfTerminalID: EditText

    private var registrationDomain = ""
    private var checkpointCode = ""
    private var terminalID = ""
    private var checkInMode: Boolean = false
    private var cameraFacing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        tfRegistrationDomain = findViewById(R.id.registration_domain)
        tfCheckpointCode = findViewById(R.id.checkpoint_code)
        tfTerminalID = findViewById(R.id.terminal_id)
        switchCheckInMode = findViewById(R.id.checkin_mode)
        switchCameraFacing = findViewById(R.id.camera_facing)

        btnSave = findViewById(R.id.btnSave)
        btnSave.setOnClickListener { setValue() }
    }

    override fun onStart() {
        super.onStart()
        getValue()
    }

    private fun getValue() {
        //default data put here
         var defRegistrationDomain = "foodandhotelmalaysia.com.my"
         var defCheckpointCode = "AU2ZSS" //default data put here
         var defTerminalID = "A102" //default data put here
         var defCheckInMode = true // true means it is check in mode
         var defCameraFacing = false // true means it is Front Camera

        val sharedPref: SharedPreferences =
            getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
        registrationDomain = sharedPref.getString("registrationDomain", defRegistrationDomain) ?:defRegistrationDomain
        checkpointCode = sharedPref.getString("checkpointCode", defCheckpointCode)?:defCheckpointCode
        terminalID = sharedPref.getString("terminalID", defTerminalID) ?: defTerminalID
        checkInMode = sharedPref.getBoolean("checkInMode", defCheckInMode)
        cameraFacing = sharedPref.getBoolean("cameraFacing", defCameraFacing)

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
            utils.showAlertBox("ERROR", "Please fill in all the fields")
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
        editor.apply()

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("updateValue", 1)
        startActivity(intent)
    }
}
