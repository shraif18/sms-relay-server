package com.yourname.smsforwarder;

import android.Manifest;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {
    private EditText topicInput;
    private Button saveButton;
    private Button testButton;
    private TextView statusText;
    private static final int SMS_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        topicInput = findViewById(R.id.topicInput);
        saveButton = findViewById(R.id.saveButton);
        testButton = findViewById(R.id.testButton);
        statusText = findViewById(R.id.statusText);

        loadSettings();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                saveSettings();
            }
        });

        testButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendTestCode();
            }
        });

        requestSmsPermission();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("SMSForwarder", MODE_PRIVATE);
        String topic = prefs.getString("ntfy_topic", "");
        topicInput.setText(topic);
    }

    private void saveSettings() {
        String topic = topicInput.getText().toString().trim();

        if (topic.isEmpty()) {
            Toast.makeText(this, "נא להזין שם טופיק", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("SMSForwarder", MODE_PRIVATE);
        prefs.edit().putString("ntfy_topic", topic).apply();

        statusText.setText("✅ הגדרות נשמרו!\nהאפליקציה מוכנה לפעולה.\nטופיק: " + topic);
        Toast.makeText(this, "הגדרות נשמרו בהצלחה!", Toast.LENGTH_SHORT).show();
    }

    /**
     * Sends a dummy "test" message to ntfy.sh using the saved topic so the user
     * can verify (a) the topic is set correctly and (b) the phone has internet
     * access to ntfy.sh, without needing an actual SMS to arrive.
     */
    private void sendTestCode() {
        final String topic = topicInput.getText().toString().trim();
        if (topic.isEmpty()) {
            Toast.makeText(this, "קודם שמור טופיק", Toast.LENGTH_SHORT).show();
            return;
        }
        statusText.setText("⏳ שולח קוד בדיקה...");
        new Thread(new Runnable() {
            @Override
            public void run() {
                final boolean ok = NtfyClient.send(topic, "TEST",
                        "Verification code is 123456", "123456");
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (ok) {
                            statusText.setText("✅ נשלח 123456 ל-ntfy.sh!\nבדוק את הדפדפן.");
                            Toast.makeText(MainActivity.this,
                                    "נשלח בהצלחה", Toast.LENGTH_SHORT).show();
                        } else {
                            statusText.setText("❌ שליחה ל-ntfy.sh נכשלה.\nבדוק חיבור אינטרנט.");
                            Toast.makeText(MainActivity.this,
                                    "שליחה נכשלה — בדוק לוג", Toast.LENGTH_LONG).show();
                        }
                    }
                });
            }
        }).start();
    }

    private void requestSmsPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS}, SMS_PERMISSION_CODE);
        } else {
            statusText.setText("✅ יש הרשאות SMS\n⏳ הזן טופיק ולחץ שמור.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusText.setText("✅ הרשאות SMS ניתנו!\n⏳ הזן טופיק ולחץ שמור.");
                Toast.makeText(this, "הרשאות SMS ניתנו!", Toast.LENGTH_SHORT).show();
            } else {
                statusText.setText("❌ ללא הרשאות SMS, האפליקציה לא תעבוד");
                Toast.makeText(this, "האפליקציה דורשת הרשאות SMS!", Toast.LENGTH_LONG).show();
            }
        }
    }
}
