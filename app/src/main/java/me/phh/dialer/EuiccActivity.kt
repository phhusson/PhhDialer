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


class EuiccActivity : Activity() {
    fun telephonyManagerWay() {

        val tm = getSystemService(TelephonyManager::class.java)

        tm.javaClass.getMethod("switchSlots", IntArray::class.java).invoke(tm, intArrayOf(0))

        val euiccChannel = tm.iccOpenLogicalChannel("A0000005591010FFFFFFFF8900000100", 0)
        val euiccChannelId = euiccChannel.channel
        Log.d("PHH", "channel is $euiccChannelId ${euiccChannel.status}")

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
                var ret = ""
                for (pdu in p0) {
                    ret = transmitAPDU(pdu)
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
        Log.d("PHH", "Asking for EID " + lpa.getEID())
        Log.d("PHH", "Listing profiles " + lpa.getProfiles())
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
        val appletId = byteArrayOf(
            -96,
            0,
            0,
            5,
            89,
            16,
            16,
            -1,
            -1,
            -1,
            -1,
            -119,
            0,
            0,
            1,
            0
        )
        val channel = session.openLogicalChannel(appletId)
        Log.d("PHH", "Select response " + channel?.selectResponse)
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
        telephonyManagerWay()
    }
}