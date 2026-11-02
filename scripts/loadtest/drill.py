#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
智能体考试系统 · 生产灰度压测（无第三方依赖，跑在服务器本机）

子命令：
  smoke                 登录 + 建 1 个会话 + 跑 1 轮 SSE，验证链路
  sessions N            建 N 个会话，写 sessions.json
  sse N ROUNDS          用 sessions.json 的前 N 个会话并发跑 ROUNDS 轮
  ws N CONC             并发 WS 握手（无 token，只看路由/接入）
  login N CONC          并发登录（错误口令，测鉴权路径）
  cleanup               删除本次压测产生的会话/消息/账号

设计要点：
  * 只打本机 127.0.0.1，不经过公网；
  * SSE 用 http.client 流式读取，统计 ttfb/ttft/完成率/错误文本；
  * 所有压测数据的 nickname/username 都带 ltdrill 前缀，便于清理。
"""

import json
import os
import ssl
import statistics
import sys
import threading
import time
import http.client
from concurrent.futures import ThreadPoolExecutor

BASE_HOST = "127.0.0.1"
BASE_PORT = 18080
API_PREFIX = "/web/api"
TOKEN = os.environ.get("TOKEN", "")
AGENT_ID = os.environ.get("AGENT_ID", "2095518701586325505")   # 考试测试租户05 的深寻-flash agent
AGENT_CODE = os.environ.get("AGENT_CODE", "exam-2026005-001")
USERNAME = os.environ.get("DRILL_USER", "ltdrill")
PASSWORD = os.environ.get("DRILL_PASS", "Drill@2026")
QUESTION = os.environ.get("QUESTION", "用一句话说明你能帮我做什么。")
FORCE_AGENT_CODE = os.environ.get("FORCE_AGENT_CODE", "")

SESSIONS_FILE = "/tmp/ltdrill-sessions.json"

lock = threading.Lock()


def http_conn():
    return http.client.HTTPConnection(BASE_HOST, BASE_PORT, timeout=180)


def api(method, path, body=None, token=None, timeout=30):
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT, timeout=timeout)
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    payload = json.dumps(body) if body is not None else None
    conn.request(method, API_PREFIX + path, body=payload, headers=headers)
    resp = conn.getresponse()
    data = resp.read()
    conn.close()
    try:
        return resp.status, json.loads(data.decode("utf-8"))
    except Exception:
        return resp.status, data.decode("utf-8", "ignore")


def login():
    status, body = api("POST", "/auth/login", {"username": USERNAME, "password": PASSWORD})
    data = body.get("data") if isinstance(body, dict) else None
    token = None
    if isinstance(data, dict):
        token = data.get("accessToken") or data.get("token")
    if not token:
        raise SystemExit("登录失败: %s %s" % (status, str(body)[:300]))
    return token


def create_session(token, title):
    status, body = api("POST", "/agent/chat/session", {"agentId": AGENT_ID, "title": title}, token=token)
    data = body.get("data") if isinstance(body, dict) else None
    sid = data.get("id") if isinstance(data, dict) else None
    if not sid:
        raise SystemExit("建会话失败: %s %s" % (status, str(body)[:300]))
    return str(sid)


def run_sse(thread_id, agent_code, token, idx=0):
    """跑一轮 AG-UI SSE，返回度量"""
    conn = http.client.HTTPConnection(BASE_HOST, BASE_PORT, timeout=180)
    body = {
        "threadId": thread_id,
        "runId": "lt_%d_%d_%d" % (int(time.time() * 1000), idx, os.getpid()),
        "messages": [{"id": "m1", "role": "user", "content": QUESTION}],
        "state": {},
        "tools": [],
        "context": [],
        "forwardedProps": {"agentCode": agent_code},
    }
    headers = {
        "Content-Type": "application/json",
        "Accept": "text/event-stream",
        "Authorization": "Bearer " + token,
    }
    t0 = time.time()
    m = {"status": 0, "elapsed": -1, "ttfb": -1, "ttft": -1, "finished": False, "err": "", "bytes": 0}
    try:
        conn.request("POST", API_PREFIX + "/runtime/agui/run/" + agent_code, body=json.dumps(body), headers=headers)
        resp = conn.getresponse()
        m["status"] = resp.status
        m["ttfb"] = round((time.time() - t0) * 1000)
        if resp.status != 200:
            m["err"] = "HTTP %s %s" % (resp.status, resp.read(200).decode("utf-8", "ignore"))
            m["elapsed"] = round((time.time() - t0) * 1000)
            return m

        buf = ""
        finished = False
        while True:
            chunk = resp.read(1024)
            if not chunk:
                break
            m["bytes"] += len(chunk)
            buf += chunk.decode("utf-8", "ignore")
            if m["ttft"] < 0 and ("TEXT_MESSAGE_CONTENT" in buf or '"delta"' in buf):
                m["ttft"] = round((time.time() - t0) * 1000)
            if not m["err"] and ('\\"error\\"' in buf or '"error"' in buf):
                i = buf.find('error')
                m["err"] = buf[max(0, i - 30):i + 200].replace("\n", " ")
            if "RUN_FINISHED" in buf:
                finished = True
                break
        m["finished"] = finished
        m["elapsed"] = round((time.time() - t0) * 1000)
        if not m["err"] and not finished:
            m["err"] = "stream ended without RUN_FINISHED"
        return m
    except Exception as e:
        m["elapsed"] = round((time.time() - t0) * 1000)
        m["err"] = "%s: %s" % (type(e).__name__, e)
        return m
    finally:
        try:
            conn.close()
        except Exception:
            pass


def pct(vals, p):
    if not vals:
        return -1
    vals = sorted(vals)
    i = min(len(vals) - 1, max(0, int((p / 100.0) * len(vals) + 0.999999) - 1))
    return vals[i]


def summarize(rows):
    ok = [r for r in rows if r["finished"] and not r["err"]]
    el = [r["elapsed"] for r in rows if r["elapsed"] >= 0]
    tt = [r["ttft"] for r in rows if r["ttft"] >= 0]
    errs = {}
    for r in rows:
        if r["err"]:
            key = r["err"][:120]
            errs[key] = errs.get(key, 0) + 1
    return {
        "total": len(rows),
        "ok": len(ok),
        "errorRate": round(1 - len(ok) / max(1, len(rows)), 4),
        "finishedRate": round(sum(1 for r in rows if r["finished"]) / max(1, len(rows)), 4),
        "elapsed_ms": {"p50": pct(el, 50), "p95": pct(el, 95), "p99": pct(el, 99), "max": max(el) if el else -1},
        "ttft_ms": {"p50": pct(tt, 50), "p95": pct(tt, 95), "p99": pct(tt, 99)},
        "errors": dict(sorted(errs.items(), key=lambda kv: -kv[1])[:6]),
    }


def cmd_smoke():
    t = login()
    print("login ok")
    sid = create_session(t, "ltdrill-smoke")
    print("session", sid)
    r = run_sse(sid, AGENT_CODE, t)
    print(json.dumps(r, ensure_ascii=False))
    return 1 if not r["finished"] else 0


def cmd_sessions(n):
    t = login()
    out = []
    for i in range(n):
        out.append({"threadId": create_session(t, "ltdrill-%d-%d" % (int(time.time()), i)), "agentCode": AGENT_CODE})
        if (i + 1) % 25 == 0:
            print("created", i + 1, flush=True)
    with open(SESSIONS_FILE, "w") as f:
        json.dump(out, f)
    print("sessions ->", SESSIONS_FILE, len(out))


def cmd_sse(n, rounds):
    t = TOKEN or login()
    with open(SESSIONS_FILE) as f:
        sessions = json.load(f)
    sessions = sessions[:n]
    total = len(sessions) * rounds
    rows = []
    done = [0]
    t0 = time.time()

    def worker(s, r):
        m = run_sse(s["threadId"], FORCE_AGENT_CODE or s["agentCode"], t, idx=r)
        with lock:
            rows.append(m)
            done[0] += 1
            if done[0] % 25 == 0:
                print("  progress %d/%d" % (done[0], total), flush=True)

    with ThreadPoolExecutor(max_workers=len(sessions)) as ex:
        futs = [ex.submit(worker, s, r) for s in sessions for r in range(rounds)]
        for f in futs:
            f.result()

    summary = summarize(rows)
    summary["vus"] = len(sessions)
    summary["rounds"] = rounds
    summary["wallMs"] = round((time.time() - t0) * 1000)
    summary["rps"] = round(len(rows) / max(0.001, time.time() - t0), 2)
    print(json.dumps(summary, ensure_ascii=False, indent=2))
    with open("/tmp/ltdrill-sse-%d.json" % len(sessions), "w") as f:
        json.dump({"summary": summary, "rows": rows}, f)
    return 0 if summary["errorRate"] <= 0.01 and summary["finishedRate"] >= 0.99 else 1


def cmd_ws(n, conc):
    def one(_):
        t0 = time.time()
        try:
            c = http.client.HTTPConnection(BASE_HOST, BASE_PORT, timeout=15)
            c.putrequest("GET", API_PREFIX + "/ws/apboa")
            c.putheader("Connection", "Upgrade")
            c.putheader("Upgrade", "websocket")
            c.putheader("Sec-WebSocket-Version", "13")
            c.putheader("Sec-WebSocket-Key", "dGhlIHNhbXBsZSBub25jZQ==")
            c.endheaders()
            r = c.getresponse()
            code = r.status
            r.read(200)
            c.close()
        except Exception:
            code = 0
        return code, round((time.time() - t0) * 1000)

    res = []
    with ThreadPoolExecutor(max_workers=conc) as ex:
        for item in ex.map(one, range(n)):
            res.append(item)
    by = {}
    for code, _ in res:
        by[str(code)] = by.get(str(code), 0) + 1
    ms = [m for _, m in res]
    print(json.dumps({"n": n, "concurrency": conc, "status": by,
                      "p50": pct(ms, 50), "p95": pct(ms, 95)}, ensure_ascii=False))
    return 0 if str(404) not in by else 3


def cmd_login(n, conc):
    def one(_):
        t0 = time.time()
        try:
            st, _ = api("POST", "/auth/login", {"username": "ltdrill_nouser", "password": "x"}, timeout=20)
        except Exception:
            st = 0
        return st, round((time.time() - t0) * 1000)

    res = []
    with ThreadPoolExecutor(max_workers=conc) as ex:
        for item in ex.map(one, range(n)):
            res.append(item)
    by = {}
    for code, _ in res:
        by[str(code)] = by.get(str(code), 0) + 1
    ms = [m for _, m in res]
    print(json.dumps({"n": n, "concurrency": conc, "status": by,
                      "p50": pct(ms, 50), "p95": pct(ms, 95), "max": max(ms)}, ensure_ascii=False))
    return 0


def cmd_cleanup():
    # 只删本次压测的会话与消息；账号由 SQL 侧统一清理
    import subprocess
    sql = (
        "DELETE m FROM chat_message_202609 m JOIN chat_session s ON s.id=m.session_id "
        "WHERE s.title LIKE 'ltdrill%';"
        "DELETE FROM chat_session WHERE title LIKE 'ltdrill%';"
        "SELECT ROW_COUNT();"
    )
    print("请用 SQL 清理：", sql)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "smoke"
    if cmd == "smoke":
        sys.exit(cmd_smoke())
    elif cmd == "sessions":
        cmd_sessions(int(sys.argv[2]))
    elif cmd == "sse":
        sys.exit(cmd_sse(int(sys.argv[2]), int(sys.argv[3]) if len(sys.argv) > 3 else 1))
    elif cmd == "ws":
        sys.exit(cmd_ws(int(sys.argv[2]), int(sys.argv[3]) if len(sys.argv) > 3 else 20))
    elif cmd == "login":
        sys.exit(cmd_login(int(sys.argv[2]), int(sys.argv[3]) if len(sys.argv) > 3 else 20))
    elif cmd == "cleanup":
        cmd_cleanup()
    else:
        raise SystemExit("unknown cmd")
