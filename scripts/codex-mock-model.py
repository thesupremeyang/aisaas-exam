#!/usr/bin/env python3
"""Loopback-only OpenAI-compatible mock used by the isolated exam load test."""

import json
import threading
import time
import uuid
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


LOG_PATH = "/tmp/codex-rest-mock-model.jsonl"
lock = threading.Lock()
active = 0
max_active = 0
request_count = 0


def log_event(event):
    with open(LOG_PATH, "a", encoding="utf-8") as stream:
        stream.write(json.dumps(event, ensure_ascii=False) + "\n")


class Handler(BaseHTTPRequestHandler):
    protocol_version = "HTTP/1.0"

    def log_message(self, _format, *_args):
        return

    def send_json(self, payload, status=200):
        raw = json.dumps(payload, ensure_ascii=False).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(raw)))
        self.send_header("Connection", "close")
        self.end_headers()
        self.wfile.write(raw)

    def do_GET(self):
        if self.path in ("/health", "/v1/health"):
            self.send_json({"ok": True, "service": "codex-isolated-mock-model"})
            return
        if self.path.endswith("/models"):
            self.send_json({
                "object": "list",
                "data": [{"id": "codex-rest-model", "object": "model", "owned_by": "codex"}],
            })
            return
        self.send_json({"error": {"message": "not found"}}, 404)

    def do_POST(self):
        global active, max_active, request_count
        started = time.time()
        with lock:
            active += 1
            request_count += 1
            max_active = max(max_active, active)
            active_at_start = active
            peak = max_active

        status = 200
        error = None
        stream = False
        try:
            length = int(self.headers.get("Content-Length", "0"))
            body = self.rfile.read(length) if length else b"{}"
            try:
                request = json.loads(body.decode("utf-8"))
            except Exception:
                request = {}
            stream = bool(request.get("stream", False))

            if not self.path.endswith("chat/completions"):
                status = 404
                self.send_json({"error": {"message": "not found"}}, status)
                return

            response_id = "chatcmpl-codex-" + uuid.uuid4().hex[:16]
            content = "这是考试版隔离压力测试的模拟模型回答：公平、全面、可验证。"
            if stream:
                chunks = ["这是", "考试版", "隔离压力测试", "的模拟模型", "回答：公平、全面、可验证。"]
                self.send_response(200)
                self.send_header("Content-Type", "text/event-stream")
                self.send_header("Cache-Control", "no-cache")
                self.send_header("Connection", "close")
                self.end_headers()

                first = {
                    "id": response_id,
                    "object": "chat.completion.chunk",
                    "created": int(time.time()),
                    "model": "codex-rest-model",
                    "choices": [{"index": 0, "delta": {"role": "assistant"}, "finish_reason": None}],
                }
                self.wfile.write(("data: " + json.dumps(first, ensure_ascii=False) + "\n\n").encode("utf-8"))
                self.wfile.flush()
                for part in chunks:
                    time.sleep(0.05)
                    event = {
                        "id": response_id,
                        "object": "chat.completion.chunk",
                        "created": int(time.time()),
                        "model": "codex-rest-model",
                        "choices": [{"index": 0, "delta": {"content": part}, "finish_reason": None}],
                    }
                    self.wfile.write(("data: " + json.dumps(event, ensure_ascii=False) + "\n\n").encode("utf-8"))
                    self.wfile.flush()
                final = {
                    "id": response_id,
                    "object": "chat.completion.chunk",
                    "created": int(time.time()),
                    "model": "codex-rest-model",
                    "choices": [{"index": 0, "delta": {}, "finish_reason": "stop"}],
                    "usage": {"prompt_tokens": 8, "completion_tokens": 12, "total_tokens": 20},
                }
                self.wfile.write(("data: " + json.dumps(final, ensure_ascii=False) + "\n\ndata: [DONE]\n\n").encode("utf-8"))
                self.wfile.flush()
            else:
                time.sleep(0.05)
                payload = {
                    "id": response_id,
                    "object": "chat.completion",
                    "created": int(time.time()),
                    "model": "codex-rest-model",
                    "choices": [{"index": 0, "message": {"role": "assistant", "content": content}, "finish_reason": "stop"}],
                    "usage": {"prompt_tokens": 8, "completion_tokens": 12, "total_tokens": 20},
                }
                self.send_json(payload)
        except Exception as exc:
            status = 500
            error = str(exc)[:200]
            try:
                self.send_json({"error": {"message": "mock failure"}}, status)
            except Exception:
                pass
        finally:
            finished = time.time()
            with lock:
                active -= 1
                active_after = active
                current_count = request_count
            log_event({
                "type": "chat_completion",
                "path": self.path,
                "stream": stream,
                "status": status,
                "active_at_start": active_at_start,
                "max_active_so_far": peak,
                "active_at_end": active_after,
                "count": current_count,
                "duration_ms": round((finished - started) * 1000, 1),
                "error": error,
            })


if __name__ == "__main__":
    ThreadingHTTPServer(("127.0.0.1", 18080), Handler).serve_forever()
