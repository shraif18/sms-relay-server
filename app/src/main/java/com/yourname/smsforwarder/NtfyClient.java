package com.yourname.smsforwarder;

import android.util.Log;

import java.io.IOException;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends OTP codes to ntfy.sh.
 *
 * Protocol: POST to https://ntfy.sh/<topic> with the raw OTP code as the body.
 * Optional ntfy headers used here:
 *   X-Title       — short label (we use the SMS sender for debuggability)
 *   X-Tags        — visible chips in the ntfy UI (we use "otp")
 *
 * The Chrome extension polls https://ntfy.sh/<topic>/json?poll=1&since=...
 * and pulls the body of the latest message.
 */
final class NtfyClient {

    private static final String TAG = "NtfyClient";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final MediaType TEXT = MediaType.parse("text/plain; charset=utf-8");

    private NtfyClient() {}

    /**
     * Synchronous send. MUST be called off the main thread.
     *
     * @return true on HTTP 2xx, false on any failure.
     */
    static boolean send(String topic, String sender, String fullMessage, String code) {
        if (topic == null || topic.isEmpty()) {
            Log.e(TAG, "Cannot send: empty topic");
            return false;
        }
        if (code == null || code.isEmpty()) {
            Log.e(TAG, "Cannot send: empty code");
            return false;
        }

        final String url = "https://ntfy.sh/" + topic;
        RequestBody body = RequestBody.create(code, TEXT);

        // X-Title and X-Tags only accept ASCII reliably; encode safely.
        String safeTitle = asciiSafe(sender == null ? "SMS" : sender);

        Request.Builder builder = new Request.Builder()
                .url(url)
                .post(body)
                .header("X-Title", safeTitle)
                .header("X-Tags", "otp");

        // Include the original SMS body as a debug header, ASCII-safe.
        if (fullMessage != null && !fullMessage.isEmpty()) {
            String safeMsg = asciiSafe(fullMessage);
            if (safeMsg.length() > 200) safeMsg = safeMsg.substring(0, 200);
            // ntfy ignores unknown headers; harmless if dropped.
            builder.header("X-Sms-Preview", safeMsg);
        }

        try (Response response = CLIENT.newCall(builder.build()).execute()) {
            if (response.isSuccessful()) {
                Log.d(TAG, "ntfy POST ok (" + response.code() + ") topic=" + topic);
                return true;
            } else {
                Log.e(TAG, "ntfy POST failed: HTTP " + response.code());
                return false;
            }
        } catch (IOException e) {
            Log.e(TAG, "ntfy POST exception: " + e.getMessage(), e);
            return false;
        }
    }

    /**
     * Replace anything outside printable ASCII with '?'. ntfy headers must be
     * latin-1 safe; Hebrew SMS sender names like "הראל" would otherwise crash
     * OkHttp's header validator.
     */
    private static String asciiSafe(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= 0x20 && c < 0x7F) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }
}
