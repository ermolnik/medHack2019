package ru.ermolnik.medication.rtc_client;

import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import de.tavendo.autobahn.WebSocket.WebSocketConnectionObserver;
import de.tavendo.autobahn.WebSocketConnection;
import de.tavendo.autobahn.WebSocketException;
import ru.ermolnik.medication.util.AsyncHttpURLConnection;

public class WebSocketChannelClient {
  private static final String TAG = "WSChannelRTCClient";
  private static final int CLOSE_TIMEOUT = 1000;
  private final WebSocketChannelEvents events;
  private final Handler handler;
  private WebSocketConnection ws;
  private String wsServerUrl;
  private String postServerUrl;
  @Nullable
  private String roomID;
  @Nullable
  private String clientID;
  private WebSocketConnectionState state;
  private WebSocketObserver wsObserver;
  private final Object closeEventLock = new Object();
  private boolean closeEvent;
  private final List<String> wsSendQueue = new ArrayList<>();

  public enum WebSocketConnectionState { NEW, CONNECTED, REGISTERED, CLOSED, ERROR }

  public interface WebSocketChannelEvents {
    void onWebSocketMessage(final String message);
    void onWebSocketClose();
    void onWebSocketError(final String description);
  }

  public WebSocketChannelClient(Handler handler, WebSocketChannelEvents events) {
    this.handler = handler;
    this.events = events;
    roomID = null;
    clientID = null;
    state = WebSocketConnectionState.NEW;
  }

  public WebSocketConnectionState getState() {
    return state;
  }

  public void connect(final String wsUrl, final String postUrl) {
    checkIfCalledOnValidThread();
    if (state != WebSocketConnectionState.NEW) {
      Log.e(TAG, "WebSocket is already connected.");
      return;
    }
    wsServerUrl = wsUrl;
    postServerUrl = postUrl;
    closeEvent = false;

    Log.d(TAG, "Connecting WebSocket to: " + wsUrl + ". Post URL: " + postUrl);
    ws = new WebSocketConnection();
    wsObserver = new WebSocketObserver();
    try {
      ws.connect(new URI(wsServerUrl), wsObserver);
    } catch (URISyntaxException e) {
      reportError("URI error: " + e.getMessage());
    } catch (WebSocketException e) {
      reportError("WebSocket connection error: " + e.getMessage());
    }
  }

  public void register(final String roomID, final String clientID) {
    checkIfCalledOnValidThread();
    this.roomID = roomID;
    this.clientID = clientID;
    if (state != WebSocketConnectionState.CONNECTED) {
      Log.w(TAG, "WebSocket register() in state " + state);
      return;
    }
    Log.d(TAG, "Registering WebSocket for room " + roomID + ". ClientID: " + clientID);
    JSONObject json = new JSONObject();
    try {
      json.put("cmd", "register");
      json.put("roomid", roomID);
      json.put("clientid", clientID);
      Log.d(TAG, "C->WSS: " + json.toString());
      ws.sendTextMessage(json.toString());
      state = WebSocketConnectionState.REGISTERED;
      // Send any previously accumulated messages.
      for (String sendMessage : wsSendQueue) {
        send(sendMessage);
      }
      wsSendQueue.clear();
    } catch (JSONException e) {
      reportError("WebSocket register JSON error: " + e.getMessage());
    }
  }

  public void send(String message) {
    checkIfCalledOnValidThread();
    switch (state) {
      case NEW:
      case CONNECTED:
        // Store outgoing messages and send them after websocket client
        // is registered.
        Log.d(TAG, "WS ACC: " + message);
        wsSendQueue.add(message);
        return;
      case ERROR:
      case CLOSED:
        Log.e(TAG, "WebSocket send() in error or closed state : " + message);
        return;
      case REGISTERED:
        JSONObject json = new JSONObject();
        try {
          json.put("cmd", "send");
          json.put("msg", message);
          message = json.toString();
          Log.d(TAG, "C->WSS: " + message);
          ws.sendTextMessage(message);
        } catch (JSONException e) {
          reportError("WebSocket send JSON error: " + e.getMessage());
        }
        break;
    }
  }

  public void post(String message) {
    checkIfCalledOnValidThread();
    sendWSSMessage("POST", message);
  }

  public void disconnect(boolean waitForComplete) {
    checkIfCalledOnValidThread();
    Log.d(TAG, "Disconnect WebSocket. State: " + state);
    if (state == WebSocketConnectionState.REGISTERED) {
      send("{\"type\": \"bye\"}");
      state = WebSocketConnectionState.CONNECTED;
      sendWSSMessage("DELETE", "");
    }
    if (state == WebSocketConnectionState.CONNECTED || state == WebSocketConnectionState.ERROR) {
      ws.disconnect();
      state = WebSocketConnectionState.CLOSED;

      if (waitForComplete) {
        synchronized (closeEventLock) {
          while (!closeEvent) {
            try {
              closeEventLock.wait(CLOSE_TIMEOUT);
              break;
            } catch (InterruptedException e) {
              Log.e(TAG, "Wait error: " + e.toString());
            }
          }
        }
      }
    }
    Log.d(TAG, "Disconnecting WebSocket done.");
  }

  private void reportError(final String errorMessage) {
    Log.e(TAG, errorMessage);
    handler.post(() -> {
      if (state != WebSocketConnectionState.ERROR) {
        state = WebSocketConnectionState.ERROR;
        events.onWebSocketError(errorMessage);
      }
    });
  }

  private void sendWSSMessage(final String method, final String message) {
    String postUrl = postServerUrl + "/" + roomID + "/" + clientID;
    Log.d(TAG, "WS " + method + " : " + postUrl + " : " + message);
    AsyncHttpURLConnection httpConnection =
        new AsyncHttpURLConnection(method, postUrl, message, new AsyncHttpURLConnection.AsyncHttpEvents() {
          @Override
          public void onHttpError(String errorMessage) {
            reportError("WS " + method + " error: " + errorMessage);
          }

          @Override
          public void onHttpComplete(String response) {}
        });
    httpConnection.send();
  }

  private void checkIfCalledOnValidThread() {
    if (Thread.currentThread() != handler.getLooper().getThread()) {
      throw new IllegalStateException("WebSocket method is not called on valid thread");
    }
  }

  private class WebSocketObserver implements WebSocketConnectionObserver {
    @Override
    public void onOpen() {
      Log.d(TAG, "WebSocket connection opened to: " + wsServerUrl);
      handler.post(() -> {
        state = WebSocketConnectionState.CONNECTED;
        if (roomID != null && clientID != null) {
          register(roomID, clientID);
        }
      });
    }

    @Override
    public void onClose(WebSocketCloseNotification code, String reason) {
      Log.d(TAG, "WebSocket connection closed. Code: " + code + ". Reason: " + reason + ". State: "
              + state);
      synchronized (closeEventLock) {
        closeEvent = true;
        closeEventLock.notify();
      }
      handler.post(() -> {
        if (state != WebSocketConnectionState.CLOSED) {
          state = WebSocketConnectionState.CLOSED;
          events.onWebSocketClose();
        }
      });
    }

    @Override
    public void onTextMessage(String payload) {
      Log.d(TAG, "WSS->C: " + payload);
      final String message = payload;
      handler.post(() -> {
        if (state == WebSocketConnectionState.CONNECTED
            || state == WebSocketConnectionState.REGISTERED) {
          events.onWebSocketMessage(message);
        }
      });
    }

    @Override
    public void onRawTextMessage(byte[] payload) {}

    @Override
    public void onBinaryMessage(byte[] payload) {}
  }
}
