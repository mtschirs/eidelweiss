package de.mtschirs.eidelweiss

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.res.Configuration
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputFilter
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Spinner
import android.widget.ArrayAdapter
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/**
 * Research companion for a NORMAL Governikus self-disclosure.
 *
 * Real EAC always runs inside the unmodified published SDK. No APDU interception,
 * credential/SM reuse, unattended consent, or fault injection is implemented.
 */
class MainActivity : Activity(), NfcAdapter.ReaderCallback {
    private val ui = Handler(Looper.getMainLooper())
    private var adapter: NfcAdapter? = null
    private lateinit var sdkBridge: AusweisAppSdkBridge
    private lateinit var output: TextView
    private lateinit var sdkButton: Button
    private lateinit var startButton: Button
    private lateinit var cancelButton: Button
    private lateinit var continueButton: Button
    private lateinit var observeButton: Button
    private lateinit var resultButton: Button
    private lateinit var labFaultSelection: Spinner
    private lateinit var labSingleButton: Button
    private lateinit var labMatrixButton: Button
    private lateinit var labTraceButton: Button
    private var resumed = false
    @Volatile private var observing = false
    @Volatile private var authenticating = false
    private var sdkReady = false
    private var apiLevelRequested: Int? = null
    private var rights: JSONObject? = null
    private var activeDialog: AlertDialog? = null
    private var certificateShown = false
    private var rightsShown = false
    private var nfcForwarded = false
    private var startedAtUtc = ""
    private var resultUrl: String? = null
    private val sdkEvidence = SdkEvidenceRecorder()
    private var scenarios = emptyList<ScenarioReport>()
    private val labTrials = mutableListOf<LabTrial>()
    private val observations = mutableListOf<String>()
    private val authHistory = mutableListOf<AuthRunSummary>()
    private val statusLines = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = NfcAdapter.getDefaultAdapter(this)
        sdkBridge = AusweisAppSdkBridge(this, object : AusweisAppSdkBridge.Listener {
            override fun onSdkSessionEstablished() {
                status("AusweisApp-SDK verbunden; API-Level wird abgefragt.")
                sdkBridge.send(SelfAuthProtocol.command("GET_API_LEVEL"))
            }

            override fun onSdkMessage(message: JSONObject) {
                handleSdkMessage(message)
            }

            override fun onSdkDisconnected() {
                sdkReady = false
                apiLevelRequested = null
                if (authenticating) {
                    sdkEvidence.mark(SdkEvidenceEvent.SDK_DISCONNECTED, SdkEvidenceSource.SDK_CALLBACK)
                    finishLocally("sdk_disconnected")
                }
                status("AusweisApp-SDK-Verbindung beendet.")
                updateButtons()
            }

            override fun onSdkError(message: String) {
                sdkEvidence.mark(SdkEvidenceEvent.SDK_ERROR, SdkEvidenceSource.SDK_CALLBACK)
                status(message)
                updateButtons()
            }
        })
        buildUi()
        updateButtons()
        sdkBridge.bind()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        syncNfc()
    }

    override fun onPause() {
        resumed = false
        runCatching { adapter?.disableReaderMode(this) }
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        // Preserve SDK session and user consent dialog on rotation.
    }

    override fun onDestroy() {
        closeDialog()
        sdkBridge.close()
        super.onDestroy()
    }

    private fun buildUi() {
        val stack = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
        }
        stack.addView(TextView(this).apply {
            text = "Eidelweiss · EAC Diagnostics"
            textSize = 22f
            gravity = Gravity.CENTER_HORIZONTAL
        })
        stack.addView(TextView(this).apply {
            text = "Reguläre AusweisApp-Selbstauskunft mit unverändertem SDK. " +
                "NFC, EAC und Netzwerkverbindung übernimmt Governikus. " +
                "Offline-Fehlermodelle bleiben davon getrennt."
            setPadding(0, 20, 0, 24)
            textSize = 14f
        })

        sdkButton = action("SDK verbinden") {
            sdkBridge.bind()
            status("SDK-Verbindung angefordert.")
        }.also(stack::addView)
        startButton = action("Selbstauskunft starten") { startNormalSelfDisclosure() }
            .also(stack::addView)
        cancelButton = action("Authentisierung abbrechen") {
            if (authenticating) {
                sdkEvidence.mark(SdkEvidenceEvent.USER_CANCEL_REQUESTED, SdkEvidenceSource.UI_ACTION)
                sdkBridge.send(SelfAuthProtocol.command("CANCEL"))
                status("Abbruch an AusweisApp-SDK gesendet.")
            }
        }.also(stack::addView)
        continueButton = action("Nach Pause fortsetzen") {
            sdkEvidence.mark(SdkEvidenceEvent.USER_CONTINUE_REQUESTED, SdkEvidenceSource.UI_ACTION)
            sdkBridge.send(SelfAuthProtocol.command("CONTINUE"))
            continueButton.visibility = View.GONE
        }.also(stack::addView)
        resultButton = action("Selbstauskunft-Ergebnis im Browser öffnen") {
            val url = resultUrl ?: return@action
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.also(stack::addView)

        stack.addView(TextView(this).apply {
            text = "Diagnose ohne Kartenzugriff"
            textSize = 17f
            setPadding(0, 24, 0, 10)
        })
        observeButton = action("NFC passiv beobachten") {
            if (!authenticating) {
                observing = !observing
                syncNfc()
                updateButtons()
            }
        }.also(stack::addView)
        stack.addView(action("Offline-EAC-Fehlermatrix ausführen") {
            scenarios = OfflineMatrix.run()
            scenarios.forEach { scenario ->
                status("Offline ${scenario.name}: ${if (scenario.anomaly) "Modell-Fortsetzung" else "keine Modell-Fortsetzung"}")
            }
        })
        stack.addView(TextView(this).apply {
            text = "EAC2-Fehlerinjektion / zweite TA — nur Offline-Labor"
            textSize = 17f
            setPadding(0, 24, 0, 10)
        })
        stack.addView(TextView(this).apply {
            text = "Achtung: Die Testfälle erzeugen keine APDUs oder Signaturen. " +
                "Eine neue Servertransaktion und die fortbestehende PACE-Sitzung " +
                "werden ausschließlich simuliert."
            textSize = 13f
            setPadding(0, 0, 0, 12)
        })
        labFaultSelection = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                LabFaultPoint.entries.map { it.displayName }
            )
        }
        stack.addView(labFaultSelection)
        labSingleButton = action("Labor: Fehler injizieren und zweite TA simulieren") {
            if (!authenticating) {
                val fault = LabFaultPoint.entries[labFaultSelection.selectedItemPosition]
                val trial = Eac2FaultLab.run(fault)
                recordLabTrial(trial)
            }
        }.also(stack::addView)
        labMatrixButton = action("Labor: gesamte EAC2-/TA-Matrix") {
            if (!authenticating) {
                val matrix = Eac2FaultLab.runMatrix()
                matrix.forEach(::addLabTrial)
                val accepted = matrix.count { it.secondTaAccepted }
                status("Offline-Labor: ${matrix.size} Testfälle; zweite TA im Modell " +
                    "$accepted-mal akzeptiert. Kein echter Chipkontakt.")
                updateButtons()
            }
        }.also(stack::addView)
        labTraceButton = action("Labor: letzte TA-Ablaufspur ansehen") {
            if (!authenticating) showLastLabTrace()
        }.also(stack::addView)
        stack.addView(action("Datensparsamen JSON-Bericht teilen") {
            val json = DiagnosticReport.toJson(
                scenarios, observations.toList(), authHistory.toList(), labTrials.toList(),
                sdkEvidence.snapshot()
            )
            startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_TEXT, json)
            }, "Eidelweiss-Bericht teilen"))
        })
        output = TextView(this).apply {
            text = "AusweisApp-SDK wird verbunden."
            textSize = 14f
            setPadding(0, 18, 0, 16)
        }
        stack.addView(output)
        setContentView(ScrollView(this).apply { addView(stack) })
    }

    private fun addLabTrial(trial: LabTrial) {
        if (labTrials.size >= 100) labTrials.removeAt(0)
        labTrials.add(trial)
        updateButtons()
    }

    private fun showLastLabTrace() {
        val trial = labTrials.lastOrNull() ?: return
        val details = buildString {
            appendLine("Ausschließlich synthetische Offline-Ereignisse.")
            appendLine("Keine reale Chip- oder Serversignatur.")
            appendLine()
            appendLine("Fehler: ${trial.fault.displayName}")
            appendLine("Zweites Terminal: ${trial.nextTerminal.name}")
            appendLine("Modellregel: ${trial.chipPolicy.name}")
            appendLine("Erste TA abgeschlossen: ${trial.firstTaSucceeded}")
            appendLine("Zweite TA im Modell akzeptiert: ${trial.secondTaAccepted}")
            appendLine()
            trial.steps.forEach { step ->
                appendLine("${step.index}. ${step.operation} → ${step.result}")
                appendLine("  Servertransaktion=${step.serverTransaction}; " +
                    "Karten-Sitzung=${step.cardSession}; SM=${step.secureMessaging}")
                appendLine("  ${step.note}")
            }
        }
        AlertDialog.Builder(this)
            .setTitle("Offline-EAC2-/TA-Ablaufspur")
            .setMessage(details)
            .setPositiveButton("Schließen", null)
            .show()
    }

    private fun recordLabTrial(trial: LabTrial) {
        addLabTrial(trial)
        val result = if (trial.secondTaAccepted) "im Modell akzeptiert" else "im Modell abgewiesen"
        status("EAC2-Fehler: ${trial.fault.displayName}; " +
            "zweite TA $result; Modell: ${trial.chipPolicy.name}. " +
            "Keine APDU, keine reale TA-Signatur.")
    }

    private fun action(label: String, onClick: (View) -> Unit) =
        Button(this).apply {
            text = label
            setOnClickListener(onClick)
            isAllCaps = false
        }

    private fun startNormalSelfDisclosure() {
        if (!sdkReady || authenticating) return
        if (adapter?.isEnabled != true) {
            status("NFC ist ausgeschaltet oder nicht verfügbar.")
            return
        }
        observing = false
        authenticating = true
        val correlationId = sdkEvidence.begin()
        startedAtUtc = Instant.now().toString()
        status("Evidenzkennung: $correlationId")
        certificateShown = false
        rightsShown = false
        nfcForwarded = false
        rights = null
        resultUrl = null
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        syncNfc()
        updateButtons()
        status("Reguläre Selbstauskunft gestartet. Kein Fehlertest aktiv.")
        if (!sdkBridge.send(SelfAuthProtocol.startCommand())) finishLocally("sdk_not_connected")
    }

    private fun handleSdkMessage(data: JSONObject) {
        sdkEvidence.receivedSdkMessage(SelfAuthProtocol.sdkMessageType(data))
        when (SelfAuthProtocol.sdkMessageType(data)) {
            "API_LEVEL" -> {
                if (apiLevelRequested == null) {
                    val level = SelfAuthProtocol.apiLevelToSelect(data)
                    if (level == null) {
                        status("Kein unterstütztes SDK-API-Level verfügbar.")
                        return
                    }
                    apiLevelRequested = level
                    sdkBridge.send(JSONObject().put("cmd", "SET_API_LEVEL").put("level", level))
                } else {
                    val current = data.optInt("current", -1)
                    if (current != apiLevelRequested || data.has("error")) {
                        status("SDK-API-Level konnte nicht ausgehandelt werden.")
                        return
                    }
                    sdkReady = true
                    status("AusweisApp-SDK bereit (API-Level $current).")
                    updateButtons()
                }
            }
            "AUTH" -> {
                if (SelfAuthProtocol.isFinalAuth(data)) {
                    finishAuthentication(data)
                } else if (data.has("error")) {
                    status("SDK hat die Authentisierung nicht gestartet.")
                    if (authenticating) finishLocally("sdk_rejected_start")
                } else status("Governikus-Authentisierung läuft.")
            }
            "ACCESS_RIGHTS" -> {
                if (!authenticating) return
                rights = data
                status("Berechtigungen empfangen. Berechtigungszertifikat wird geladen.")
                if (!sdkBridge.send(SelfAuthProtocol.command("GET_CERTIFICATE"))) {
                    sdkBridge.send(SelfAuthProtocol.command("CANCEL"))
                    status("Zertifikat konnte nicht angefordert werden; Vorgang abgebrochen.")
                }
            }
            "CERTIFICATE" -> {
                if (authenticating && rights != null) showCertificateAndRights(data)
            }
            "INSERT_CARD" -> status("Bitte Ausweis am NFC-Leser halten.")
            "ENTER_PIN" -> if (authenticating) promptForSecret("PIN", "SET_PIN", 6, data)
            "ENTER_CAN" -> if (authenticating) promptForSecret("CAN", "SET_CAN", 6, data)
            "ENTER_PUK" -> if (authenticating) promptForSecret("PUK", "SET_PUK", 10, data)
            "ENTER_NEW_PIN" -> {
                status("Unerwartete PIN-Änderungsanfrage: Vorgang wird abgebrochen.")
                sdkBridge.send(SelfAuthProtocol.command("CANCEL"))
            }
            "PAUSE" -> {
                continueButton.visibility = View.VISIBLE
                status("SDK pausiert. Voraussetzung prüfen und dann fortsetzen.")
            }
            "STATUS" -> status("SDK-Status wurde aktualisiert.")
            "BAD_STATE" -> status("SDK hat einen Befehl im aktuellen Zustand abgewiesen.")
            "READER_LIST", "READER", "INFO" -> Unit
            else -> Unit // Do not export unknown or sensitive SDK message bodies.
        }
    }

    private fun showCertificateAndRights(certMessage: JSONObject) {
        if (certMessage.has("error")) {
            status("Zertifikatsdaten fehlen; keine Zustimmung möglich.")
            sdkBridge.send(SelfAuthProtocol.command("CANCEL"))
            return
        }
        val description = certMessage.optJSONObject("description")
        val validity = certMessage.optJSONObject("validity")
        val subject = description?.optString("subjectName").orEmpty()
        val terms = description?.optString("termsOfUsage").orEmpty()
        val expires = validity?.optString("expirationDate").orEmpty()
        val access = rights?.optJSONObject("chat")
        if (subject.isBlank() || terms.isBlank() || expires.isBlank() || access == null) {
            status("Pflichtinformationen zum Berechtigungszertifikat fehlen.")
            sdkBridge.send(SelfAuthProtocol.command("CANCEL"))
            return
        }

        val details = buildString {
            appendLine("Diensteanbieter: $subject")
            appendLine("Aussteller: ${description?.optString("issuerName").orEmpty()}")
            appendLine("Zweck: ${description?.optString("purpose").orEmpty()}")
            appendLine("Zertifikat gültig bis: $expires")
            appendLine()
            appendLine("Pflichtangaben des Diensteanbieters (einschließlich Anschrift, E-Mail und Datenschutzaufsicht):")
            appendLine(terms)
            appendLine()
            appendLine("Erforderliche Datenkategorien:")
            appendLine(joinRights(access.optJSONArray("required")))
            appendLine("Optionale Datenkategorien:")
            appendLine(joinRights(access.optJSONArray("optional")))
            appendLine("Aktuell ausgewählte Datenkategorien:")
            appendLine(joinRights(access.optJSONArray("effective")))
            appendLine()
            appendLine("Nur zustimmen, wenn Diensteanbieter und angeforderte Daten korrekt sind.")
        }
        closeDialog()
        val view = ScrollView(this).apply {
            addView(TextView(this@MainActivity).apply {
                text = details
                textSize = 15f
                setPadding(28, 20, 28, 20)
            })
        }
        certificateShown = true
        rightsShown = true
        sdkEvidence.mark(SdkEvidenceEvent.CERTIFICATE_AND_RIGHTS_DISPLAYED, SdkEvidenceSource.UI_ACTION)
        activeDialog = AlertDialog.Builder(this)
            .setTitle("Berechtigungszertifikat und Freigabe")
            .setView(view)
            .setCancelable(false)
            .setPositiveButton("Daten freigeben") { _, _ ->
                activeDialog = null
                rights = null
                sdkEvidence.mark(SdkEvidenceEvent.USER_CONSENT_ACCEPTED, SdkEvidenceSource.UI_ACTION)
                sdkBridge.send(SelfAuthProtocol.command("ACCEPT"))
                status("Zugriffsrechte ausdrücklich bestätigt.")
            }
            .setNegativeButton("Abbrechen") { _, _ ->
                activeDialog = null
                rights = null
                sdkEvidence.mark(SdkEvidenceEvent.USER_CONSENT_REJECTED, SdkEvidenceSource.UI_ACTION)
                sdkBridge.send(SelfAuthProtocol.command("CANCEL"))
                status("Zugriffsrechte abgelehnt.")
            }
            .create()
        activeDialog?.show()
    }

    private fun joinRights(values: JSONArray?): String {
        if (values == null || values.length() == 0) return "Keine"
        return (0 until values.length()).map { values.optString(it) }.joinToString(", ")
    }

    private fun promptForSecret(title: String, command: String, length: Int, data: JSONObject) {
        closeDialog()
        val retryCounter = data.optJSONObject("reader")?.optJSONObject("card")
            ?.optInt("retryCounter", -1) ?: -1
        val prompt = when (title) {
            "PIN" -> "Sechsstellige Ausweis-PIN eingeben. Falsche Eingaben vermindern den Versuchszähler."
            "CAN" -> "Sechsstellige Kartenzugangsnummer eingeben. Nur bei bekannter Nummer fortfahren."
            else -> "Zehnstellige PUK nur zum Entsperren eines blockierten Ausweises eingeben."
        } + if (retryCounter >= 0) "\nVerbleibende PIN-Versuche: $retryCounter" else ""
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 20, 28, 12)
        }
        container.addView(TextView(this).apply { text = prompt })
        val secret = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            filters = arrayOf(InputFilter.LengthFilter(length))
            isSingleLine = true
            setFilterTouchesWhenObscured(true)
        }
        container.addView(secret)
        val dialog = AlertDialog.Builder(this)
            .setTitle("$title erforderlich")
            .setView(container)
            .setCancelable(false)
            .setPositiveButton("Sicher übermitteln", null)
            .setNegativeButton("Abbrechen") { _, _ ->
                secret.text.clear()
                activeDialog = null
                sdkBridge.send(SelfAuthProtocol.command("CANCEL"))
            }.create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val chars = CharArray(secret.text.length) { index -> secret.text[index] }
                secret.text.clear()
                if (chars.size != length || chars.any { it !in '0'..'9' }) {
                    chars.fill('\u0000')
                    secret.error = "Genau $length Ziffern erforderlich"
                } else {
                    val sent = sdkBridge.sendSecret(command, chars)
                    if (sent) {
                        val marker = when (command) {
                            "SET_PIN" -> SdkEvidenceEvent.PIN_SUBMITTED
                            "SET_CAN" -> SdkEvidenceEvent.CAN_SUBMITTED
                            else -> SdkEvidenceEvent.PUK_SUBMITTED
                        }
                        sdkEvidence.mark(marker, SdkEvidenceSource.UI_ACTION)
                        status("$title an AusweisApp-SDK übergeben (Wert nicht gespeichert).")
                        activeDialog = null
                        dialog.dismiss()
                    } else {
                        status("Sichere Übermittlung nicht möglich; Vorgang abbrechen.")
                    }
                }
            }
        }
        activeDialog = dialog
        dialog.show()
    }

    private fun finishAuthentication(message: JSONObject) {
        sdkEvidence.mark(
            if (SelfAuthProtocol.safeOutcome(message) == "ok") SdkEvidenceEvent.AUTH_FINAL_OK
            else SdkEvidenceEvent.AUTH_FINAL_ERROR,
            SdkEvidenceSource.SDK_CALLBACK
        )
        sdkEvidence.end()
        closeDialog()
        authHistory += SelfAuthProtocol.summarize(
            message, startedAtUtc, certificateShown, rightsShown, nfcForwarded
        )
        if (authHistory.size > 20) authHistory.removeAt(0)
        resultUrl = if (SelfAuthProtocol.safeOutcome(message) == "ok")
            SelfAuthProtocol.validatedResultUrl(message) else null
        authenticating = false
        rights = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        status("Authentisierung: ${SelfAuthProtocol.safeOutcome(message)}. " +
            "Das Ergebnis kann gegebenenfalls separat im Browser angezeigt werden.")
        syncNfc()
        updateButtons()
    }

    private fun finishLocally(reason: String) {
        sdkEvidence.mark(SdkEvidenceEvent.LOCAL_ABORT, SdkEvidenceSource.APP_DISPATCH)
        sdkEvidence.end()
        closeDialog()
        authHistory += AuthRunSummary(
            startedAtUtc, Instant.now().toString(),
            "UNMODIFIED_AUSWEISAPP_SDK", "incomplete", reason,
            certificateShown, rightsShown, nfcForwarded
        )
        if (authHistory.size > 20) authHistory.removeAt(0)
        authenticating = false
        rights = null
        window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        syncNfc()
        updateButtons()
    }

    private fun closeDialog() {
        activeDialog?.dismiss()
        activeDialog = null
    }

    private fun status(message: String) {
        statusLines += message
        if (statusLines.size > 20) statusLines.removeAt(0)
        output.text = statusLines.joinToString("\n\n")
    }

    private fun updateButtons() {
        sdkButton.isEnabled = !sdkReady
        startButton.isEnabled = sdkReady && !authenticating
        cancelButton.isEnabled = authenticating
        continueButton.visibility = View.GONE
        resultButton.isEnabled = resultUrl != null
        observeButton.isEnabled = !authenticating
        labFaultSelection.isEnabled = !authenticating
        labSingleButton.isEnabled = !authenticating
        labMatrixButton.isEnabled = !authenticating
        labTraceButton.isEnabled = !authenticating && labTrials.isNotEmpty()
        observeButton.text = if (observing) "Passive NFC-Beobachtung beenden"
            else "NFC passiv beobachten"
    }

    private fun syncNfc() {
        if (!resumed) return
        val nfc = adapter ?: return
        runCatching {
            if (authenticating || observing) {
                nfc.enableReaderMode(
                    this, this,
                    NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_NFC_B or
                        NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
                    null
                )
            } else {
                nfc.disableReaderMode(this)
            }
        }.onFailure { status("NFC-Lesemodus konnte nicht aktiviert werden.") }
    }

    override fun onTagDiscovered(tag: Tag) {
        val iso = IsoDep.get(tag)
        val compatible = iso != null && iso.isExtendedLengthApduSupported
        ui.post {
            if (!resumed) return@post
            if (authenticating) {
                if (!compatible) {
                    status("Karte/Gerät bietet keine erforderliche ISO-DEP-Extended-Length-Unterstützung.")
                } else if (sdkBridge.forwardTag(tag)) {
                    sdkEvidence.mark(SdkEvidenceEvent.NFC_TAG_DISPATCHED, SdkEvidenceSource.APP_DISPATCH)
                    nfcForwarded = true
                    status("ISO-DEP-Tag an unveränderte AusweisApp-SDK-Engine übergeben.")
                } else {
                    status("NFC-Tag konnte nicht an AusweisApp-SDK übergeben werden.")
                }
            } else if (observing) {
                val msg = if (iso == null) "NFC: kein ISO-DEP"
                    else "ISO-DEP erkannt; extendedLength=$compatible; maxTransceiveLength=${iso.maxTransceiveLength}"
                if (observations.size >= 100) observations.removeAt(0)
                observations += msg
                status(msg + " (keine APDU übertragen)")
            }
        }
    }
}
