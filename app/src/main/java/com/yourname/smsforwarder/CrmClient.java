package com.yourname.smsforwarder;

import android.util.Log;

import org.json.JSONObject;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Sends an incoming login SMS to the agent's own CRM.
 *
 * Protocol: POST {baseUrl}/api/otp-messages
 *   Authorization: Bearer <device token>
 *   { "sender": ..., "body": ..., "received_at": <ISO-8601> }
 *
 * The CRM extracts the numeric code, stores it scoped to this agent, and the
 * agent's browser extension pulls it. The token is the one shown once on the
 * CRM "חיבור מכשיר" screen.
 */
final class CrmClient {

    private static final String TAG = "CrmClient";
    private static final OkHttpClient CLIENT = new OkHttpClient();
    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private CrmClient() {}

    /** Synchronous POST. MUST be called off the main thread. @return true on HTTP 2xx. */
    static boolean send(String baseUrl, String token, String sender, String body) {
        if (baseUrl == null || baseUrl.isEmpty() || token == null || token.isEmpty()) {
            Log.e(TAG, "Cannot send: CRM not configured");
            return false;
        }

        final String url = baseUrl.replaceAll("/+$", "") + "/api/otp-messages";
        final String now = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZZZZZ", Locale.US).format(new Date());

        final String payload;
        try {
            payload = new JSONObject()
                    .put("sender", sender == null ? "" : sender)
                    .put("body", body == null ? "" : body)
                    .put("received_at", now)
                    .toString();
        } catch (Exception e) {
            Log.e(TAG, "Failed to build JSON", e);
            return false;
        }

        Request request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(payload, JSON))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json")
                .build();

        try (Response response = CLIENT.newCall(request).execute()) {
            if (response.isSuccessful()) {
                Log.d(TAG, "CRM POST ok (" + response.code() + ")");
                return true;
            }
            Log.e(TAG, "CRM POST failed: HTTP " + response.code());
            return false;
        } catch (IOException e) {
            Log.e(TAG, "CRM POST exception: " + e.getMessage(), e);
            return false;
        }
    }
}
