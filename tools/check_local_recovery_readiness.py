#!/usr/bin/env python3
"""Read-only readiness check for local MegaRepo recovery approvals.

Matching observed bytes is not publisher authorization or permission to publish.
"""
import argparse
import json
import re
from pathlib import Path
from urllib.parse import urlparse

HASH = re.compile(r"sha256-[a-f0-9]{64}\Z", re.I)

def analyze(incidents, approvals):
    if not isinstance(incidents, list) or not isinstance(approvals, list):
        raise ValueError("incidents and approvals must be JSON arrays")
    rows = []
    for incident in incidents:
        if not isinstance(incident, dict):
            raise ValueError("incident must be an object")
        name = str(incident.get("plugin") or "").strip().casefold()
        source = str(incident.get("sourceId") or "").strip()
        candidates = [x for x in approvals if isinstance(x, dict) and
                      str(x.get("plugin") or "").strip().casefold() == name and
                      x.get("sourceId") == source]
        matching = []
        for item in candidates:
            uri = urlparse(str(item.get("evidenceUrl") or ""))
            digest = str(item.get("fileHash") or "")
            if (item.get("url") == incident.get("candidateUrl") and
                item.get("version") == incident.get("candidateVersion") and
                type(item.get("fileSize")) is int and item["fileSize"] > 0 and
                item["fileSize"] == incident.get("actualFileSize") and
                HASH.fullmatch(digest) and
                digest.lower() == str(incident.get("observedActualSHA256") or "").lower() and
                uri.scheme == "https" and uri.hostname and
                item.get("reviewedBy") and item.get("reviewedAt") and item.get("reason")):
                matching.append(item)
        rows.append({"plugin": name, "sourceId": source,
                     "status": "approval_record_present_requires_independent_review"
                     if matching else "not_approved",
                     "matchingRecordCount": len(matching),
                     "releaseAuthorized": False})
    return {"readOnly": True, "releaseAuthorized": False, "incidents": rows,
            "unapprovedCount": sum(x["status"] == "not_approved" for x in rows),
            "notice": "This check cannot authorize a binary, establish provenance, or publish. Existing integrity gate must independently verify packages."}

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--incidents", required=True, type=Path)
    parser.add_argument("--approvals", required=True, type=Path)
    args = parser.parse_args()
    print(json.dumps(analyze(json.loads(args.incidents.read_text()),
                             json.loads(args.approvals.read_text())), indent=2))

if __name__ == "__main__":
    main()
