package me.phh.dialer

import android.annotation.SuppressLint
import android.media.*
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService
import android.util.Log
import kotlin.concurrent.thread
import kotlin.math.sin

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
            thread {
                val am = getSystemService(AudioManager::class.java)

                val pfd = resources.assets.openFd("rick.webm")
                val extractor = MediaExtractor()
                extractor.setDataSource(pfd)
                val format = extractor.getTrackFormat(0)
                val mime = format.getString(MediaFormat.KEY_MIME)!!
                extractor.selectTrack(0)

                val decoder = MediaCodec.createDecoderByType(mime)
                decoder.configure(format, null, null, 0)
                decoder.start()
                val inBuffers = decoder.getInputBuffers()
                var outBuffers = decoder.getOutputBuffers()

                val devices = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
                val telephonyOut = devices.find { it.type == AudioDeviceInfo.TYPE_TELEPHONY }
                    ?: return@thread
                val playbackTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                            .build())
                    .setAudioFormat(AudioFormat.Builder()
                        //.setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(16000)
                        //oformat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                        .setSampleRate(format.getInteger(MediaFormat.KEY_SAMPLE_RATE))
                        .build())
                    .build()
                playbackTrack.setPreferredDevice(telephonyOut)


                playbackTrack.play()
                Log.d("PHH-Call", "Requesting sample rate ${format.getInteger(MediaFormat.KEY_SAMPLE_RATE)}, got ${playbackTrack.sampleRate}")

                var sawInputEOS = false
                var sawOutputEOS = false
                while(true) {
                    val inputBufIndex = decoder.dequeueInputBuffer(50000)
                    if (inputBufIndex >= 0) {
                        val dstBuf = inBuffers[inputBufIndex]

                        var sampleSize = extractor.readSampleData(dstBuf, 0);
                        var presentationTimeUs = 0L
                        if (sampleSize < 0) {
                            sawInputEOS = true
                            sampleSize = 0
                        } else {
                            presentationTimeUs = extractor.getSampleTime()
                        }

                        decoder.queueInputBuffer(
                            inputBufIndex,
                            0, //offset
                            sampleSize,
                            presentationTimeUs,
                            if(sawInputEOS) MediaCodec.BUFFER_FLAG_END_OF_STREAM else 0);
                        if (!sawInputEOS) {
                            extractor.advance();
                        }
                    }

                    val info = MediaCodec.BufferInfo()
                    val res = decoder.dequeueOutputBuffer(info, 50000)
                    if (res >= 0) {
                        val outputBufIndex = res
                        val buf = outBuffers[outputBufIndex]

                        val chunk = ByteArray(info.size)
                        buf.get(chunk); // Read the buffer all at once
                        buf.clear(); // ** MUST DO!!! OTHERWISE THE NEXT TIME YOU GET THIS SAME BUFFER BAD THINGS WILL HAPPEN

                        if (chunk.size > 0) {
                            playbackTrack.write(chunk, 0, chunk.size);
                        }
                        decoder.releaseOutputBuffer(outputBufIndex, false /* render */);

                        if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true;
                        }
                    } else if (res == MediaCodec.INFO_OUTPUT_BUFFERS_CHANGED) {
                        outBuffers = decoder.getOutputBuffers();
                    } else if (res == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val oformat = decoder.getOutputFormat();
                        Log.d("PHH-Call", "Output format has changed to " + oformat);
                        playbackTrack.setPlaybackRate(oformat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
                    }

                    if(sawOutputEOS) break
                }

                /*
                for(i in 1..4) {
                    val buf = ShortArray(16000)
                    for(j in 0 until 16000) {
                        buf[j] = (10000 * sin(440 * (j/16000.0) * 2 * Math.PI )).toInt().toShort()
                    }
                    playbackTrack.write(buf, 0, buf.size)
                }*/
                playbackTrack.stop()
                playbackTrack.release()
            }
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