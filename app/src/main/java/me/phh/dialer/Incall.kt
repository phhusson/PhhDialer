package me.phh.dialer

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import java.nio.ByteBuffer
import kotlin.concurrent.thread

class Incall : InCallService() {
    val handler = Handler(HandlerThread("IncallService").also { it.start() }.looper)
    @SuppressLint("MissingPermission")
    override fun onCallAdded(call: Call) {
        call.registerCallback(object: Call.Callback() {
            override fun onCannedTextResponsesLoaded(
                call: Call?,
                cannedTextResponses: MutableList<String>?
            ) {
                Log.d("PHH-Call", "Proposed refusal list ${cannedTextResponses}")
            }

            override fun onStateChanged(call: Call?, state: Int) {
                Log.d("PHH-Call", "Call state changed $call $state")
            }
        })
        val details = call.details
        Log.d("PHH-Call", "Got new call")
        Log.d("PHH-Call", "Call from ${details.callerDisplayName}/${details.contactDisplayName} verified status ${details.callerNumberVerificationStatus}")
        Log.d("PHH-Call", "...${details.handle} ${details.handlePresentation}")
        Log.d("PHH-Call", "Proposed refusal list ${call.cannedTextResponses}")


        handler.postDelayed({
            Log.d("PHH-Call", "Enter background audio processing...")
            call.javaClass.getMethod("enterBackgroundAudioProcessing").invoke(call)
            handler.postDelayed({
                thread {
                    /*for(source in listOf(MediaRecorder.AudioSource.VOICE_DOWNLINK, MediaRecorder.AudioSource.VOICE_UPLINK, MediaRecorder.AudioSource.VOICE_CALL, MediaRecorder.AudioSource.VOICE_COMMUNICATION)) {
                        Log.d("PHH-Call", "Try source $source")
                        val recordTrack = AudioRecord(source, 16000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, 4096)
                        Log.d("PHH-Call", "Record $source track status ${recordTrack.state}")
                        recordTrack.startRecording()
                        if(recordTrack.state == AudioRecord.STATE_INITIALIZED) {
                            val buffer = ByteBuffer.allocateDirect(4096)
                            for(i in 1..10) {
                                val nRead = recordTrack.read(buffer, 4096)
                                if(nRead <= 0) {
                                    Log.d("PHH-Call", "Record $source failed with $nRead")
                                    break
                                }
                                Log.d("PHH-Call", "Audio record received size $nRead")
                            }
                        }
                        recordTrack.stop()
                        recordTrack.release()
                    }*/
                    val recorder = MediaRecorder()
                    recorder.setAudioSource(MediaRecorder.AudioSource.VOICE_DOWNLINK)
                    recorder.setOutputFormat(MediaRecorder.OutputFormat.OGG)
                    recorder.setAudioEncoder(MediaRecorder.AudioEncoder.OPUS)
                    recorder.setOutputFile("/sdcard/Music/toto.ogg")
                    recorder.prepare()
                    recorder.start()
                    Thread.sleep(3000)
                    recorder.stop()
                    recorder.release()
                }
            }, 2000L)
            handler.postDelayed({
                call.disconnect()
            }, 10000L)
        }, 5000L)

    }

    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        Log.d("PHH-Call", "Call audio state changed $audioState")
    }

    override fun onCallRemoved(call: Call) {
        Log.d("PHH-Call", "Call removed $call")
    }

    override fun onCanAddCallChanged(canAddCall: Boolean) {
        Log.d("PHH-Call", "Can add call changed $canAddCall")
    }

    override fun onConnectionEvent(call: Call, event: String, extras: Bundle?) {
        Log.d("PHH-Call", "Got connection event $call $event $extras")
    }
}