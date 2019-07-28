package ru.ermolnik.medication.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.util.Scanner;

public class AsyncHttpURLConnection {
  private static final int HTTP_TIMEOUT_MS = 8000;
  private static final String HTTP_ORIGIN = "https://appr.tc";
  private final String method;
  private final String url;
  private final String message;
  private final AsyncHttpEvents events;
  private String contentType;

  public interface AsyncHttpEvents {
    void onHttpError(String errorMessage);
    void onHttpComplete(String response);
  }

  public AsyncHttpURLConnection(String method, String url, String message, AsyncHttpEvents events) {
    this.method = method;
    this.url = url;
    this.message = message;
    this.events = events;
  }

  public void setContentType(String contentType) {
    this.contentType = contentType;
  }

  public void send() {
    new Thread(this ::sendHttpMessage).start();
  }

  private void sendHttpMessage() {
    try {
      HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
      byte[] postData = new byte[0];
      if (message != null) {
        postData = message.getBytes("UTF-8");
      }
      connection.setRequestMethod(method);
      connection.setUseCaches(false);
      connection.setDoInput(true);
      connection.setConnectTimeout(HTTP_TIMEOUT_MS);
      connection.setReadTimeout(HTTP_TIMEOUT_MS);
      connection.addRequestProperty("origin", HTTP_ORIGIN);
      boolean doOutput = false;
      if (method.equals("POST")) {
        doOutput = true;
        connection.setDoOutput(true);
        connection.setFixedLengthStreamingMode(postData.length);
      }
      if (contentType == null) {
        connection.setRequestProperty("Content-Type", "text/plain; charset=utf-8");
      } else {
        connection.setRequestProperty("Content-Type", contentType);
      }

      // Send POST request.
      if (doOutput && postData.length > 0) {
        OutputStream outStream = connection.getOutputStream();
        outStream.write(postData);
        outStream.close();
      }

      // Get response.
      int responseCode = connection.getResponseCode();
      if (responseCode != 200) {
        events.onHttpError("Non-200 response to " + method + " to URL: " + url + " : "
            + connection.getHeaderField(null));
        connection.disconnect();
        return;
      }
      InputStream responseStream = connection.getInputStream();
      String response = drainStream(responseStream);
      responseStream.close();
      connection.disconnect();
      events.onHttpComplete(response);
    } catch (SocketTimeoutException e) {
      events.onHttpError("HTTP " + method + " to " + url + " timeout");
    } catch (IOException e) {
      events.onHttpError("HTTP " + method + " to " + url + " error: " + e.getMessage());
    }
  }

  private static String drainStream(InputStream in) {
    Scanner s = new Scanner(in, "UTF-8").useDelimiter("\\A");
    return s.hasNext() ? s.next() : "";
  }
}
