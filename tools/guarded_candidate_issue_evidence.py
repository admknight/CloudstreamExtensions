#!/usr/bin/env python3
"""Fail-closed proof that a formerly blocked candidate is now the audited release.

A successful published-catalog checksum for a DIFFERENT version or binary is
not sufficient to close an incident about a rejected upstream candidate.
Existing manually authored issue text is never modified here.
"""
import json
import re
from urllib.parse import urlparse, unquote

BEGIN = "<!-- megarepo-candidate-evidence:start -->"
END = "<!-- megarepo-candidate-evidence:end -->"
DIGEST = re.compile(r"^sha256-[a-fA-F0-9]{64}$")
COMMIT = re.compile(r"^[a-fA-F0-9]{40}$")


def _matches_pinned_source(original_url, pinned_url):
    origin = urlparse(str(original_url or ""))
    selected = urlparse(str(pinned_url or ""))
    if any(x.scheme != "https" or x.netloc != "raw.githubusercontent.com"
           or x.username or x.password or x.query or x.fragment
           for x in (origin, selected)):
        return False
    a, b = origin.path.strip("/").split("/"), selected.path.strip("/").split("/")
    return bool(
        len(a) >= 4 and len(b) >= 4 and COMMIT.fullmatch(b[2])
        and (a[0].casefold(), a[1].casefold()) == (b[0].casefold(), b[1].casefold())
        and unquote("/".join(a[3:])) == unquote("/".join(b[3:]))
    )


def issue_candidate_resolution(body, plugin, provenance):
    """Return (eligible, reason). No candidate section means published-only issue.

    Candidate-specific issue can close only after the exact rejected candidate
    version, source and downloaded SHA-256 are in the audited immutable release.
    """
    if not isinstance(body, str) or not isinstance(plugin, dict) or not isinstance(provenance, dict):
        return False, "invalid_issue_or_catalog"
    left, right = body.count(BEGIN), body.count(END)
    if left == 0 and right == 0:
        return True, "published_audit_only"
    if left != 1 or right != 1 or body.index(BEGIN) >= body.index(END):
        return False, "malformed_candidate_evidence_boundaries"
    block = body.split(BEGIN, 1)[1].split(END, 1)[0]
    fence = re.escape(chr(96) * 3)
    parts = re.findall(r"(?ms)^" + fence + r"json\s*\n(.*?)\n" + fence + r"[ \t]*$", block)
    if len(parts) != 1:
        return False, "missing_or_multiple_candidate_json"
    try:
        candidate = json.loads(parts[0])
    except (ValueError, TypeError):
        return False, "invalid_candidate_json"
    if not isinstance(candidate, dict):
        return False, "invalid_candidate_record"
    key = str(plugin.get("internalName") or plugin.get("name") or "").strip().casefold()
    actual = str(candidate.get("observedActualSHA256") or "")
    size = candidate.get("actualFileSize")
    if (
        str(candidate.get("plugin") or "").strip().casefold() != key
        or candidate.get("sourceId") != provenance.get("sourceId")
        or type(candidate.get("candidateVersion")) is not int
        or type(plugin.get("version")) is not int
        or plugin["version"] != candidate["candidateVersion"]
        or type(size) is not int or size <= 0 or plugin.get("fileSize") != size
        or not DIGEST.fullmatch(actual)
        or actual.lower() != str(plugin.get("fileHash") or "").lower()
        or candidate.get("verificationStatus") != "hash_verified"
        or (candidate.get("declaredFileSize") is not None
            and candidate["declaredFileSize"] != size)
        or provenance.get("packageUrl") != plugin.get("url")
        or not _matches_pinned_source(candidate.get("candidateUrl"), plugin.get("url"))
    ):
        return False, "blocked_candidate_not_independently_published"
    return True, "exact_candidate_version_sha256_immutable_published"
