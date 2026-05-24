package com.yourname.smsforwarder;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/**
 * DEPRECATED — no longer used.
 *
 * The previous implementation forwarded SMS messages from this Service, started
 * by SmsReceiver via context.startService(). That pattern is forbidden on
 * Android 8 (Oreo) and later for apps in the background — it throws
 * IllegalStateException and the SMS was silently dropped.
 *
 * All forwarding logic has moved into SmsReceiver, which uses goAsync() to
 * perform the HTTP request on a worker thread directly from the receiver.
 *
 * This empty class is kept only so the file remains compilable. The service is
 * no longer registered in AndroidManifest.xml and is never started by anything.
 */
public class SmsForwarderService extends Service {
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
