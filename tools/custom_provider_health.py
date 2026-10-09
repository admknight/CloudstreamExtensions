#!/usr/bin/env python3
import argparse
import json
import re
import socket
import time
from datetime import datetime, timedelta, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen
from urllib.parse import urlsplit

MAIN_URL_RE = re.compile(r'override\s+var\s+mainUrl\s*=\s*"([^"]+)"')
USER_AGENT = "Mozilla/5.0 (CloudstreamExtensions health check; +https://github.com/admknight/CloudstreamExtensions)"
TIME_FORMAT = "%Y-%m-%d %H:%M:%S UTC"

def discover(root: Path):
    rows = []
    for path in sorted(root.glob("*/src/main/kotlin/**/*.kt")):
        text = path.read_text(encoding="utf-8", errors="ignore")
        match = MAIN_URL_RE.search(text)
        if not match:
            continue
        rows.append({
            "module": path.parts[0],
            "source": str(path),
            "url": match.group(1).strip(),
        })
    return rows

def navigation_details(original, final):
    """Describe observed redirects without changing the configured site URL."""
    try:
        old = urlsplit(original or "")
        new = urlsplit(final or "")
        old_host = (old.hostname or "").lower()
        new_host = (new.hostname or "").lower()
        if not old_host or not new_host:
            return {"redirected": False, "hostChanged": False,
                    "configuredHost": old_host, "finalHost": new_host}
        return {
            "redirected": original.rstrip("/") != final.rstrip("/"),
            "hostChanged": old_host != new_host,
            "configuredHost": old_host,
            "finalHost": new_host,
        }
    except ValueError:
        return {"redirected": False, "hostChanged": False,
                "configuredHost": "", "finalHost": ""}


def classify_network_error(exc):
    reason = getattr(exc, "reason", exc)
    if isinstance(reason, socket.gaierror):
        return "dns_error"
    if isinstance(reason, (socket.timeout, TimeoutError)):
        return "timeout"
    message = str(reason).lower()
    if any(mark in message for mark in
           ("name or service not known", "nodename nor servname",
            "getaddrinfo failed", "temporary failure in name resolution")):
        return "dns_error"
    if "timed out" in message or "timeout" in message:
        return "timeout"
    return "network_error"


def check(item, timeout, attempts=3):
    result = dict(item)
    last_error = ""
    for attempt in range(1, attempts + 1):
        req = Request(
            item["url"],
            headers={
                "User-Agent": USER_AGENT,
                "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
            },
            method="GET",
        )
        try:
            with urlopen(req, timeout=timeout) as response:
                status = getattr(response, "status", None) or response.getcode()
                response.read(2048)
                result.update({
                    "state": "ok" if 200 <= status < 400 else "fail",
                    "httpStatus": status,
                    "finalUrl": response.geturl(),
                    "error": "",
                    "attempts": attempt,
                    "outcome": "ok" if 200 <= status < 400 else "http_error",
                })
                result.update(navigation_details(item["url"], result["finalUrl"]))
                return result
        except HTTPError as exc:
            state = "restricted" if exc.code in (401, 403, 429) else "fail"
            result.update({
                "state": state,
                "httpStatus": exc.code,
                "finalUrl": exc.geturl() or item["url"],
                "error": f"HTTP {exc.code}: {exc.reason}",
                "attempts": attempt,
                "outcome": "http_restricted" if state == "restricted" else "http_error",
            })
            result.update(navigation_details(item["url"], result["finalUrl"]))
            if state == "restricted" or exc.code in (404, 410):
                return result
            last_error = result["error"]
        except (URLError, socket.timeout, TimeoutError) as exc:
            last_error = str(getattr(exc, "reason", exc))
            result.update({
                "state": "fail",
                "httpStatus": None,
                "finalUrl": item["url"],
                "error": last_error,
                "attempts": attempt,
                "outcome": classify_network_error(exc),
            })
            result.update(navigation_details(item["url"], result["finalUrl"]))
        except Exception as exc:
            last_error = f"{type(exc).__name__}: {exc}"
            result.update({
                "state": "fail",
                "httpStatus": None,
                "finalUrl": item["url"],
                "error": last_error,
                "attempts": attempt,
                "outcome": "unexpected_error",
            })
            result.update(navigation_details(item["url"], result["finalUrl"]))

        if attempt < attempts:
            time.sleep(attempt * 2)

    result["error"] = last_error or result.get("error", "Unknown failure")
    return result

def render_markdown(results, generated_at):
    icon = {"ok": "✅", "restricted": "⚠️", "fail": "❌"}
    ok = sum(1 for x in results if x["state"] == "ok")
    restricted = sum(1 for x in results if x["state"] == "restricted")
    failed = sum(1 for x in results if x["state"] == "fail")
    drift = sum(bool(x.get("hostChanged")) for x in results)
    redirected = sum(bool(x.get("redirected")) for x in results)
    dns = sum(x.get("outcome") == "dns_error" for x in results)
    timeouts = sum(x.get("outcome") == "timeout" for x in results)
    lines = [
        "# Custom Provider Runtime Health",
        "",
        f"Generated: **{generated_at}**",
        "",
        f"Providers checked: **{len(results)}** · Healthy: **{ok}** · Restricted/anti-bot response: **{restricted}** · Failed: **{failed}**",
        f"Observed redirects: **{redirected}** · Hostname changes: **{drift}** · DNS failures: **{dns}** · Timeouts: **{timeouts}**",
        "",
        "> This is an advisory website-availability check only. It does not modify production and does not prove playback works.",
        "",
        "| Provider | State | Outcome | HTTP | Host drift | Configured URL | Final URL / Error |",
        "| --- | --- | --- | ---: | --- | --- | --- |",
    ]
    for row in results:
        state = row["state"]
        http = row["httpStatus"] if row["httpStatus"] is not None else "—"
        detail = row.get("error") or row.get("finalUrl") or row["url"]
        detail = str(detail).replace("|", "\\|")
        url = row["url"].replace("|", "\\|")
        drift_note = (row.get("configuredHost", "") + " → " + row.get("finalHost", "")) if row.get("hostChanged") else "—"
        lines.append(
            f"| {row['module']} | {icon.get(state, '❔')} {state} | {row.get('outcome', 'unknown')} | {http} | {drift_note} | {url} | {detail} |"
        )
    lines += [
        "",
        "## Interpretation",
        "",
        "- **ok**: the configured provider URL returned a normal 2xx/3xx HTTP response.",
        "- **restricted**: the site responded with 401/403/429; it is reachable but may be blocking automated requests.",
        "- **fail**: the URL returned another error, timed out, or could not be reached.",
        "- **host drift**: a request ended on a different hostname. This is advisory, not an automatically approved new base URL.",
        "- **outcome**: HTTP restrictions, HTTP errors, DNS failures, network failures and timeouts are distinguished.",
        "",
    ]
    return "\n".join(lines)

def parse_time(value):
    try:
        return datetime.strptime(value, TIME_FORMAT).replace(tzinfo=timezone.utc)
    except Exception:
        return None

def load_history(path):
    if not path:
        return {"version": 1, "updatedAt": None, "snapshots": []}
    p = Path(path)
    if not p.exists():
        return {"version": 1, "updatedAt": None, "snapshots": []}
    try:
        data = json.loads(p.read_text(encoding="utf-8"))
        if not isinstance(data, dict) or not isinstance(data.get("snapshots"), list):
            raise ValueError("Invalid history structure")
        return data
    except Exception:
        return {"version": 1, "updatedAt": None, "snapshots": []}

def compact_provider(row):
    return {
        "module": row.get("module"),
        "state": row.get("state"),
        "httpStatus": row.get("httpStatus"),
        "configuredUrl": row.get("url"),
        "finalUrl": row.get("finalUrl"),
        "attempts": row.get("attempts"),
        "error": row.get("error") or "",
        "outcome": row.get("outcome") or "unknown",
        "redirected": bool(row.get("redirected")),
        "hostChanged": bool(row.get("hostChanged")),
        "configuredHost": row.get("configuredHost") or "",
        "finalHost": row.get("finalHost") or "",
    }

def make_snapshot(payload):
    return {
        "generatedAt": payload["generatedAt"],
        "summary": payload["summary"],
        "providers": [compact_provider(x) for x in payload["providers"]],
    }

def build_window(snapshots, now, days):
    cutoff = now - timedelta(days=days)
    selected = []
    for snap in snapshots:
        stamp = parse_time(snap.get("generatedAt", ""))
        if stamp and stamp >= cutoff:
            selected.append(snap)

    provider_stats = {}
    total = {"checks": 0, "ok": 0, "restricted": 0, "failed": 0,
             "redirected": 0, "hostChanged": 0, "dnsErrors": 0, "timeouts": 0}
    for snap in selected:
        for row in snap.get("providers", []):
            module = row.get("module") or "Unknown"
            stats = provider_stats.setdefault(
                module,
                {"checks": 0, "ok": 0, "restricted": 0, "failed": 0,
                 "redirected": 0, "hostChanged": 0, "dnsErrors": 0, "timeouts": 0},
            )
            state = row.get("state")
            navigation = navigation_details(row.get("configuredUrl") or row.get("url"), row.get("finalUrl"))
            for name, active in (
                ("redirected", bool(row.get("redirected", navigation["redirected"]))),
                ("hostChanged", bool(row.get("hostChanged", navigation["hostChanged"]))),
                ("dnsErrors", row.get("outcome") == "dns_error"),
                ("timeouts", row.get("outcome") == "timeout"),
            ):
                if active:
                    stats[name] += 1
                    total[name] += 1
            stats["checks"] += 1
            total["checks"] += 1
            if state == "ok":
                stats["ok"] += 1
                total["ok"] += 1
            elif state == "restricted":
                stats["restricted"] += 1
                total["restricted"] += 1
            else:
                stats["failed"] += 1
                total["failed"] += 1

    def percentages(stats):
        checks = stats["checks"]
        healthy = round((stats["ok"] / checks) * 100, 1) if checks else None
        reachable = round(((stats["ok"] + stats["restricted"]) / checks) * 100, 1) if checks else None
        return healthy, reachable

    providers = []
    for module, stats in sorted(provider_stats.items()):
        healthy, reachable = percentages(stats)
        providers.append({
            "module": module,
            **stats,
            "healthyPct": healthy,
            "reachablePct": reachable,
        })

    healthy, reachable = percentages(total)
    return {
        "days": days,
        "snapshotCount": len(selected),
        **total,
        "healthyPct": healthy,
        "reachablePct": reachable,
        "providers": providers,
    }

def build_trends(snapshots, now):
    recent = []
    for snap in snapshots[-10:]:
        summary = snap.get("summary") or {}
        recent.append({
            "generatedAt": snap.get("generatedAt"),
            "ok": summary.get("ok", 0),
            "restricted": summary.get("restricted", 0),
            "failed": summary.get("failed", 0),
            "hostChanged": summary.get("hostChanged", 0),
            "redirected": summary.get("redirected", 0),
        })
    return {
        "generatedAt": now.strftime(TIME_FORMAT),
        "windows": {
            "7d": build_window(snapshots, now, 7),
            "30d": build_window(snapshots, now, 30),
        },
        "recent": recent,
    }

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--timeout", type=int, default=15)
    parser.add_argument("--json", dest="json_path", default="custom-provider-health.json")
    parser.add_argument("--markdown", dest="markdown_path", default="CUSTOM_PROVIDER_HEALTH.md")
    parser.add_argument("--history-in", default="")
    parser.add_argument("--history-out", default="")
    parser.add_argument("--latest-out", default="")
    parser.add_argument("--trends-out", default="")
    parser.add_argument("--max-history", type=int, default=180)
    args = parser.parse_args()

    root = Path(args.root)
    discovered = discover(root)
    results = [check(item, args.timeout) for item in discovered]
    now = datetime.now(timezone.utc)
    generated_at = now.strftime(TIME_FORMAT)

    payload = {
        "generatedAt": generated_at,
        "providerCount": len(results),
        "summary": {
            "ok": sum(1 for x in results if x["state"] == "ok"),
            "restricted": sum(1 for x in results if x["state"] == "restricted"),
            "failed": sum(1 for x in results if x["state"] == "fail"),
            "redirected": sum(bool(x.get("redirected")) for x in results),
            "hostChanged": sum(bool(x.get("hostChanged")) for x in results),
            "dnsErrors": sum(x.get("outcome") == "dns_error" for x in results),
            "timeouts": sum(x.get("outcome") == "timeout" for x in results),
        },
        "providers": results,
    }

    Path(args.json_path).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown_path).write_text(render_markdown(results, generated_at) + "\n", encoding="utf-8")

    if args.history_out or args.latest_out or args.trends_out:
        history = load_history(args.history_in)
        snapshots = history.get("snapshots", [])
        snapshot = make_snapshot(payload)
        snapshots = [x for x in snapshots if x.get("generatedAt") != generated_at]
        snapshots.append(snapshot)
        snapshots = sorted(
            snapshots,
            key=lambda x: parse_time(x.get("generatedAt", "")) or datetime.min.replace(tzinfo=timezone.utc),
        )[-max(1, args.max_history):]

        history_payload = {
            "version": 1,
            "updatedAt": generated_at,
            "snapshots": snapshots,
        }

        if args.history_out:
            Path(args.history_out).write_text(
                json.dumps(history_payload, indent=2) + "\n",
                encoding="utf-8",
            )
        if args.latest_out:
            Path(args.latest_out).write_text(
                json.dumps(payload, indent=2) + "\n",
                encoding="utf-8",
            )
        if args.trends_out:
            Path(args.trends_out).write_text(
                json.dumps(build_trends(snapshots, now), indent=2) + "\n",
                encoding="utf-8",
            )

    print(json.dumps(payload["summary"]))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
