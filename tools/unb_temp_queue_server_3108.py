#!/usr/bin/env python3
# UNB_TEMP_QUEUE_ATOMIC_LOCK_V1

import copy
import json
import os
import shutil
import threading
import time
import uuid
from pathlib import Path
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from urllib.parse import urlparse, parse_qs

BASE = Path("/data/data/com.termux/files/home/unb-connect-lab")
DATA = BASE / "data"
TOKEN_FILE = DATA / "web_token.txt"
QUEUE_FILE = DATA / "termux_queue.json"
DATA.mkdir(parents=True, exist_ok=True)

DEFAULT_TOKEN = "UNB_1780250733_8cd0eb461eb946a492223e2a30dd63e7cb63b234c1501c8a"

QUEUE_LOCK = threading.RLock()
LAST_GOOD_QUEUE = None


def token():
    if TOKEN_FILE.exists():
        t = TOKEN_FILE.read_text().strip()
        if t:
            return t
    TOKEN_FILE.write_text(DEFAULT_TOKEN)
    return DEFAULT_TOKEN


def normalize(row):
    if not isinstance(row, dict):
        row = {}

    method = row.get("method") or row.get("delivery_method") or row.get("send_method") or "whatsapp_web"
    msg = row.get("message") or row.get("body") or row.get("text") or ""
    jid = row.get("id") or row.get("job_id") or ("TQ_" + uuid.uuid4().hex[:12])
    phone = row.get("phone") or row.get("recipient_phone") or row.get("to") or ""
    status = row.get("status") or "pending"

    out = dict(row)
    out["id"] = jid
    out["job_id"] = jid
    out["method"] = method
    out["delivery_method"] = method
    out["phone"] = phone
    out["to"] = out.get("to") or phone
    out["message"] = msg
    out["body"] = msg
    out["status"] = status
    return out


def _backup_bad_queue(raw_text, reason):
    try:
        ts = time.strftime("%Y%m%d_%H%M%S")
        bad = DATA / f"termux_queue_corrupt_{ts}_{reason}.json"
        bad.write_text(raw_text, encoding="utf-8")
        return str(bad)
    except Exception:
        return ""


def read_queue_unlocked():
    global LAST_GOOD_QUEUE

    if not QUEUE_FILE.exists():
        LAST_GOOD_QUEUE = []
        write_queue_unlocked([])
        return []

    try:
        raw = QUEUE_FILE.read_text(encoding="utf-8")
        if raw.strip() == "":
            raise ValueError("empty_queue_file")

        v = json.loads(raw)
        if not isinstance(v, list):
            raise ValueError("queue_json_not_list")

        q = [normalize(x) for x in v]
        LAST_GOOD_QUEUE = copy.deepcopy(q)
        return q

    except Exception as e:
        raw = ""
        try:
            raw = QUEUE_FILE.read_text(encoding="utf-8", errors="ignore")
        except Exception:
            pass

        _backup_bad_queue(raw, e.__class__.__name__)

        if LAST_GOOD_QUEUE is not None:
            return copy.deepcopy(LAST_GOOD_QUEUE)

        # إذا السيرفر بدأ والملف نفسه فاسد ولا توجد نسخة ذاكرة، لا نكتب فوقه هنا.
        return []


def write_queue_unlocked(q):
    global LAST_GOOD_QUEUE

    q = [normalize(x) for x in (q or [])]
    DATA.mkdir(parents=True, exist_ok=True)

    payload = json.dumps(q, ensure_ascii=False, indent=2)
    tmp = DATA / f".termux_queue.{os.getpid()}.{threading.get_ident()}.{int(time.time()*1000)}.tmp"

    with open(tmp, "w", encoding="utf-8") as f:
        f.write(payload)
        f.flush()
        os.fsync(f.fileno())

    os.replace(str(tmp), str(QUEUE_FILE))
    LAST_GOOD_QUEUE = copy.deepcopy(q)


def read_queue():
    with QUEUE_LOCK:
        return read_queue_unlocked()


def write_queue(q):
    with QUEUE_LOCK:
        write_queue_unlocked(q)


def ok_token(v):
    return (v or "").strip() == token()


class H(BaseHTTPRequestHandler):
    def send_json(self, code, obj):
        b = json.dumps(obj, ensure_ascii=False, indent=2).encode("utf-8")
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Headers", "Content-Type, Authorization, X-UNB-Token")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Content-Length", str(len(b)))
        self.end_headers()
        self.wfile.write(b)

    def do_OPTIONS(self):
        self.send_json(200, {"ok": True})

    def body_json(self):
        n = int(self.headers.get("Content-Length") or "0")
        if n <= 0:
            return {}
        raw = self.rfile.read(n).decode("utf-8", "ignore")
        try:
            return json.loads(raw)
        except Exception:
            return {}

    def auth_value(self, qs, data=None):
        data = data or {}
        h = self.headers.get("Authorization", "")
        if h.lower().startswith("bearer "):
            return h[7:].strip()
        return (
            data.get("token")
            or self.headers.get("X-UNB-Token")
            or (qs.get("token") or [""])[0]
            or ""
        )

    def do_GET(self):
        u = urlparse(self.path)
        qs = parse_qs(u.query)

        if u.path == "/health":
            return self.send_json(200, {
                "ok": True,
                "app": "UNB Connect APK Hydra",
                "mode": "termux_temp_queue_atomic_lock_v1",
                "port": 3108,
                "device_methods": ["call_apk", "sms_apk", "whatsapp_apk", "whatsapp_web"],
                "token_required": True,
                "has_pull": True,
                "atomic_lock": True,
                "time": int(time.time())
            })

        if u.path == "/api/queue/list":
            if not ok_token(self.auth_value(qs)):
                return self.send_json(401, {"ok": False, "error": "bad_token"})

            with QUEUE_LOCK:
                q = read_queue_unlocked()

            by = {}
            for r in q:
                by[r.get("status", "pending")] = by.get(r.get("status", "pending"), 0) + 1

            return self.send_json(200, {"ok": True, "count": len(q), "by_status": by, "items": q})

        if u.path == "/api/queue/pull":
            return self.handle_pull(qs, {})

        return self.send_json(404, {"ok": False, "error": "not_found", "path": u.path})

    def do_POST(self):
        u = urlparse(self.path)
        qs = parse_qs(u.query)
        data = self.body_json()

        if u.path == "/api/queue/enqueue":
            if not ok_token(self.auth_value(qs, data)):
                return self.send_json(401, {"ok": False, "error": "bad_token"})

            with QUEUE_LOCK:
                q = read_queue_unlocked()
                methods = data.get("methods") or [data.get("method") or "whatsapp_web"]
                created = []

                for m in methods:
                    jid = "TQ_" + str(int(time.time() * 1000)) + "_" + uuid.uuid4().hex[:8]
                    row = normalize({
                        "id": jid,
                        "job_id": jid,
                        "method": m,
                        "delivery_method": m,
                        "phone": data.get("phone", ""),
                        "to": data.get("to") or data.get("phone", ""),
                        "message": data.get("message") or data.get("body") or "",
                        "body": data.get("message") or data.get("body") or "",
                        "status": "pending",
                        "created_at": int(time.time()),
                        "last_error": "",
                        "source": data.get("source", "api_enqueue"),
                        "phase": data.get("phase", ""),
                        "batch": data.get("batch", "")
                    })
                    q.append(row)
                    created.append(row)

                write_queue_unlocked(q)

            return self.send_json(200, {"ok": True, "created": created, "items": created})

        if u.path == "/api/queue/pull":
            return self.handle_pull(qs, data)

        if u.path == "/api/queue/ack":
            if not ok_token(self.auth_value(qs, data)):
                return self.send_json(401, {"ok": False, "error": "bad_token"})

            with QUEUE_LOCK:
                q = read_queue_unlocked()
                jid = data.get("id") or data.get("job_id") or data.get("queue_id")
                found = False

                for r in q:
                    rid = r.get("id") or r.get("job_id")
                    if rid == jid:
                        r["id"] = rid
                        r["job_id"] = rid
                        r["status"] = data.get("status", "sent")
                        r["last_error"] = data.get("last_error", data.get("error", "ack_from_app"))
                        r["acked_at"] = int(time.time())
                        if r["status"] == "sent":
                            r["sent_at"] = int(time.time())
                        if r["status"] == "failed":
                            r["failed_at"] = int(time.time())
                        found = True

                write_queue_unlocked(q)

            return self.send_json(200, {"ok": True, "found": found, "id": jid})

        return self.send_json(404, {"ok": False, "error": "not_found", "path": u.path})

    def handle_pull(self, qs, data):
        # UNB_TEMP_QUEUE_PULL_RESERVE_ONE_JOB_V1 + UNB_TEMP_QUEUE_ATOMIC_LOCK_V1
        if not ok_token(self.auth_value(qs, data)):
            return self.send_json(401, {"ok": False, "error": "bad_token"})

        requested = []
        for key in ["method", "delivery_method"]:
            v = data.get(key) or (qs.get(key) or [""])[0]
            if v:
                requested.append(v)

        raw_methods = data.get("methods") or (qs.get("methods") or [""])[0]
        if isinstance(raw_methods, list):
            requested += raw_methods
        elif isinstance(raw_methods, str) and raw_methods:
            requested += [x.strip() for x in raw_methods.split(",") if x.strip()]

        requested = set(requested)
        now = int(time.time())
        lease_seconds = int(data.get("lease_seconds") or (qs.get("lease_seconds") or ["120"])[0] or 120)

        with QUEUE_LOCK:
            q = read_queue_unlocked()

            for r in q:
                if r.get("status") == "processing":
                    processing_at = int(r.get("processing_at") or 0)
                    if processing_at and now - processing_at > lease_seconds:
                        r["status"] = "pending"
                        r["last_error"] = "processing_lease_expired_returned_to_pending"

            picked = None
            for r in q:
                if r.get("status") != "pending":
                    continue

                m = r.get("method") or r.get("delivery_method")
                if requested and m not in requested:
                    continue

                r["status"] = "processing"
                r["processing_at"] = now
                r["attempts"] = int(r.get("attempts") or 0) + 1
                r["last_error"] = "pulled_by_device_processing"
                picked = dict(r)
                picked["status"] = "pending"
                picked["reserved"] = True
                break

            write_queue_unlocked(q)

        items = [picked] if picked else []
        first = picked if picked else None

        return self.send_json(200, {
            "ok": True,
            "count": len(items),
            "items": items,
            "jobs": items,
            "queue": items,
            "job": first,
            "item": first
        })

    def log_message(self, *args):
        return


if __name__ == "__main__":
    if not QUEUE_FILE.exists():
        with QUEUE_LOCK:
            write_queue_unlocked([])

    ThreadingHTTPServer(("127.0.0.1", 3108), H).serve_forever()
