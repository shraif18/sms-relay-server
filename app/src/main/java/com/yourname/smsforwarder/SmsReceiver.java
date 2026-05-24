package com.yourname.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Receives incoming SMS messages and forwards each OTP code to ntfy.sh.
 *
 * No service is used — starting Android 8, apps in the background may not call
 * context.startService(). We rely on BroadcastReceiver.goAsync() to keep the
 * receiver alive while a quick HTTP request runs on a worker thread.
 */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    @Override
    public void onReceive(final Context context, Intent intent) {
        Log.d(TAG, "SMS received!");

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) {
            return;
        }

        // Re-assemble multi-part SMS by sender.
        String format = bundle.getString("format");
        final Map<String, StringBuilder> bySender = new HashMap<>();
        for (Object pdu : pdus) {
            SmsMessage smsMessage;
            if (format != null) {
                smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
            } else {
                smsMessage = SmsMessage.createFromPdu((byte[]) pdu);
            }
            String sender = smsMessage.getDisplayOriginatingAddress();
            String body = smsMessage.getDisplayMessageBody();
            if (sender == null) sender = "";
            if (body == null) body = "";
            StringBuilder sb = bySender.get(sender);
            if (sb == null) {
                sb = new StringBuilder();
                bySender.put(sender, sb);
            }
            sb.append(body);
        }

        // Read configuration synchronously (fast, no network).
        SharedPreferences prefs = context.getSharedPreferences("SMSForwarder", Context.MODE_PRIVATE);
        final String topic = prefs.getString("ntfy_topic", "");
        if (topic.isEmpty()) {
            Log.w(TAG, "No ntfy topic configured — open the app and save settings.");
            return;
        }

        // goAsync() lets us survive past onReceive() and do network on a worker thread.
        final PendingResult pendingResult = goAsync();

        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (Map.Entry<String, StringBuilder> entry : bySender.entrySet()) {
                        String sender = entry.getKey();
                        String message = entry.getValue().toString();
                        Log.d(TAG, "From: " + sender + ", Message: " + message);

                        String code = extractCode(message);
                        if (code == null) {
                            Log.d(TAG, "No code found in message — skipping");
                            continue;
                        }
                        Log.d(TAG, "Code extracted: " + code);
                        boolean ok = NtfyClient.send(topic, sender, message, code);
                        if (!ok) {
                            Log.e(TAG, "Failed to forward code to ntfy.sh");
                        }
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "Unexpected error in SmsReceiver worker", t);
                } finally {
                    pendingResult.finish();
                }
            }
        }).start();
    }

    /**
     * Extract a 4–6 digit verification code from the message.
     *
     * Lookarounds (?<!\d) and (?!\d) instead of \b — friendlier to Hebrew text
     * around the code, and won't accidentally pull a short chunk out of a
     * longer numeric string.
     */
    static String extractCode(String message) {
        if (message == null) return null;
        Pattern pattern = Pattern.compile("(?<!\\d)(\\d{4,6})(?!\\d)");
        Matcher matcher = pattern.matcher(message);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
