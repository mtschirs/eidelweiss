package de.mtschirs.eidelweiss

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private val ui = Handler(Looper.getMainLooper())
    private var adapter: NfcAdapter? = null
    private var observing = false
    private var scenarios = emptyList<ScenarioReport>()
    private val observations = mutableListOf<String>()
    private lateinit var output: TextView
    private lateinit var observeButton: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }
        val title = TextView(this).apply {
            text = "Eidelweiss · EAC Diagnostics"
            textSize = 22f
            gravity = Gravity.CENTER_HORIZONTAL
        }
        stack.addView(title)
        stack.addView(TextView(this).apply {
            text = "Offline EAC control-flow simulations and passive NFC discovery. " +
                "No APDU exchange, network calls or authentication fault injection."
            textSize = 15f
            setPadding(0, 20, 0, 20)
        })
        fun button(label: String, action: (View) -> Unit): Button =
            Button(this).apply { text = label; setOnClickListener(action) }
        stack.addView(button("Run offline fault matrix") {
            scenarios = OfflineMatrix.run()
            val traces = scenarios.joinToString("\n\n") { s ->
                val outcomes = s.transitions.joinToString(" → ") {
                    "${it.event.name}:${if (it.accepted) "OK" else "BLOCK"}"
                }
                "${s.name} (${s.policy}):\n$outcomes\nSynthetic anomaly: ${s.anomaly}"
            }
            output.text = "SYNTHETIC MODELS — not real chip evidence\n\n$traces"
        })
        observeButton = button("Observe NFC (off)") {
            observing = !observing
            syncNfc()
        }
        stack.addView(observeButton)
        stack.addView(button("Share JSON report") {
            val json = DiagnosticReport.toJson(scenarios, observations.toList())
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
            }, "Share redacted Eidelweiss report"))
        })
        output = TextView(this).apply {
            text = "Run offline scenarios or enable passive NFC discovery."
            textSize = 14f
            setPadding(0, 24, 0, 24)
        }
        stack.addView(output)
        setContentView(ScrollView(this).apply { addView(stack) })
    }

    override fun onResume() {
        super.onResume()
        syncNfc()
    }

    override fun onPause() {
        adapter?.disableReaderMode(this)
        super.onPause()
    }

    private fun syncNfc() {
        observeButton.text = if (observing) "Observe NFC (on)" else "Observe NFC (off)"
        val nfc = adapter
        if (nfc == null) {
            observing = false
            output.text = "No NFC adapter is present."
            return
        }
        if (!observing) {
            nfc.disableReaderMode(this)
            return
        }
        // No tag connection and no IsoDep.transceive: observation only.
        nfc.enableReaderMode(this,
            this,
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null)
    }

    override fun onTagDiscovered(tag: Tag) {
        val iso = IsoDep.get(tag)
        val summary = if (iso == null) {
            "NFC discovery: tag is not ISO-DEP"
        } else {
            "ISO-DEP detected; maxTransceiveLength=${iso.maxTransceiveLength}; " +
                "historicalBytesPresent=${iso.historicalBytes != null}; " +
                "hiLayerResponsePresent=${iso.hiLayerResponse != null}"
        }
        // No UID, historical bytes, tag payload or cryptographic data retained.
        ui.post {
            if (observing) {
                if (observations.size >= 100) observations.removeAt(0)
                observations += summary
                output.text = "NFC discovery: $summary\n\nNo APDU sent."
            }
        }
    }
}
