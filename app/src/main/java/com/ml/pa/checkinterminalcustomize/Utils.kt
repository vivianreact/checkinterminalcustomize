package com.ml.pa.checkinterminalcustomize

import android.view.View
import android.widget.*
import androidx.cardview.widget.CardView
import android.app.Activity
import android.content.Context
import android.content.Context.CONNECTIVITY_SERVICE
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.constraintlayout.widget.ConstraintLayout
import okhttp3.*
import java.io.IOException
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.net.UnknownHostException
import kotlin.concurrent.thread

class Utils(private val context: Context) {
    val SHARED_PREFERENCE_NAME = "checkinterminalcustomize"
    val DEFAULT_KIOSK_PASSWORD = "frontdesk"
    val SETUP_URL =
        "index.php?option=com_platform&view=api&task=scan_print_badge_setup"
    val CHECK_IN_API =
        "index.php?option=com_platform&view=api&task=terminal_check_in"
    val DEFAULT_LOGO = "@drawable/applogo"
    val RESCAN_TIME: Long = 1 * 1000
    val CAMERA_PERMISSION_CODE = 100

    fun getBitmapFromURL(src: String?, callback: (Bitmap) -> Unit) {
        thread {
            if (isOnline()) {
                val urlOpen = URL(src).openConnection() as HttpURLConnection
                try {
                    val ins = urlOpen.inputStream
                    val image = BitmapFactory.decodeStream(ins)
                    ins.close()
                    urlOpen.disconnect()
                    callback(image)
                } catch (e: IOException) {
                    showAlert(e.toString(), true)
                } catch (e: Exception) {
                    showAlert(e.toString(), true)
                }
            } else {
                showAlert("No Internet Connection")
            }
        }
    }

    private fun isOnline(): Boolean {
        val cm = (context as Activity).getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val n = cm.activeNetwork
        if (n != null) {
            val nc = cm.getNetworkCapabilities(n)
            return nc!!.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) || nc.hasTransport(
                NetworkCapabilities.TRANSPORT_WIFI
            )
        }
        return false
    }

    fun run(
        host: String,
        domainName: String,
        url: String,
        listener: (r: Response) -> Unit,
        failedListener: (e: String) -> Unit
    ) {
        if (isOnline()) {
            if (domainName == "") {
                failedListener.invoke("Please key in Registration Domain")
                return
            }

            thread {
                var reachable = false
                try {
                    if (InetAddress.getByName(domainName).isReachable(1000)) {
                        reachable = true
                    }
                } catch (e: UnknownHostException) {
                    failedListener.invoke("Registration Domain is unreachable [B02]")
                } catch (e: Exception) {
                    showAlert("Registration Domain is unreachable [B01]: $e", true)
                    failedListener.invoke("Registration Domain is unreachable [B01]")
                }

                (context as Activity).runOnUiThread {
                    if (reachable) {
                        try {
                            val request =
                                Request.Builder().url("$host://$domainName/$url").build()
                            val client = OkHttpClient()
                            client.newCall(request).enqueue(object : Callback {
                                override fun onFailure(call: Call, e: IOException) {
//                                  failedListener.invoke("Request Failed due to $e")
                                    showAlert("Request Failed due to $e", true)
                                    call.cancel()
                                }

                                override fun onResponse(call: Call, response: Response) {
                                    if (response.isSuccessful) {
                                        val responseBody = response.body()
                                        if (responseBody != null) {
                                            listener.invoke(response)
                                        } else {
                                            failedListener.invoke("Cannot Connect to Server [C01]")
                                        }
                                    } else {
                                        failedListener.invoke("Cannot Connect to Server [C02]")
                                    }
                                    response.close()
                                }
                            })

                        } catch (e: UnknownHostException) {
                            failedListener.invoke("Invalid Registration Domain due to $e")
                        } catch (e: Exception) {
                            failedListener.invoke("Invalid Request due to $e")
                        }
                    } else {
                        failedListener.invoke("Domain is unreachable [B03]")
                    }
                }
            }

        } else {
            failedListener.invoke("No Internet Connection")
        }
    }

    fun showAlert(m: String, silent: Boolean = false) {
        val message: String = if (m != "") m else "Nothing to be shown"
        if (!silent) (context as Activity).runOnUiThread {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
        Log.d("polaroidmessage", message)
    }

    fun showAlertBox(
        dialog:CardView,
        btnOK: Button,
        alertTitle: TextView,
        alertContent: TextView,
        title: String,
        message: String,
        onClickAction: () -> Unit = {},
        immediateAction: ( dialog:CardView) -> Unit = {}
    ) {
        showAlert(message, true)
        dialog.visibility = View.VISIBLE
        alertTitle.text = title
        alertContent.text = message
        btnOK.setOnClickListener{
            onClickAction()
            dialog.visibility = View.GONE
        }
        immediateAction(dialog)
    }

    fun getTextFieldString(textField: EditText): String {
        return if (textField.text.toString().trim() == "") "" else textField.text.toString().trim()
    }

    fun getTextFieldInt(textField: EditText): Int {
        return textField.text.toString().trim().toInt()
    }

    fun setLogo(logo: String, logoView: ImageView) {
        if (logo != DEFAULT_LOGO && logo != "") {
            getBitmapFromURL(logo) { mIcon11 ->
                (context as Activity).runOnUiThread {
                    logoView.setImageBitmap(mIcon11)
                }
            }
        }
    }

    fun setBackgroundLayout(landingLandscape: String, constraintLayout: ConstraintLayout) {
        if (landingLandscape != "") {
            getBitmapFromURL(landingLandscape) { mIcon11 ->
                (context as Activity).runOnUiThread {
                    val dr: Drawable = BitmapDrawable(context.getResources(), mIcon11);
                    constraintLayout.background = dr
                }
            }
        }
    }
}