package me.phh.dialer

import android.app.Activity
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.se.omapi.SEService
import android.telephony.TelephonyManager
import android.util.Log
import com.truphone.lpa.ApduChannel
import com.truphone.lpa.ApduTransmittedListener
import com.truphone.lpa.progress.DownloadProgress
import com.truphone.lpad.progress.Progress
import com.truphone.lpad.progress.ProgressListener
import kotlin.concurrent.thread


class EuiccActivity : Activity() {
    fun hexStringToByteArray(str: String): ByteArray {
        val length = str.length / 2
        val out = ByteArray(length)
        for (i in 0 until length) {
            val i2 = i * 2
            out[i] = str.substring(i2, i2 + 2).toInt(16).toByte()
        }
        return out
    }
    fun byteArrayToHex(arr: ByteArray): String {
        val sb = StringBuilder()
        val length = arr.size
        for (i in 0 until length) {
            sb.append(String.format("%02X", arr[i]))
        }
        return sb.toString()
    }


    var channel = -1
    fun telephonyManagerWay() {
        val tm = getSystemService(TelephonyManager::class.java)

        //tm.javaClass.getMethod("switchSlots", IntArray::class.java).invoke(tm, intArrayOf(0))

        val euiccChannel = tm.iccOpenLogicalChannel("A0000005591010FFFFFFFF8900000100", 0)
        val euiccChannelId = euiccChannel.channel
        Log.d("PHH", "channel is $euiccChannelId ${euiccChannel.status}")
        channel = euiccChannelId

        val truphoneChannel = object: ApduChannel {
            var listener: ApduTransmittedListener? = null
            override fun transmitAPDU(payload: String): String {
                val cla = Integer.parseInt(payload.substring(0, 2), 16)
                val instruction = Integer.parseInt(payload.substring(2, 4), 16)
                val p1 = Integer.parseInt(payload.substring(4, 6), 16)
                val p2 = Integer.parseInt(payload.substring(6, 8), 16)
                val p3 = Integer.parseInt(payload.substring(8, 10), 16)
                val p4 = payload.substring(10)
                return tm.iccTransmitApduLogicalChannel(
                    euiccChannelId,
                    cla, instruction, p1, p2, p3, p4
                )
            }

            override fun transmitAPDUS(p0: MutableList<String>): String {
                Log.d("PHH", "Entering transmit APDU", Exception())
                var ret = ""
                for (pdu in p0) {
                    ret = transmitAPDU(pdu)
                    Log.d("PHH", "Transmit returned $ret")
                }
                return ret
            }

            override fun sendStatus() {
            }

            override fun setApduTransmittedListener(p0: ApduTransmittedListener) {
                listener = p0
            }

            override fun removeApduTransmittedListener(p0: ApduTransmittedListener) {
                listener = null
            }
        }

        val lpa = com.truphone.lpa.impl.LocalProfileAssistantImpl(truphoneChannel)
        /*
        lpa.deleteProfile("98100000002143658709", Progress().also { it.setProgressListener(object: ProgressListener {
            override fun onAction(p0: String?, p1: String?, p2: Double?, p3: String?) {
                Log.d("PHH", "Deleting Profile got action $p0 $p1 $p2 $p3")
            }

        })})*/

        Log.d("PHH", "Asking for EID " + lpa.getEID())
        Log.d("PHH", "Listing profiles " + lpa.getProfiles())
        lpa.downloadProfile("1\$smdp.io\$4Y-1DZCAD-1R0T7S0", DownloadProgress().also { it.setProgressListener(object: ProgressListener {
            override fun onAction(p0: String?, p1: String?, p2: Double?, p3: String?) {
                Log.d("PHH", "Got action $p0 $p1 $p2 $p3")
            }
        })})
    }

    val handler = Handler(HandlerThread("OMAPI").also { it.start() }.looper)

    fun omapiConnected(s: SEService) {
        for(reader in s.readers) {
            Log.d("PHH", "Got a reader " + reader.isSecureElementPresent + ", " + reader.name)
        }
        //val reader = s.getUiccReader(1)
        val reader = s.readers[0]
        Log.d("PHH", "Got a reader " + reader.isSecureElementPresent + ", " + reader.name)
        val session = reader.openSession()
        val appletId = byteArrayOf(-96, 0, 0, 5, 89, 16, 16, -1, -1, -1, -1, -119, 0, 0, 1, 0)
        val channel = session.openLogicalChannel(appletId)
        Log.d("PHH", "Select response " + channel?.selectResponse)
        if(channel == null) return

        val truphoneChannel = object: ApduChannel {
            override fun transmitAPDU(p0: String): String {
                return byteArrayToHex(channel.transmit(hexStringToByteArray(p0)))
            }

            override fun transmitAPDUS(p0: MutableList<String>): String {
                var res = ""
                for(pdu in p0) {
                    res = transmitAPDU(pdu)
                    Log.d("PHH", "Transmit gave $res")
                }
                return res
            }

            override fun sendStatus() {
            }

            override fun setApduTransmittedListener(p0: ApduTransmittedListener?) {
            }

            override fun removeApduTransmittedListener(p0: ApduTransmittedListener?) {
            }
        }

        val lpa = com.truphone.lpa.impl.LocalProfileAssistantImpl(truphoneChannel)
        Log.d("PHH", "Asking for EID " + lpa.getEID())
        Log.d("PHH", "Listing profiles " + lpa.getProfiles())
        // Scan qrcode, and put the only as first parameter of downloadProfile
        /*lpa.downloadProfile(XXXXX, DownloadProgress().also { it.setProgressListener(object: ProgressListener {
            override fun onAction(p0: String?, p1: String?, p2: Double?, p3: String?) {
                Log.d("PHH", "Got action $p0 $p1 $p2 $p3")
            }
        })})*/
        // Insert here ICC ID you got from getProfiles
        lpa.enableProfile(XXXX, Progress().apply {
            setProgressListener(object: ProgressListener {
                override fun onAction(p0: String?, p1: String?, p2: Double?, p3: String?) {
                    Log.d("PHH", "Enable profile $p0 $p1 $p2 $p3")
                }

            })
        })
    }
    fun omapiWay() {
        var s: SEService? = null
        val ses = SEService(this,
            { p0 -> try {
                handler.post(p0)
            } catch(t: Throwable) {
                Log.d("PHH", "ARRR", t)
            } },
            { omapiConnected(s!!) })
        s = ses
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //omapiWay()
        thread {
            /*
            try {
                telephonyManagerWay()
            } finally {
                val tm = getSystemService(TelephonyManager::class.java)
                tm.iccCloseLogicalChannel(channel)
            }*/
            try {
                omapiWay()
            } finally {
            }
        }
    }
}