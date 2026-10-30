package com.lyricsstatus.poweramp.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/** Tiny blocking HTTP helper on HttpURLConnection (no dependencies). */
public final class Http {

    public static final class Response {
        public final int code;
        public final String body;
        public final long elapsedMs;

        Response(int code, String body, long elapsedMs) {
            this.code = code;
            this.body = body;
            this.elapsedMs = elapsedMs;
        }

        public boolean ok() {
            return code >= 200 && code < 300;
        }
    }

    private Http() {}

    public static Response request(String method, String url, Map<String, String> headers, String jsonBody,
                                   int timeoutMs) throws IOException {
        long start = System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            try {
                // Android's OkHttp-backed HttpURLConnection accepts PATCH directly
                c.setRequestMethod(method);
            } catch (java.net.ProtocolException e) {
                // Plain JDK HttpURLConnection rejects PATCH
                c.setRequestMethod("POST");
                forceMethod(c, method);
            }
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setUseCaches(false);
            if (headers != null) for (Map.Entry<String, String> h : headers.entrySet()) c.setRequestProperty(h.getKey(), h.getValue());
            if (jsonBody != null) {
                byte[] b = jsonBody.getBytes(StandardCharsets.UTF_8);
                c.setDoOutput(true);
                c.setRequestProperty("Content-Type", "application/json");
                c.setFixedLengthStreamingMode(b.length);
                try (OutputStream os = c.getOutputStream()) {
                    os.write(b);
                }
            }
            int code = c.getResponseCode();
            InputStream in = code >= 400 ? c.getErrorStream() : c.getInputStream();
            // Reading the body to the end and closing the stream (without disconnect()) returns the socket to
            // the keep-alive pool, so the next lyric line reuses the TLS connection instead of a new handshake.
            String body = in == null ? "" : readAll(in);
            return new Response(code, body, System.currentTimeMillis() - start);
        } catch (IOException e) {
            // Broken connection: don't put it back in the pool
            c.disconnect();
            throw e;
        }
    }

    /** Fallback for runtimes whose HttpURLConnection rejects the method: set the protected field directly. */
    private static void forceMethod(HttpURLConnection c, String method) {
        for (Class<?> k = c.getClass(); k != null; k = k.getSuperclass()) {
            try {
                java.lang.reflect.Field f = k.getDeclaredField("method");
                f.setAccessible(true);
                f.set(c, method);
                return;
            } catch (NoSuchFieldException ignored) {
            } catch (Exception e) {
                c.setRequestProperty("X-HTTP-Method-Override", method);
                return;
            }
        }
    }

    private static String readAll(InputStream in) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            while ((n = is.read(buf)) > 0) out.write(buf, 0, n);
            return out.toString("UTF-8");
        }
    }

    public static String enc(String s) {
        try {
            return java.net.URLEncoder.encode(s, "UTF-8").replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
