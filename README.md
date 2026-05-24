# SMS Forwarder

אפליקציית Android שמעבירה קודי SMS למחשב לצורך מילוי אוטומטי בתוסף Chrome.

## התקנה

1. פתח את הפרויקט ב-Android Studio
2. חבר טלפון Android עם USB Debugging
3. לחץ Run ▶️
4. תן הרשאות SMS באפליקציה
5. הכנס את כתובת ה-IP של המחשב

## הרצת השרת
```bash
cd Server
python sms_server.py
```

## שימוש

כשמגיע SMS עם קוד, הוא מועבר אוטומטית למחשב!
