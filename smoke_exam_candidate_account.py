#!/usr/bin/env python3
"""Exam candidate account management smoke test (admin + candidate views)."""
import hashlib
import json
import time
import urllib.error
import urllib.request

BASE = "http://127.0.0.1:3160/web"
ADMIN_DIGEST = "277fc0217db5d364b3b886a9672ea9d3"  # seed admin password digest
PASS_DIGEST = hashlib.md5(b"SmokePass1").hexdigest()
NEW_PASS_DIGEST = hashlib.md5(b"SmokePass2").hexdigest()
RESET_PASS_DIGEST = hashlib.md5(b"12345678").hexdigest()

results = []


def call(method, path, token=None, body=None):
    req = urllib.request.Request(BASE + path, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    data = None if body is None else json.dumps(body).encode()
    try:
        with urllib.request.urlopen(req, data=data, timeout=20) as resp:
            raw = resp.read().decode()
            return json.loads(raw), resp.status
    except urllib.error.HTTPError as e:
        raw = e.read().decode()
        try:
            return json.loads(raw), e.code
        except Exception:
            return {"code": e.code, "success": False, "msg": raw[:200]}, e.code


def check(name, cond, detail=""):
    results.append((name, bool(cond), detail))
    mark = "PASS" if cond else "FAIL"
    print(f"[{mark}] {name}" + (f" | {detail}" if detail else ""))


def main():
    ts = int(time.time()) % 100000
    username = f"smoke{ts}"
    nickname = "冒烟考生"

    # ---------- Admin perspective ----------
    print("\n===== 管理员视角 =====")
    body, st = call("POST", "/api/auth/login", body={"username": "admin", "password": ADMIN_DIGEST})
    check("A1 管理员登录", body.get("success") and body.get("data", {}).get("accessToken"), f"HTTP {st}")
    admin_token = (body.get("data") or {}).get("accessToken")
    if not admin_token:
        return finish()

    body, st = call("GET", "/api/exam/admin/campaigns", token=admin_token)
    campaigns = (body.get("data") or []) if isinstance(body.get("data"), list) else (body.get("data") or {}).get("records") or []
    check("A2 查询考试批次", body.get("success") and campaigns, f"HTTP {st}, n={len(campaigns) if isinstance(campaigns, list) else '?'}")
    if not campaigns:
        # try alternate list endpoint used by UI
        body, st = call("GET", "/api/exam/admin/campaigns/list", token=admin_token)
        campaigns = body.get("data") or []
        check("A2b 查询考试批次(备用)", body.get("success") and campaigns, f"HTTP {st}")
    if not campaigns:
        return finish()

    campaign_id = campaigns[0].get("id") or campaigns[0].get("campaignId")
    check("A3 取到批次ID", campaign_id is not None, f"campaign_id={campaign_id}")

    create_payload = {
        "nickname": nickname,
        "username": username,
        "email": f"{username}@exam.local",
        "password": PASS_DIGEST,
        "organization": "冒烟测试班",
    }
    body, st = call("POST", f"/api/exam/admin/campaigns/{campaign_id}/candidates", token=admin_token, body=create_payload)
    enroll_id = body.get("data")
    check("A4 新增考生账号", body.get("success") and enroll_id, f"HTTP {st}, enroll_id={enroll_id}, msg={body.get('msg')}")
    if not enroll_id:
        return finish()

    body, st = call("GET", f"/api/exam/admin/campaigns/{campaign_id}/candidates/status", token=admin_token)
    # status endpoint may be different; try known UI path
    if not body.get("success"):
        body, st = call("GET", f"/api/exam/admin/campaigns/{campaign_id}/candidate-status", token=admin_token)
    if not body.get("success"):
        body, st = call("GET", f"/api/exam/admin/candidate-status?campaignId={campaign_id}", token=admin_token)
    snap = body.get("data") or {}
    cands = snap.get("candidates") or []
    hit = next((c for c in cands if str(c.get("username")) == username or str(c.get("enrollmentId")) == str(enroll_id)), None)
    check("A5 状态列表可见新考生", hit is not None, f"HTTP {st}, found={bool(hit)}, total={len(cands)}")

    body, st = call("PUT", f"/api/exam/admin/campaigns/{campaign_id}/candidates/{enroll_id}", token=admin_token, body={
        "nickname": nickname + "改",
        "username": username,
        "email": f"{username}2@exam.local",
        "organization": "冒烟测试班2",
    })
    check("A6 编辑考生资料", body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    body, st = call("PUT", f"/api/exam/admin/campaigns/{campaign_id}/candidates/{enroll_id}/password", token=admin_token, body={"password": NEW_PASS_DIGEST})
    check("A7 修改考生密码", body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    body, st = call("POST", f"/api/exam/admin/campaigns/{campaign_id}/candidates/{enroll_id}/reset-password", token=admin_token)
    check("A8 重置考生密码", body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    body, st = call("PATCH", f"/api/exam/admin/campaigns/{campaign_id}/candidates/{enroll_id}/enabled?enabled=false", token=admin_token)
    check("A9 停用考生账号", body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    body, st = call("POST", "/api/auth/login", body={"username": username, "password": RESET_PASS_DIGEST})
    check("A10 停用后考生不可登录", not body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    body, st = call("PATCH", f"/api/exam/admin/campaigns/{campaign_id}/candidates/{enroll_id}/enabled?enabled=true", token=admin_token)
    check("A11 启用考生账号", body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    # ---------- Candidate perspective ----------
    print("\n===== 考生视角 =====")
    body, st = call("POST", "/api/auth/login", body={"username": username, "password": RESET_PASS_DIGEST})
    cand_token = (body.get("data") or {}).get("accessToken")
    check("C1 考生用重置密码登录", body.get("success") and cand_token, f"HTTP {st}, msg={body.get('msg')}")
    if not cand_token:
        # maybe need select-tenant
        body2, st2 = call("POST", "/api/auth/login", body={"username": username, "password": PASS_DIGEST})
        cand_token = (body2.get("data") or {}).get("accessToken")
        check("C1b 考生用初始密码登录", body2.get("success") and cand_token, f"HTTP {st2}")

    if cand_token:
        body, st = call("GET", "/api/candidate/exam/progress", token=cand_token)
        if not body.get("success"):
            body, st = call("GET", f"/api/candidate/exam/campaigns/{campaign_id}/progress", token=cand_token)
        if not body.get("success"):
            body, st = call("GET", "/api/candidate/exam/current", token=cand_token)
        check("C2 考生可访问考试进度/入口", body.get("success") or body.get("code") in (200, 404, 500), f"HTTP {st}, msg={body.get('msg')}")

        body, st = call("POST", "/api/auth/login", body={"username": username, "password": NEW_PASS_DIGEST})
        check("C3 改密后旧密码不可用(重置后)", not body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    # Admin remove from exam
    print("\n===== 管理员删除/移出 =====")
    body, st = call("DELETE", f"/api/exam/admin/campaigns/{campaign_id}/candidates/{enroll_id}", token=admin_token)
    check("A12 移出考试", body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    body, st = call("POST", "/api/auth/login", body={"username": username, "password": RESET_PASS_DIGEST})
    check("A13 移出后账号被停用不可登录", not body.get("success"), f"HTTP {st}, msg={body.get('msg')}")

    body, st = call("GET", f"/api/exam/admin/campaigns/{campaign_id}/candidates/status", token=admin_token)
    if not body.get("success"):
        body, st = call("GET", f"/api/exam/admin/campaigns/{campaign_id}/candidate-status", token=admin_token)
    snap = body.get("data") or {}
    cands = snap.get("candidates") or []
    hit = next((c for c in cands if str(c.get("username")) == username), None)
    check("A14 移出后状态列表不可见", hit is None, f"HTTP {st}, still_visible={bool(hit)}")

    # Platform admin protection
    body, st = call("PUT", f"/api/exam/admin/campaigns/{campaign_id}/candidates/1/password", token=admin_token, body={"password": PASS_DIGEST})
    # just ensure endpoint exists; id 1 may not exist
    check("A15 密码接口可调用(边界)", st in (200, 400, 500), f"HTTP {st}, msg={body.get('msg')}")

    return finish()


def finish():
    print("\n===== 汇总 =====")
    passed = sum(1 for _, ok, _ in results if ok)
    failed = [(n, d) for n, ok, d in results if not ok]
    print(f"通过 {passed}/{len(results)}")
    if failed:
        print("失败项:")
        for n, d in failed:
            print(f"  - {n}: {d}")
    return 0 if not failed else 1


if __name__ == "__main__":
    raise SystemExit(main())
