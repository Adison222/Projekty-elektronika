package pl.climagold.qc;

import android.graphics.Bitmap;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.json.JSONArray;
import org.json.JSONObject;

/** Optional Azure AI Vision Read v3.2. Never invoked without the user's upload action. */
final class HandwritingOcr {
  static URI endpoint(String value) {
    URI uri = URI.create(value.trim());
    String host = uri.getHost();
    if (!"https".equalsIgnoreCase(uri.getScheme())
        || host == null
        || !host.endsWith(".cognitiveservices.azure.com")
        || uri.getUserInfo() != null
        || uri.getPort() != -1
        || uri.getQuery() != null
        || uri.getFragment() != null
        || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) {
      throw new IllegalArgumentException("Podaj adres https://NAZWA.cognitiveservices.azure.com/");
    }
    return URI.create("https://" + host + "/");
  }

  private static HttpsURLConnection connection(URI uri, String key) throws IOException {
    HttpsURLConnection c = (HttpsURLConnection) uri.toURL().openConnection();
    c.setConnectTimeout(15000);
    c.setReadTimeout(15000);
    c.setInstanceFollowRedirects(false);
    c.setRequestProperty("Ocp-Apim-Subscription-Key", key);
    return c;
  }

  static String read(Bitmap bitmap, URI endpoint, String key) throws Exception {
    byte[] jpeg;
    try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
      if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 95, bytes))
        throw new IOException("Nie przygotowano zdjęcia.");
      jpeg = bytes.toByteArray();
    }
    URI operation;
    HttpsURLConnection post = connection(endpoint.resolve("vision/v3.2/read/analyze"), key);
    try {
      post.setRequestMethod("POST");
      post.setRequestProperty("Content-Type", "application/octet-stream");
      post.setDoOutput(true);
      post.setFixedLengthStreamingMode(jpeg.length);
      try (OutputStream out = post.getOutputStream()) {
        out.write(jpeg);
      }
      int code = post.getResponseCode();
      if (code != 202) throw failure(code);
      String location = post.getHeaderField("Operation-Location");
      if (location == null) throw new IOException("Usługa nie zwróciła adresu wyniku.");
      operation = URI.create(location);
      if (!"https".equals(operation.getScheme())
          || !endpoint.getHost().equals(operation.getHost())
          || operation.getUserInfo() != null
          || operation.getPort() != -1) throw new IOException("Nieprawidłowy adres wyniku OCR.");
    } finally {
      post.disconnect();
    }

    long deadline = android.os.SystemClock.elapsedRealtime() + 60000;
    while (android.os.SystemClock.elapsedRealtime() < deadline) {
      Thread.sleep(1000);
      HttpsURLConnection get = connection(operation, key);
      try {
        int code = get.getResponseCode();
        if (code != 200) throw failure(code);
        JSONObject result;
        try (InputStream in = get.getInputStream();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
          byte[] buffer = new byte[8192];
          int n;
          while ((n = in.read(buffer)) != -1) {
            if (bytes.size() + n > 2_000_000) throw new IOException("Wynik OCR jest zbyt duży.");
            bytes.write(buffer, 0, n);
          }
          result = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
        }
        String state = result.optString("status");
        if ("failed".equals(state))
          throw new IOException("Usługa nie odczytała zdjęcia. Spróbuj wyraźniejszego ujęcia.");
        if ("succeeded".equals(state)) {
          StringBuilder text = new StringBuilder();
          JSONArray pages = result.getJSONObject("analyzeResult").getJSONArray("readResults");
          for (int p = 0; p < pages.length(); p++) {
            JSONArray lines = pages.getJSONObject(p).getJSONArray("lines");
            for (int l = 0; l < lines.length(); l++)
              text.append(lines.getJSONObject(l).getString("text")).append('\n');
          }
          return text.toString().trim();
        }
        if (!"running".equals(state) && !"notStarted".equals(state))
          throw new IOException("Nieznany status rozpoznawania.");
      } finally {
        get.disconnect();
      }
    }
    throw new IOException("Przekroczono czas oczekiwania. Spróbuj ponownie później.");
  }

  private static IOException failure(int status) {
    if (status == 401 || status == 403)
      return new IOException("Sprawdź klucz i adres usługi Azure AI Vision.");
    if (status == 429) return new IOException("Limit usługi OCR. Spróbuj ponownie później.");
    return new IOException("Usługa OCR zwróciła błąd HTTP " + status + ".");
  }
}
