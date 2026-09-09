package com.radwan.nova.utils

import android.content.Context
import android.media.AudioManager
import android.util.Log
import org.webrtc.*
import java.util.Collections

object WebRtcAudioCallManager {
    private const val TAG = "WebRtcAudioCall"

    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null
    private var localAudioSource: AudioSource? = null
    private var localAudioTrack: AudioTrack? = null
    private var audioManager: AudioManager? = null

    private var isInitialized = false
    var isMuted: Boolean = false
        private set
    var isSpeakerOn: Boolean = false
        private set

    fun init(context: Context) {
        if (isInitialized) return
        try {
            val options = PeerConnectionFactory.InitializationOptions.builder(context.applicationContext)
                .setEnableInternalTracer(true)
                .createInitializationOptions()
            PeerConnectionFactory.initialize(options)

            peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(PeerConnectionFactory.Options())
                .createPeerConnectionFactory()

            audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            isInitialized = true
            Log.d(TAG, "WebRTC Audio Manager initialized")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize WebRTC", e)
        }
    }

    fun startCall(context: Context, isCaller: Boolean, onSignalToSend: (String) -> Unit) {
        init(context)
        audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager?.isSpeakerphoneOn = isSpeakerOn

        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        // إنشاء مسار صوت الميكروفون
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        localAudioSource = peerConnectionFactory?.createAudioSource(audioConstraints)
        localAudioTrack = peerConnectionFactory?.createAudioTrack("ARDAMSa0", localAudioSource)
        localAudioTrack?.setEnabled(true)

        val iceServers = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer()
        )

        val rtcConfig = PeerConnection.RTCConfiguration(iceServers).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = peerConnectionFactory?.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {
                Log.d(TAG, "ICE Connection State: $state")
            }
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onIceCandidate(candidate: IceCandidate?) {
                candidate?.let {
                    val sdp = "${it.sdpMid}|${it.sdpMLineIndex}|${it.sdp}"
                    onSignalToSend("[ICE]$sdp")
                }
            }
            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onAddStream(stream: MediaStream?) {}
            override fun onRemoveStream(stream: MediaStream?) {}
            override fun onDataChannel(channel: DataChannel?) {}
            override fun onRenegotiationNeeded() {}
            override fun onAddTrack(receiver: RtpReceiver?, streams: Array<out MediaStream>?) {
                Log.d(TAG, "Remote Audio Track connected and streaming live!")
            }
        })

        peerConnection?.addTrack(localAudioTrack, Collections.singletonList("ARDAMS"))

        if (isCaller) {
            peerConnection?.createOffer(object : SdpObserver {
                override fun onCreateSuccess(desc: SessionDescription?) {
                    desc?.let {
                        peerConnection?.setLocalDescription(this, it)
                        onSignalToSend("[OFFER]${it.description}")
                    }
                }
                override fun onSetSuccess() {}
                override fun onCreateFailure(p0: String?) {}
                override fun onSetFailure(p0: String?) {}
            }, constraints)
        }
    }

    fun handleRemoteSignal(signal: String, onSignalToSend: (String) -> Unit) {
        val constraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", "false"))
        }

        when {
            signal.startsWith("[OFFER]") -> {
                val sdp = signal.removePrefix("[OFFER]")
                val desc = SessionDescription(SessionDescription.Type.OFFER, sdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        peerConnection?.createAnswer(object : SdpObserver {
                            override fun onCreateSuccess(ansDesc: SessionDescription?) {
                                ansDesc?.let {
                                    peerConnection?.setLocalDescription(this, it)
                                    onSignalToSend("[ANSWER]${it.description}")
                                }
                            }
                            override fun onSetSuccess() {}
                            override fun onCreateFailure(p0: String?) {}
                            override fun onSetFailure(p0: String?) {}
                        }, constraints)
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, desc)
            }
            signal.startsWith("[ANSWER]") -> {
                val sdp = signal.removePrefix("[ANSWER]")
                val desc = SessionDescription(SessionDescription.Type.ANSWER, sdp)
                peerConnection?.setRemoteDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        Log.d(TAG, "Remote Answer set successfully!")
                    }
                    override fun onCreateFailure(p0: String?) {}
                    override fun onSetFailure(p0: String?) {}
                }, desc)
            }
            signal.startsWith("[ICE]") -> {
                val parts = signal.removePrefix("[ICE]").split("|")
                if (parts.size >= 3) {
                    val candidate = IceCandidate(parts[0], parts[1].toIntOrNull() ?: 0, parts[2])
                    peerConnection?.addIceCandidate(candidate)
                }
            }
        }
    }

    fun toggleMute(muted: Boolean) {
        isMuted = muted
        localAudioTrack?.setEnabled(!muted)
    }

    fun toggleSpeaker(context: Context, speaker: Boolean) {
        isSpeakerOn = speaker
        audioManager?.isSpeakerphoneOn = speaker
    }

    fun endCall(context: Context) {
        try {
            peerConnection?.close()
            peerConnection = null

            localAudioTrack?.dispose()
            localAudioTrack = null

            localAudioSource?.dispose()
            localAudioSource = null

            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
            Log.d(TAG, "Call ended and WebRTC resources released")
        } catch (e: Exception) {
            Log.e(TAG, "Error ending call", e)
        }
    }
}
