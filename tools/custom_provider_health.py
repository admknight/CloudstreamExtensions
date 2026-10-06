#!/usr/bin/env python3
import argparse
import json
import re
import socket
from datetime import datetime, timezone
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

MAIN_URL_RE = re.compile(r'override\s+var\s+mainUrl\s*=\s*"([^"]+)"')
USER_AGENT = "Mozilla/5.0 (CloudstreamExtensions health check; +https://github.com/admknight/CloudstreamExtensions)"

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

def check(item, timeout):
    req = Request(
        item["url"],
        headers={
            "User-Agent": USER_AGENT,
            "Accept": "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8",
        },
        method="GET",
    )
    result = dict(item)
    try:
        with urlopen(req, timeout=timeout) as response:
            status = getattr(response, "status", None) or response.getcode()
            response.read(2048)
            result.update({
                "state": "ok" if 200 <= status < 400 else "fail",
                "httpStatus": status,
                "finalUrl": response.geturl(),
                "error": "",
            })
    except HTTPError as exc:
        state = "restricted" if exc.code in (401, 403, 429) else "fail"
        result.update({
            "state": state,
            "httpStatus": exc.code,
            "finalUrl": exc.geturl() or item["url"],
            "error": f"HTTP {exc.code}: {exc.reason}",
        })
    except (URLError, socket.timeout, TimeoutError) as exc:
        result.update({
            "state": "fail",
            "httpStatus": None,
            "finalUrl": item["url"],
            "error": str(getattr(exc, "reason", exc)),
        })
    except Exception as exc:
        result.update({
            "state": "fail",
            "httpStatus": None,
            "finalUrl": item["url"],
            "error": f"{type(exc).__name__}: {exc}",
        })
    return result

def render_markdown(results, generated_at):
    icon = {"ok": "✅", "restricted": "⚠️", "fail": "❌"}
    ok = sum(1 for x in results if x["state"] == "ok")
    restricted = sum(1 for x in results if x["state"] == "restricted")
    failed = sum(1 for x in results if x["state"] == "fail")
    lines = [
        "# Custom Provider Runtime Health",
        "",
        f"Generated: **{generated_at}**",
        "",
        f"Providers checked: **{len(results)}** · Healthy: **{ok}** · Restricted/anti-bot response: **{restricted}** · Failed: **{failed}**",
        "",
        "> This is an advisory website-availability check only. It does not modify production and does not prove playback works.",
        "",
        "| Provider | State | HTTP | Configured URL | Final URL / Error |",
        "| --- | --- | ---: | --- | --- |",
    ]
    for row in results:
        state = row["state"]
        http = row["httpStatus"] if row["httpStatus"] is not None else "—"
        detail = row["error"] or row.get("finalUrl") or row["url"]
        detail = str(detail).replace("|", "\\|")
        url = row["url"].replace("|", "\\|")
        lines.append(
            f"| {row['module']} | {icon.get(state, '❔')} {state} | {http} | {url} | {detail} |"
        )
    lines += [
        "",
        "## Interpretation",
        "",
        "- **ok**: the configured provider URL returned a normal 2xx/3xx HTTP response.",
        "- **restricted**: the site responded with 401/403/429; it is reachable but may be blocking automated requests.",
        "- **fail**: the URL returned another error, timed out, or could not be reached.",
        "",
    ]
    return "\n".join(lines)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", default=".")
    parser.add_argument("--timeout", type=int, default=15)
    parser.add_argument("--json", dest="json_path", default="custom-provider-health.json")
    parser.add_argument("--markdown", dest="markdown_path", default="CUSTOM_PROVIDER_HEALTH.md")
    args = parser.parse_args()

    root = Path(args.root)
    discovered = discover(root)
    results = [check(item, args.timeout) for item in discovered]
    generated_at = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

    payload = {
        "generatedAt": generated_at,
        "providerCount": len(results),
        "summary": {
            "ok": sum(1 for x in results if x["state"] == "ok"),
            "restricted": sum(1 for x in results if x["state"] == "restricted"),
            "failed": sum(1 for x in results if x["state"] == "fail"),
        },
        "providers": results,
    }

    Path(args.json_path).write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    Path(args.markdown_path).write_text(render_markdown(results, generated_at) + "\n", encoding="utf-8")

    print(json.dumps(payload["summary"]))
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
