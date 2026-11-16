package de.mtschirs.eidelweiss

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.nfc.Tag
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.RemoteException
import com.governikus.ausweisapp2.IAusweisApp2Sdk
import com.governikus.ausweisapp2.IAusweisApp2SdkCallback
import org.json.JSONObject

/** Thin AIDL adapter. Does not handle raw APDUs or EAC cryptographic state. */
class AusweisAppSdkBridge(
    private val context: Context,
    private val listener: Listener
) {
    interface Listener {
        fun onSdkSessionEstablished()
        fun onSdkMessage(message: JSONObject)
        fun onSdkDisconnected()
        fun onSdkError(message: String)
    }

    private val main = Handler(Looper.getMainLooper())
    @Volatile private var sdk: IAusweisApp2Sdk? = null
    @Volatile private var sessionId: String? = null
    private var bound = false
    private var closed = false

    private val callback = object : IAusweisApp2SdkCallback.Stub() {
        override fun sessionIdGenerated(pSessionId: String?) {
            main.post {
                if (!closed && !pSessionId.isNullOrEmpty()) {
                    sessionId = pSessionId
                    listener.onSdkSessionEstablished()
                }
            }
        }

        override fun receive(pJson: String?) {
            main.post {
                if (closed || pJson == null) return@post
                val value = runCatching { JSONObject(pJson) }.getOrNull()
                if (value != null) listener.onSdkMessage(value)
                else listener.onSdkError("SDK returned an invalid JSON message")
            }
        }

        override fun sdkDisconnected() {
            main.post {
                if (!closed) {
                    sessionId = null
                    listener.onSdkDisconnected()
                }
            }
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            if (closed) return
            sdk = IAusweisApp2Sdk.Stub.asInterface(binder)
            try {
                if (sdk?.connectSdk(callback) != true) {
                    listener.onSdkError("Cannot establish AusweisApp SDK session")
                }
            } catch (_: RemoteException) {
                listener.onSdkError("AusweisApp SDK IPC failure")
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            sdk = null
            sessionId = null
            if (!closed) listener.onSdkDisconnected()
        }

        override fun onBindingDied(name: ComponentName?) {
            sdk = null
            sessionId = null
            if (!closed) listener.onSdkDisconnected()
        }

        override fun onNullBinding(name: ComponentName?) {
            listener.onSdkError("AusweisApp SDK service returned a null binder")
        }
    }

    fun bind() {
        if (bound || closed) return
        val intent = Intent("com.governikus.ausweisapp2.START_SERVICE")
            .setPackage(context.packageName)
        try {
            bound = context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
            if (!bound) listener.onSdkError("AusweisApp SDK service not available")
        } catch (_: SecurityException) {
            listener.onSdkError("AusweisApp SDK service binding denied")
        }
    }

    val isConnected: Boolean get() = sdk != null && !sessionId.isNullOrEmpty()

    fun send(payload: JSONObject): Boolean {
        val message = payload.toString().toCharArray()
        return transmit(message)
    }

    /** Secrets are passed through char[] (AIDL transmit) and buffers are wiped afterwards. */
    fun sendSecret(command: String, secret: CharArray): Boolean {
        require(command in setOf("SET_PIN", "SET_CAN", "SET_PUK"))
        val required = if (command == "SET_PUK") 10 else 6
        if (secret.size != required || secret.any { it !in '0'..'9' }) {
            secret.fill('\u0000')
            return false
        }
        val prefix = ("{\"cmd\":\"" + command + "\",\"value\":\"").toCharArray()
        val suffix = "\"}".toCharArray()
        val bytes = CharArray(prefix.size + secret.size + suffix.size)
        prefix.copyInto(bytes)
        secret.copyInto(bytes, prefix.size)
        suffix.copyInto(bytes, prefix.size + secret.size)
        secret.fill('\u0000')
        return transmit(bytes)
    }

    private fun transmit(chars: CharArray): Boolean {
        try {
            val target = sdk ?: return false
            val id = sessionId ?: return false
            return target.transmit(id, chars)
        } catch (_: RemoteException) {
            main.post { if (!closed) listener.onSdkError("AusweisApp SDK command failed") }
            return false
        } finally {
            chars.fill('\u0000')
        }
    }

    fun forwardTag(tag: Tag): Boolean {
        try {
            val target = sdk ?: return false
            val id = sessionId ?: return false
            return target.updateNfcTag(id, tag)
        } catch (_: RemoteException) {
            main.post { if (!closed) listener.onSdkError("NFC dispatch to SDK failed") }
            return false
        }
    }

    fun close() {
        closed = true
        if (bound) {
            context.unbindService(serviceConnection)
            bound = false
        }
        sdk = null
        sessionId = null
    }
}
