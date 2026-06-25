#!/usr/bin/env python3
"""
ECU Controller — run this on your PC, then open http://localhost:5000 in Chrome.
The Android app connects to http://<your-PC-IP>:5000/cpu to read the ECU load.

No dependencies required — uses Python 3 standard library only.

Usage:
    python3 server.py

Find your PC's IP:
    Mac:     ifconfig | grep "inet " | grep -v 127
    Windows: ipconfig
"""
import json, os, socket
from http.server import BaseHTTPRequestHandler, HTTPServer

state = {'value': 20.0}

HTML_PATH = os.path.join(os.path.dirname(__file__), 'index.html')

class Handler(BaseHTTPRequestHandler):

    def do_GET(self):
        if self.path == '/':
            self._serve_file(HTML_PATH, 'text/html')
        elif self.path == '/cpu':
            self._json(state)
        else:
            self.send_response(404); self.end_headers()

    def do_POST(self):
        if self.path == '/cpu':
            length = int(self.headers.get('Content-Length', 0))
            body   = json.loads(self.rfile.read(length))
            state['value'] = max(0.0, min(100.0, float(body.get('value', state['value']))))
            self._json(state)
        else:
            self.send_response(404); self.end_headers()

    def do_OPTIONS(self):
        self.send_response(200)
        self._cors()
        self.end_headers()

    def _json(self, data):
        body = json.dumps(data).encode()
        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Content-Length', str(len(body)))
        self._cors()
        self.end_headers()
        self.wfile.write(body)

    def _serve_file(self, path, mime):
        with open(path, 'rb') as f:
            body = f.read()
        self.send_response(200)
        self.send_header('Content-Type', mime)
        self.send_header('Content-Length', str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def _cors(self):
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type')

    def log_message(self, fmt, *args):
        pass  # suppress per-request logging


def local_ip():
    try:
        s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
        s.connect(('8.8.8.8', 80))
        ip = s.getsockname()[0]
        s.close()
        return ip
    except Exception:
        return '127.0.0.1'


if __name__ == '__main__':
    ip = local_ip()
    print(f'\n  ECU Controller started')
    print(f'  Browser UI  →  http://localhost:8765')
    print(f'  Android app →  enter IP: {ip}')
    print(f'\n  Press Ctrl+C to stop\n')
    HTTPServer(('0.0.0.0', 8765), Handler).serve_forever()
