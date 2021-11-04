package uk.co.romatech.mifare

import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.*
import android.text.Layout
import android.text.Spannable
import android.text.SpannableString
import android.text.style.AlignmentSpan
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.Toast
import org.json.JSONObject
import java.util.*
import kotlin.concurrent.schedule

class MainActivity : BeenergisedInterface, AppCompatActivity() {

    private val beEnergised: BeEnergised = BeEnergised()
    private var nfcAdapter: NfcAdapter? = null
    private var cardid: String? = null
    private var cardNumber: String? = null

    private lateinit var cardLabelText: TextView
    private lateinit var contactText: TextView
    private lateinit var continueButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)
        cardLabelText = findViewById(R.id.cardLabelTextId)
        contactText = findViewById(R.id.contactTextId)
        continueButton = findViewById(R.id.continueButtonId)
        cardLabelText.text = getString(R.string.tap_card)
        continueButton.setOnClickListener {
            val intent = Intent(this, ScanActivity::class.java)
            intent.putExtra("cardId",cardid)
            intent.putExtra("cardNumber", cardNumber)
            startActivity(intent)
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (intent != null) {
            if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
                checkCard(intent)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter?.let {
            NFCUtil.enableNFCInForeground(it, this, javaClass)
        }
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter?.let {
            NFCUtil.disableNFCInForeground(it, this)
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent != null) {
            if (NfcAdapter.ACTION_TECH_DISCOVERED == intent.action) {
                checkCard(intent)
            }
        }

    }

    private fun checkCard(intent: Intent) {
        Log.d("MIFARE", "intent")
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        val tagId = tag?.id
        tagId?.reverse()
        cardLabelText.text = getString(R.string.checking_card)
        contactText.visibility = View.INVISIBLE
        continueButton.visibility = View.INVISIBLE
        beEnergised.getCard(this, tagId!!.toHex())
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    override fun beEnergisedCallback(api: String, response: JSONObject) {
        when(api) {
            "/card/" -> {
                if (response.optBoolean("active")) {
                    cardid = response.optString("tag_id")
                    cardNumber = response.optString("number")
                    cardLabelText.text = cardNumber
                    beEnergised.getContact(this, response.optString("contact"))
                } else {
                    showToast(getString(R.string.invalid_card))
                    cardLabelText.text = getString(R.string.invalid_card)
                    Timer().schedule(3000) {
                        runOnUiThread() {
                            cardLabelText.text = getString(R.string.tap_card)
                        }
                    }
                }
            }
            "/contact/" -> {
                val contact = StringBuilder()
                contact.append("${response.get("display_name")}\n")
                contact.append("${response.get("street")}\n")
                contact.append("${response.get("city")}\n")
                contact.append("${response.get("zip")}\n")
                contactText.text = contact.toString()
                contactText.visibility = View.VISIBLE
                continueButton.visibility = View.VISIBLE
            }
        }

    }

    override fun beEnergisedErrorCallback(api: String, code: Int, message: String) {
        cardLabelText.text = message
        contactText.visibility = View.INVISIBLE
        continueButton.visibility = View.INVISIBLE
    }

    override fun timeoutCallback() {
        showToast(getString(R.string.timeout_error))
        contactText.visibility = View.INVISIBLE
        continueButton.visibility = View.INVISIBLE
        Timer().schedule(3000) {
            runOnUiThread() {
                cardLabelText.text = getString(R.string.tap_card)
            }
        }
    }

    override fun noConnectionCallback() {
        showToast(getString(R.string.connection_error))
        contactText.visibility = View.INVISIBLE
        continueButton.visibility = View.INVISIBLE

    }

    override fun pageNotFoundCallback(api: String) {
        cardLabelText.text = getString(R.string.invalid_card)
        contactText.visibility = View.INVISIBLE
        continueButton.visibility = View.INVISIBLE
        Timer().schedule(3000) {
            runOnUiThread() {
                cardLabelText.text = getString(R.string.tap_card)
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