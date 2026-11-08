import os
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

NAME = os.environ.get("NAME", "sim")
DELAY = float(os.environ.get("DELAY_MS", "20")) / 1000
CAPACITY = int(os.environ.get("MAX_CONCURRENCY", "8"))

slots = threading.BoundedSemaphore(CAPACITY)


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.1"   # keep-alive, like a real server

    def do_GET(self):
        with slots:
            time.sleep(DELAY)
        body = f"Name: {NAME}\n".encode()
        self.send_response(200)
        self.send_header("Content-Type", "text/plain")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def log_message(self, *args):
        pass


class Server(ThreadingHTTPServer):
    request_queue_size = 1024


Server(("0.0.0.0", 80), Handler).serve_forever()