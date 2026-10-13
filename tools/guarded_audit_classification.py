#!/usr/bin/env python3
"""Classify hourly MegaRepo metadata drift without approving an upstream binary.

A commit-pinned, byte-verified *previous* release is a documented HOLD rather
than an unexpected publication failure. A precisely attested trusted publisher
upgrade may differ from the mutable upstream only in its immutable URL.
All raw differences stay in the audit evidence. New identity/source, version
without matching candidate fingerprints, unapproved digests, missing source
indexes, package-byte mismatches and unverified mutable fallbacks fail closed.

This classification is for the *rotating hourly monitor only*. It must never
replace the full strict post-publication audit or authorize issue closure.
"""
import re
from urllib.parse import urlparse

SHA_RE = re.compile(r"^[a-f0-9]{40}$", re.I)
DIGEST_RE = re.compile(r"^sha256-[a-f0-9]{64}$", re.I)
RELEASE_METHOD = "guarded_per_plugin_compatibility"
PIN_FIELDS = {"url", "version", "fileSize", "fileHash"}


def identity(item):
    return str(item.get("internalName") or item.get("name") or "").strip().casefold()


def origin_identity(item):
    return str(item.get("plugin") or item.get("originalName") or "").strip().casefold()


def normalized_url(value):
    return str(value or "").replace(" ", "%20")


def pinned_commit(url):
    parsed = urlparse(str(url or ""))
    parts = parsed.path.strip("/").split("/")
    if (parsed.scheme != "https"
            or parsed.netloc != "raw.githubusercontent.com"
            or parsed.username or parsed.password or parsed.query or parsed.fragment
            or len(parts) < 4 or SHA_RE.fullmatch(parts[2]) is None
            or not all(parts) or not parts[-1].lower().endswith(".cs3")):
        return None
    return (parts[0].casefold(), parts[1].casefold(), parts[2].lower())


def _by_identity(items, key_fn):
    if not isinstance(items, list):
        raise ValueError("Malformed auditor identity ledger")
    indexed = {}
    for item in items:
        if not isinstance(item, dict):
            raise ValueError("Malformed auditor identity record")
        key = key_fn(item)
        if not key or key in indexed:
            raise ValueError("Missing/duplicate auditor identity")
        indexed[key] = item
    return indexed


def _exact_published_differences(drift, published):
    diffs = drift.get("differences")
    if (drift.get("problem") != "stale_metadata"
            or not isinstance(diffs, dict) or not diffs
            or not set(diffs).issubset(PIN_FIELDS | {"status"})):
        return False
    for field, change in diffs.items():
        if (not isinstance(change, dict)
                or set(change) != {"published", "upstream"}
                or change["published"] != published.get(field)):
            return False
    return True


def _known_verified_hold(drift, current, origin, incident, *, exact_candidate_bytes=True):
    if not isinstance(incident, dict) or not _exact_published_differences(drift, current):
        return False
    fields = drift["differences"]
    key = identity(current)
    source = origin.get("sourceId")
    pin = pinned_commit(current.get("url"))
    if (not pin or "url" not in fields or "status" in fields
            or incident.get("disposition") != "retained_immutable_previous"
            or incident.get("fallbackVerified") is not True
            or incident.get("fallbackThroughRecoveryLock") is not True
            or str(incident.get("plugin") or "").casefold() != key
            or incident.get("fallbackPin") != current.get("url")
            or incident.get("previousUrl") != current.get("url")
            or incident.get("previousSourceId") != source
            or incident.get("candidateSourceId") != source
            or normalized_url(fields["url"]["upstream"])
               != normalized_url(incident.get("candidateUrl"))
            or fields["url"]["published"] != current.get("url")
            or type(current.get("fileSize")) is not int
            or current["fileSize"] <= 0
            or DIGEST_RE.fullmatch(str(current.get("fileHash") or "")) is None
            or type(incident.get("observedActualSize")) is not int
            or incident["observedActualSize"] <= 0
            or DIGEST_RE.fullmatch(str(incident.get("observedActualSHA256") or "")) is None):
        return False
    if exact_candidate_bytes:
        for field, recorded in (("fileSize", "observedActualSize"),
                                ("fileHash", "observedActualSHA256")):
            if field in fields:
                from_upstream = fields[field]["upstream"]
                expected = incident[recorded]
                if field == "fileHash":
                    if str(from_upstream).lower() != str(expected).lower():
                        return False
                elif from_upstream != expected:
                    return False
    return True


def _known_policy_url_only(drift, current, origin, attestation):
    if not isinstance(attestation, dict) or not _exact_published_differences(drift, current):
        return False
    delta = drift["differences"]
    commit = pinned_commit(current.get("url"))
    if (set(delta) != {"url"} or commit is None
            or attestation.get("authorizationKind") != "trusted_upstream_policy_v1"
            or attestation.get("pinnedIndexVerified") is not True
            or attestation.get("separateBytesVerified") is not True
            or attestation.get("plugin") != identity(current)
            or attestation.get("sourceId") != origin.get("sourceId")
            or attestation.get("url") != current.get("url")
            or attestation.get("version") != current.get("version")
            or attestation.get("fileSize") != current.get("fileSize")
            or str(attestation.get("fileHash") or "").lower()
               != str(current.get("fileHash") or "").lower()
            or str(attestation.get("immutableCommit") or "").lower() != commit[2]
            or normalized_url(attestation.get("originalUrl"))
               != normalized_url(delta["url"]["upstream"])
            or delta["url"]["published"] != current.get("url")
            or origin.get("packageUrl") != current.get("url")
            or type(current.get("fileSize")) is not int
            or current["fileSize"] <= 0
            or DIGEST_RE.fullmatch(str(current.get("fileHash") or "")) is None):
        return False
    index = urlparse(str(attestation.get("pinnedIndexUrl") or ""))
    parts = index.path.strip("/").split("/")
    return (
        index.scheme == "https"
        and index.netloc == "raw.githubusercontent.com"
        and not index.query and not index.fragment
        and len(parts) == 4 and parts[-1] == "plugins.json"
        and (parts[0].casefold(), parts[1].casefold(), parts[2].lower()) == commit
    )


def classify_drift(published, provenance, report, drift):
    """Return known and unexpected ledgers. Invalid report fails closed."""
    plugins = _by_identity(published, identity)
    origins = _by_identity(provenance, origin_identity)
    if set(plugins) != set(origins) or not plugins:
        raise ValueError("Published plugins and provenance are incomplete")
    if not isinstance(report, dict) or not isinstance(drift, list):
        raise ValueError("Missing guarded release and drift records")
    if (report.get("releaseEligible") is not True
            or report.get("publicationMethod") != RELEASE_METHOD
            or report.get("uniquePlugins") != len(plugins)
            or report.get("sourceHealth", {}).get("failed") != 0
            or report.get("changes", {}).get("removed") != 0):
        raise ValueError("Missing complete guarded publication assurance")
    incidents = _by_identity(report.get("quarantineIncidents"), lambda r: str(r.get("plugin") or "").casefold())
    approvals = _by_identity(report.get("trustedPolicyUpgrades"), lambda r: str(r.get("plugin") or "").casefold())
    holds = report.get("deferredUnverified")
    if not isinstance(holds, list) or not isinstance(report.get("integrityHealth"), dict):
        raise ValueError("Incomplete guard exception counters")
    if (len(holds) != report["integrityHealth"].get("unverifiedPreviousCarried")
            or len(holds) != len({str(x.get("plugin") or "").casefold() for x in holds if isinstance(x, dict)})):
        raise ValueError("Inconsistent deferred mutable release ledger")
    if report["integrityHealth"].get("pinnedPreviousRecovered") != sum(
        bool(row.get("fallbackThroughRecoveryLock"))
        for row in incidents.values()
        if row.get("disposition") == "retained_immutable_previous"
    ):
        raise ValueError("Pinned previous release evidence count differs from production report")
    known, pending_new_upstream, unexpected = [], [], []
    seen = set()
    for row in drift:
        if not isinstance(row, dict):
            raise ValueError("Malformed upstream drift evidence")
        key = str(row.get("plugin") or "").casefold()
        if not key or key in seen:
            raise ValueError("Duplicated or missing metadata-drift identity")
        seen.add(key)
        current, origin = plugins.get(key), origins.get(key)
        kind = None
        if (current is not None and origin is not None
                and row.get("sourceId") == origin.get("sourceId")):
            if _known_verified_hold(row, current, origin, incidents.get(key)):
                kind = "immutable_sha256_verified_previous_held"
            elif _known_verified_hold(row, current, origin, incidents.get(key),
                                      exact_candidate_bytes=False):
                # Candidate bytes changed AFTER the last guarded build.
                # The installer still points to the OLD commit-pinned release,
                # independently byte-verified by the guarded release evidence.
                # This is pending UNTRUSTED upstream metadata, not an approval.
                kind = "new_upstream_candidate_untrusted_prior_immutable_pin_held"
            elif _known_policy_url_only(row, current, origin, approvals.get(key)):
                kind = "trusted_upstream_commit_pinned_url_only"
        item = {"plugin": key, "classification": kind, "originalFinding": row}
        if kind == "new_upstream_candidate_untrusted_prior_immutable_pin_held" and not holds:
            pending_new_upstream.append(item)
        elif kind and not holds:
            known.append(item)
        else:
            unexpected.append(row)
    return {
        "known": known,
        "pendingUntrustedUpstream": pending_new_upstream,
        "unexpected": unexpected,
        "knownCount": len(known),
        "pendingUntrustedUpstreamCount": len(pending_new_upstream),
        "unexpectedCount": len(unexpected),
        "unverifiedMutableHeldCount": len(holds),
        "independentFullAuditPerformed": False,
        "warning": (
            "A changed upstream digest is UNTRUSTED, has NOT been adopted, and " 
            "requires normal independent provenance plus byte verification. "
            "Previously verified immutable versions remain pinned rather than promoted. "
            "this rotating monitor does not certify upstream code, provider playback "
            "or a clean full post-publication audit."
        ),
    }
