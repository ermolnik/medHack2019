package ru.ermolnik.medication.rtc_client;

import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;
import org.webrtc.SessionDescription;

import java.util.List;

public interface AppRTCClient {
  class RoomConnectionParameters {
    public final String roomUrl;
    public final String roomId;
    public final boolean loopback;
    public final String urlParameters;
    public RoomConnectionParameters(
        String roomUrl, String roomId, boolean loopback, String urlParameters) {
      this.roomUrl = roomUrl;
      this.roomId = roomId;
      this.loopback = loopback;
      this.urlParameters = urlParameters;
    }
    public RoomConnectionParameters(String roomUrl, String roomId, boolean loopback) {
      this(roomUrl, roomId, loopback, null /* urlParameters */);
    }
  }

  void connectToRoom(RoomConnectionParameters connectionParameters);

  void sendOfferSdp(final SessionDescription sdp);

  void sendAnswerSdp(final SessionDescription sdp);

  void sendLocalIceCandidate(final IceCandidate candidate);

  void sendLocalIceCandidateRemovals(final IceCandidate[] candidates);

  void disconnectFromRoom();

  class SignalingParameters {
    public final List<PeerConnection.IceServer> iceServers;
    public final boolean initiator;
    public final String clientId;
    public final String wssUrl;
    public final String wssPostUrl;
    public final SessionDescription offerSdp;
    public final List<IceCandidate> iceCandidates;

    public SignalingParameters(List<PeerConnection.IceServer> iceServers, boolean initiator,
        String clientId, String wssUrl, String wssPostUrl, SessionDescription offerSdp,
        List<IceCandidate> iceCandidates) {
      this.iceServers = iceServers;
      this.initiator = initiator;
      this.clientId = clientId;
      this.wssUrl = wssUrl;
      this.wssPostUrl = wssPostUrl;
      this.offerSdp = offerSdp;
      this.iceCandidates = iceCandidates;
    }
  }

  interface SignalingEvents {
    void onConnectedToRoom(final SignalingParameters params);
    void onRemoteDescription(final SessionDescription sdp);
    void onRemoteIceCandidate(final IceCandidate candidate);
    void onRemoteIceCandidatesRemoved(final IceCandidate[] candidates);
    void onChannelClose();
    void onChannelError(final String description);
  }
}
