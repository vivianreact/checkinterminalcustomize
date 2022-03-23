package com.ml.pa.checkinkiosk

import android.content.Intent
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class SettingActivity : AppCompatActivity() {
    private val utils = Utils(this)

    private lateinit var btnCalibration: Button
    private lateinit var btnSave: Button
    private lateinit var tfRegistrationDomain: EditText
    private lateinit var tfScannerIP: EditText
    private lateinit var tfScannerPort: EditText
    private lateinit var switchCameraFacing: Switch
    private lateinit var tfKioskID: EditText

    private var registrationDomain = ""
    private var scannerIP = ""
    private var scannerPort = 0
    private var kioskID = ""
    private var cameraFacing: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setting)
        tfRegistrationDomain = findViewById(R.id.registration_domain)
        tfKioskID = findViewById(R.id.kiosk_id)
        tfScannerIP = findViewById(R.id.ip_address)
        tfScannerPort = findViewById(R.id.port_number)
        switchCameraFacing = findViewById(R.id.camera_facing)

        btnSave = findViewById(R.id.btnSave)
        btnCalibration = findViewById(R.id.btnCalibration)
        btnSave.setOnClickListener { setValue() }
        btnCalibration.setOnClickListener { calibratePrinter() }
    }

    override fun onStart() {
        super.onStart()
        getValue()
    }

    private fun calibratePrinter() {
        if (scannerIP == "") {
            utils.showAlertBox("Error", "Printer is not setup so calibration cannot be done")
        } else {
            utils.showAlert("Calibrating")
            utils.run(
                "http", scannerIP,
                utils.CALIBRATE_URL,
                { utils.showAlert("Calibration Done") },
                { error ->
                    utils.showAlertBox("Error", error)
                }
            )
        }
    }

    private fun getValue() {
        val sharedPref: SharedPreferences =
            getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
        registrationDomain = sharedPref.getString("registrationDomain", "") ?: ""
        scannerIP = sharedPref.getString("scannerIP", utils.DEFAULT_IP_ADDRESS)!!
        scannerPort = sharedPref.getInt("scannerPort", utils.DEFAULT_PORT)
        kioskID = sharedPref.getString("kioskID", "") ?: ""
        cameraFacing = sharedPref.getBoolean("cameraFacing", false)

        tfRegistrationDomain.setText(registrationDomain)
        tfScannerIP.setText(scannerIP)
        tfScannerPort.setText(scannerPort.toString())
        tfKioskID.setText(kioskID)
        switchCameraFacing.isChecked = cameraFacing
    }

    fun setValue() {
        registrationDomain = utils.getTextFieldString(tfRegistrationDomain)
        scannerIP = utils.getTextFieldString(tfScannerIP)
        scannerPort = utils.getTextFieldInt(tfScannerPort)
        kioskID = utils.getTextFieldString(tfKioskID)
        cameraFacing = switchCameraFacing.isChecked

        if (registrationDomain == "" || scannerIP == "" || scannerPort == 0 || kioskID == "") {
            utils.showAlertBox("ERROR", "Please fill in all the fields")
            return
        }
        val sharedPref: SharedPreferences =
            getSharedPreferences(utils.SHARED_PREFERENCE_NAME, MODE_PRIVATE)
        val editor: SharedPreferences.Editor = sharedPref.edit()
        editor.putString("registrationDomain", registrationDomain)
        editor.putString("scannerIP", scannerIP)
        editor.putInt("scannerPort", scannerPort)
        editor.putString("kioskID", kioskID)
        editor.putBoolean("cameraFacing", cameraFacing)
        editor.apply()

        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("updateValue", 1)
        startActivity(intent)

    }
}
