#!/usr/bin/env python3
"""Local Alertmanager webhook sink for forwarding verification."""
from http.server import BaseHTTPRequestHandler, HTTPServer
import json
import sys
from datetime import datetime, timezone


class Handler(BaseHTTPRequestHandler):
    def do_POST(self):
        length = int(self.headers.get("Content-Length", "0"))
        raw = self.rfile.read(length)
        try:
            payload = json.loads(raw.decode("utf-8"))
            summary = {
                "receivedAt": datetime.now(timezone.utc).isoformat(),
                "status": payload.get("status"),
                "alerts": [
                    {
                        "name": item.get("labels", {}).get("alertname"),
                        "severity": item.get("labels", {}).get("severity"),
                        "status": item.get("status"),
                    }
                    for item in payload.get("alerts", [])
                ],
            }
            print(json.dumps(summary, ensure_ascii=False), flush=True)
        except Exception as exc:  # noqa: BLE001 - test sink
            print(f"invalid payload: {exc}", file=sys.stderr, flush=True)
        self.send_response(200)
        self.end_headers()
        self.wfile.write(b"ok")

    def log_message(self, fmt, *args):
        return


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 19090
    HTTPServer(("0.0.0.0", port), Handler).serve_forever()
