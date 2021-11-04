package uk.co.romatech.mifare

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AlignmentSpan
import android.view.Gravity
import android.widget.TextView
import android.widget.Toast
import com.datalogic.decode.BarcodeManager
import com.datalogic.decode.ReadListener
import com.datalogic.decode.configuration.LengthControlMode
import com.datalogic.decode.configuration.ScannerProperties
import com.datalogic.device.ErrorManager
import com.datalogic.device.configuration.ConfigException
import org.json.JSONObject
import java.util.*
import kotlin.concurrent.schedule

class ScanActivity : BeenergisedInterface, AppCompatActivity() {
    private val beEnergised: BeEnergised = BeEnergised()
    private var decoder: BarcodeManager? = null
    private var listener: ReadListener? = null
    private var cardid: String? = null
    private var cardNumber: String? = null
    private var cpId: String? = null
    private var connectorId: String? = null
    private var evseId: String ? = null
    private var reference: String? = null
    private lateinit var referenceText: TextView
    private lateinit var evsidText: TextView
    private lateinit var actionText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_scan)

        decoder = BarcodeManager()
        val configuration = ScannerProperties.edit(decoder)
        configuration.code128.enable.set(true)
        configuration.code128.lengthMode.set(LengthControlMode.RANGE)
        configuration.code128.userID.set('y')
        configuration.code128.Length1.set(2)
        configuration.code128.Length2.set(40)

        configuration.qrCode.enable.set(true)

        ErrorManager.enableExceptions(false)

        val errorCode = configuration.store(decoder, true)

        // Check the return value.
        if (errorCode != ConfigException.SUCCESS) {
            onError(getString(R.string.decoder_error))
        }
        decoder = null

        cardid = intent.getStringExtra("cardId")
        cardNumber = intent.getStringExtra("cardNumber")
        referenceText = findViewById(R.id.referenceTextId)
        evsidText = findViewById(R.id.evsidTextId)
        actionText = findViewById(R.id.actionTextId)
    }

    override fun onResume() {
        super.onResume()

        // If the decoder instance is null, create it.
        if (decoder == null) { // Remember an onPause call will set it to null.
            decoder = BarcodeManager()
        }

        // From here on, we want to be notified with exceptions in case of errors.
        ErrorManager.enableExceptions(true)

        try {

            // Create an anonymous class.
            listener = ReadListener { decodeResult ->
                // Implement the callback method.
                // Change the displayed text to the current received result.
                val regex = "AT\\*HTB\\*E1\\d{6}".toRegex()
                val match = regex.find(decodeResult.text)
                if (match != null) {
                    evseId = match.value
                    evsidText.text = evseId
                    beEnergised.getEvse(this, evseId!!)
                } else {
                    reference = decodeResult.text
                    referenceText.text = reference
                    if (evseId != null) {
                        beEnergised.getEvse(this, evseId!!)
                    }
                }
            }

            // Remember to add it, as a listener.
            decoder!!.addReadListener(listener)

        } catch (ex: Exception) {
            onError(ex.message.toString())
        }

    }

    override fun onPause() {
        super.onPause()
        if (decoder != null) {
            try {
                decoder!!.removeReadListener(listener)
                decoder = null
            } catch (e: Exception) {
                onError(getString(R.string.decoder_error))
            }
        }
    }

    override fun beEnergisedCallback(api: String, response: JSONObject) {
        when (api) {
            "/evse/" -> {
                connectorId = response.optString("connector_id")
                cpId = response.optString("cp_id")
                beEnergised.getCp(this, cpId!!)
            }
            "/cp_status/" -> {
                beEnergised.getCp(this, cpId!!)
            }
            "/cp/" -> {
                val connectors = response.getJSONArray("connectors")
                var connector: JSONObject? = null
                run find@ {
                    (0 until connectors.length()).forEach { i ->
                        val tmpc: JSONObject = connectors.getJSONObject(i)
                        if (tmpc.get("uuid") == connectorId) {
                            connector = tmpc
                            return@find
                        }
                    }
                }
                if (connector?.optString("status") == "unavailable") {
                    clear()
                } else {
                    val transaction = connector?.optString("charging_session")
                    if (transaction != "null") {
                        beEnergised.remoteStop(this, transaction!!)
                    } else if (reference != null) {
                        beEnergised.putCard(this, cardid!!, cardNumber!!, reference!!)
                    }
                }
            }
            "/card/" -> {
                actionText.text = getString(R.string.check_cp)
                beEnergised.remoteStart(this, cardid!!, connectorId!!)
            }
            "/remote_start/" -> {
                actionText.text = getString(R.string.authorised)
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                clear()
            }
            "/remote_stop/" -> {
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
                vibrator.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
                showToast(getString(R.string.stopped))
                clear()
            }
        }
    }

    override fun beEnergisedErrorCallback(api: String, code: Int, message: String) {
        when(api) {
            "/evse/" -> {
                actionText.text = getString(R.string.evse_error)
            }
            "/cp_status/" -> {
                actionText.text = getString(R.string.cpstatus_error)
            }
            "/cp/" -> {
                actionText.text = getString(R.string.cp_error)
            }
            "/card/" -> {
                actionText.text = getString(R.string.card_error)
            }
            "/remote_start/" -> {
                if (code == java.net.HttpURLConnection.HTTP_FORBIDDEN) {
                    actionText.text = getString(R.string.remote_start_refused)
                } else {
                    actionText.text = getString(R.string.cant_start)
                }
            }
            "/remote_stop/" -> {
                actionText.text = getString(R.string.stop_error)
            }
        }
        onError(actionText.text.toString())
    }

    override fun timeoutCallback() {
        onError(getString(R.string.timeout_error))
    }

    override fun noConnectionCallback() {
        onError(getString(R.string.connection_error))
    }

    override fun pageNotFoundCallback(api: String) {
        when(api) {
            "/evse/" -> {
                onError(getString(R.string.evse_error))
            }
            else -> {
                onError(getString(R.string.unexpected))
            }
        }
    }

    private fun onError(errorMessage: String) {
        val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
        val toneGenerator = ToneGenerator(AudioManager.STREAM_SYSTEM, ToneGenerator.MAX_VOLUME)
        toneGenerator.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE)
        showToast(errorMessage)
        clear()
    }

    private fun clear() {
        Timer().schedule(5000) {
            runOnUiThread() {
                referenceText.text = getString(R.string.scan_barcode)
                evsidText.text = getString(R.string.scan_qr)
                actionText.text = ""
                reference = null
                evseId = null
            }
        }
    }

    private fun showToast(message: String) {
        val centeredText = SpannableString(message)
        centeredText.setSpan(
            AlignmentSpan { Layout.Alignment.ALIGN_CENTER },
            0,
            message.length - 1,
            Spannable.SPAN_INCLUSIVE_INCLUSIVE)
        val toast = Toast.makeText(this, centeredText, Toast.LENGTH_LONG)
        toast.setGravity(Gravity.TOP, 0, 100)
        toast.show()
    }
}