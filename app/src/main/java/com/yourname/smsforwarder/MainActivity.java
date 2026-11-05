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

import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {
    private EditText pairInput;
    private EditText crmUrlInput;
    private EditText tokenInput;
    private Button saveButton;
    private Button testButton;
    private TextView statusText;
    private static final int SMS_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        pairInput = findViewById(R.id.pairInput);
        crmUrlInput = findViewById(R.id.crmUrlInput);
        tokenInput = findViewById(R.id.tokenInput);
        saveButton = findViewById(R.id.saveButton);
        testButton = findViewById(R.id.testButton);
        statusText = findViewById(R.id.statusText);

        loadSettings();

        saveButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { saveSettings(); }
        });
        testButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { sendTestCode(); }
        });

        requestSmsPermission();
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences("SMSForwarder", MODE_PRIVATE);
        crmUrlInput.setText(prefs.getString("crm_base_url", ""));
        tokenInput.setText(prefs.getString("crm_token", ""));
    }

    /** Fills the URL/token fields from a pasted connection-code JSON, if present. */
    private void applyPairInput() {
        String raw = pairInput.getText().toString().trim();
        if (raw.isEmpty()) return;
        try {
            JSONObject o = new JSONObject(raw);
            if (o.has("base_url")) crmUrlInput.setText(o.getString("base_url"));
            if (o.has("token")) tokenInput.setText(o.getString("token"));
            pairInput.setText("");
        } catch (Exception ignored) { /* not JSON — use the fields as typed */ }
    }

    private void saveSettings() {
        applyPairInput();
        String baseUrl = crmUrlInput.getText().toString().trim();
        String token = tokenInput.getText().toString().trim();

        if (baseUrl.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "נא להזין כתובת וטוקן", Toast.LENGTH_SHORT).show();
            return;
        }

        SharedPreferences prefs = getSharedPreferences("SMSForwarder", MODE_PRIVATE);
        prefs.edit().putString("crm_base_url", baseUrl).putString("crm_token", token).apply();

        statusText.setText("✅ הגדרות נשמרו!\nהאפליקציה מוכנה לפעולה.");
        Toast.makeText(this, "הגדרות נשמרו בהצלחה!", Toast.LENGTH_SHORT).show();
    }

    /** Posts a dummy code to the CRM so the user can verify the connection. */
    private void sendTestCode() {
        applyPairInput();
        final String baseUrl = crmUrlInput.getText().toString().trim();
        final String token = tokenInput.getText().toString().trim();
        if (baseUrl.isEmpty() || token.isEmpty()) {
            Toast.makeText(this, "קודם שמור כתובת וטוקן", Toast.LENGTH_SHORT).show();
            return;
        }
        statusText.setText("⏳ שולח קוד בדיקה...");
        new Thread(new Runnable() {
            @Override public void run() {
                final boolean ok = CrmClient.send(baseUrl, token, "TEST", "Verification code is 123456");
                runOnUiThread(new Runnable() {
                    @Override public void run() {
                        if (ok) {
                            statusText.setText("✅ נשלח 123456 ל-CRM!");
                            Toast.makeText(MainActivity.this, "נשלח בהצלחה", Toast.LENGTH_SHORT).show();
                        } else {
                            statusText.setText("❌ שליחה ל-CRM נכשלה.\nבדוק כתובת, טוקן וחיבור אינטרנט.");
                            Toast.makeText(MainActivity.this, "שליחה נכשלה — בדוק לוג", Toast.LENGTH_LONG).show();
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
            statusText.setText("✅ יש הרשאות SMS\n⏳ הזן חיבור ל-CRM ולחץ שמור.");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == SMS_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                statusText.setText("✅ הרשאות SMS ניתנו!\n⏳ הזן חיבור ל-CRM ולחץ שמור.");
                Toast.makeText(this, "הרשאות SMS ניתנו!", Toast.LENGTH_SHORT).show();
            } else {
                statusText.setText("❌ ללא הרשאות SMS, האפליקציה לא תעבוד");
                Toast.makeText(this, "האפליקציה דורשת הרשאות SMS!", Toast.LENGTH_LONG).show();
            }
        }
    }
}
