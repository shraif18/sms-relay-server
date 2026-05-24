"""
SMS Server
==========

Receives SMS codes posted from the SMS Forwarder Android app, and serves the
most recent code to the Chrome extension as JSON.

Endpoints
---------
POST /            (or any path)  application/x-www-form-urlencoded
    sender, message, code        Receives an SMS code from the phone.

GET  /            (or any path)
    Returns {"code": "...", "sender": "..."} for the most recent SMS.
    The code is one-shot: after a successful GET that returned a real code,
    the stored code is cleared so it won't be replayed on the next login.
    Codes also expire 5 minutes after they arrive.

Run
---
    python sms_server.py

Then put the printed LAN IP in the Android app, and make sure your Windows
Firewall allows inbound TCP on port 8765.
"""

from http.server import HTTPServer, BaseHTTPRequestHandler
from urllib.parse import parse_qs
import json
import time
import socket
import threading

PORT = 8765
CODE_TTL_SECONDS = 300  # codes older than this are treated as expired

# Single shared state, protected by a lock since BaseHTTPServer is multi-thread
# safe per request but state isn't.
_state_lock = threading.Lock()
latest_code = {}  # {'code', 'sender', 'message', 'time'}


def get_lan_ip():
    """Return the machine's LAN IP as the phone would see it.

    socket.gethostbyname(gethostname()) often returns 127.0.0.1 on Windows,
    which is useless. The UDP trick below asks the OS which interface would
    be used to reach a public address, without actually sending any packets.
    """
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        s.connect(("8.8.8.8", 80))
        return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"
    finally:
        s.close()


class SMSHandler(BaseHTTPRequestHandler):
    # --- Browser receives the latest code ---

    def do_GET(self):
        global latest_code
        with _state_lock:
            code_data = dict(latest_code)
            # Expire stale codes.
            if code_data and time.time() - code_data.get("time", 0) > CODE_TTL_SECONDS:
                code_data = {}
                latest_code = {}

            payload = {
                "code": code_data.get("code"),
                "sender": code_data.get("sender"),
            }

            # One-shot: clear after a successful read of a real code, so the
            # extension never re-uses an old code on a subsequent login.
            if code_data.get("code"):
                latest_code = {}

        body = json.dumps(payload).encode("utf-8")
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    # --- Phone posts an incoming SMS ---

    def do_POST(self):
        global latest_code
        try:
            content_length = int(self.headers.get("Content-Length", "0") or "0")
            raw = self.rfile.read(content_length).decode("utf-8", errors="replace") if content_length else ""
            params = parse_qs(raw)

            sender = params.get("sender", [""])[0]
            message = params.get("message", [""])[0]
            code = params.get("code", [""])[0]

            print(f"[{time.strftime('%H:%M:%S')}] SMS from {sender!r}: code={code!r}")
            if message:
                # One-line preview of the message body for easier debugging.
                preview = message.replace("\n", " ").strip()
                if len(preview) > 160:
                    preview = preview[:157] + "..."
                print(f"    message: {preview}")

            with _state_lock:
                latest_code = {
                    "code": code,
                    "sender": sender,
                    "message": message,
                    "time": time.time(),
                }

            self.send_response(200)
            self.send_header("Access-Control-Allow-Origin", "*")
            self.send_header("Content-Length", "2")
            self.end_headers()
            self.wfile.write(b"OK")
        except Exception as e:
            print(f"!! Error handling POST: {e}")
            try:
                self.send_response(500)
                self.send_header("Access-Control-Allow-Origin", "*")
                self.end_headers()
            except Exception:
                pass

    # --- Pre-flight (some browsers may send it; harmless to support) ---

    def do_OPTIONS(self):
        self.send_response(204)
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")
        self.send_header("Content-Length", "0")
        self.end_headers()

    # Silence the default per-request log line; we print our own messages above.
    def log_message(self, format, *args):
        pass


def main():
    lan_ip = get_lan_ip()
    print("=" * 50)
    print("SMS Server running")
    print(f"Listening on:   0.0.0.0:{PORT}  (all interfaces)")
    print(f"LAN IP:         {lan_ip}")
    print(f"Phone app URL:  http://{lan_ip}:{PORT}")
    print(f"Browser URL:    http://localhost:{PORT}/")
    print("=" * 50)
    print("Tip: if the phone says 'failed to send', open Windows Firewall and")
    print("     allow inbound TCP on port", PORT, "for Python.")
    print()

    server = HTTPServer(("0.0.0.0", PORT), SMSHandler)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        print("\nShutting down.")
        server.server_close()


if __name__ == "__main__":
    main()
