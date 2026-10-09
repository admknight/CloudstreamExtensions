"""Validate full-scan issue sync without losing existing audit or manual notes."""
import copy
import json
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import sync_candidate_integrity_issues as candidate
import sync_integrity_issues as published
from validate_integrity_review_origin import validate_origin


def report():
    return {
        "releaseAuthorized": False,
        "candidateStatus": "REVIEW ONLY - NOT APPROVED FOR PUBLICATION",
        "previewPolicy": "compatibility",
        "sourceHealth": {"ok":35, "failed":0},
        "integrityHealth": {
            "candidateChecked": 543, "changedOrFailedCandidates": 1
        },
        "quarantineIncidents": [
            {"plugin":"gdindex","disposition":"quarantined_existing"}
        ],
    }


def verification():
    return {"checked":543,"blockedCount":1,"blocked":[{
        "plugin":"gdindex","sourceId":"shakzz-recovery",
        "version":4,"url":"https://raw.githubusercontent.com/a/b/builds/GDIndex.cs3",
        "expectedFileSize":15937,"actualFileSize":17206,
        "actualFileHash":"sha256-" + "a" * 64,
        "reason":"download verification failed: fileSize",
        "verification":"mismatch",
    }]}


def old_issue(body, closed=False):
    return {"number":18,"title":"[Integrity] megarepo-integrity:gdindex-57c0162511",
            "body":body,"state":"CLOSED" if closed else "OPEN"}


class CandidateIncidentTests(unittest.TestCase):
    def test_fresh_issue_creation(self):
        calls=[]
        def runner(args):
            calls.append(args)
            return "[]" if args[2]=="list" else "https://github.com/test/issues/18"
        r=candidate.sync(report(),verification(),37970591208,runner)
        self.assertEqual(r[0]["action"],"created")
        self.assertEqual(len([x for x in calls if x[2]=="create"]),1)
        self.assertFalse(any(x[2]=="close" for x in calls))

    def test_existing_manual_context_preserved(self):
        body=candidate.MANAGED+"\nOriginal investigation and maintainer reply"
        calls=[]
        def runner(args):
            calls.append(args)
            return json.dumps([old_issue(body)]) if args[2]=="list" else ""
        result=candidate.sync(report(),verification(),37970591208,runner)
        self.assertEqual(result[0]["action"],"updated")
        edited=next(x for x in calls if x[2]=="edit")[-1]
        self.assertIn("Original investigation and maintainer reply",edited)
        self.assertIn(candidate.BEGIN,edited)
        self.assertEqual(edited.count(candidate.BEGIN),1)
        self.assertNotIn("automaticPublicationAuthorized: true",edited)

    def test_reprocessing_identical_evidence_is_idempotent(self):
        beginning=candidate.MANAGED+"\nManual notes"
        block=candidate._evidence(verification()["blocked"][0],37970591208)
        body=candidate._upsert_block(beginning,block)
        calls=[]
        def runner(args):
            calls.append(args)
            return json.dumps([old_issue(body)]) if args[2]=="list" else ""
        result=candidate.sync(report(),verification(),37970591208,runner)
        self.assertEqual(result[0]["action"],"unchanged")
        self.assertEqual(len(calls),1)

    def test_new_observation_replaces_only_machine_block(self):
        original=candidate.MANAGED+"\nExternal maintainer reply"
        previous=candidate._upsert_block(original,candidate._evidence(verification()["blocked"][0],101))
        updated=candidate._upsert_block(previous,candidate._evidence(verification()["blocked"][0],102))
        self.assertEqual(updated.count(candidate.BEGIN),1)
        self.assertIn("External maintainer reply",updated)
        self.assertIn("runs/102",updated)
        self.assertNotIn("runs/101",updated)

    def test_existing_closed_incident_reopens_not_duplicates(self):
        calls=[]
        def runner(args):
            calls.append(args)
            return json.dumps([old_issue(candidate.MANAGED,True)]) if args[2]=="list" else ""
        candidate.sync(report(),verification(),37970591208,runner)
        self.assertTrue(any(x[2]=="reopen" for x in calls))
        self.assertFalse(any(x[2]=="create" for x in calls))

    def test_untrusted_or_incomplete_inputs_rejected(self):
        for change in ("bad_publish","wrong_counts","missing_source","missing_selection","bad_source_health"):
            r,v=report(),verification()
            if change=="bad_publish":r["releaseAuthorized"]=True
            if change=="wrong_counts":v["checked"]=5
            if change=="missing_source":del v["blocked"][0]["sourceId"]
            if change=="missing_selection":r["quarantineIncidents"]=[]
            if change=="bad_source_health":r["sourceHealth"]["failed"]=1
            with self.subTest(change=change):
                with self.assertRaises(ValueError):
                    candidate.validate(r,v)

    def test_bad_issue_boundary_fails_closed(self):
        with self.assertRaisesRegex(ValueError,"Malformed"):
            candidate._upsert_block(candidate.BEGIN,"new")
        with self.assertRaisesRegex(ValueError,"Malformed"):
            candidate._upsert_block(candidate.END,"new")

    def test_hourly_published_audit_preserves_candidate_report(self):
        body=candidate.MANAGED+"\nManual upstream release evidence"
        body=candidate._upsert_block(body,candidate._evidence(verification()["blocked"][0],37970591208))
        newer=published._update_report_preserving_manual_notes(
            body,published.MARKER+"\nPublished metadata issue"
        )
        self.assertIn(candidate.BEGIN,newer)
        self.assertIn("Manual upstream release evidence",newer)
        self.assertIn("Published metadata issue",newer)
        self.assertEqual(newer.count(candidate.BEGIN),1)

    def test_hourly_audit_report_update_is_idempotent(self):
        initial=published.MARKER+"\nInitial human evidence"
        update=published.MARKER+"\nUpdated audit result"
        first=published._update_report_preserving_manual_notes(initial,update)
        second=published._update_report_preserving_manual_notes(first,update)
        self.assertEqual(first,second)
        self.assertIn("Initial human evidence",second)

    def test_no_shell_payload_execution(self):
        r=report();v=verification()
        v["blocked"][0]["url"]="https://raw.githubusercontent.com/a/b/builds/$(touch bad).cs3"
        calls=[]
        def runner(args):
            calls.append(args)
            return "[]" if args[2]=="list" else "https://github.com/test/issues/18"
        candidate.sync(r,v,37970591208,runner)
        self.assertTrue(all(isinstance(args,list) for args in calls))
        self.assertFalse(any(args[0]=="bash" for args in calls))


class OriginTests(unittest.TestCase):
    def valid(self):
        repo="admknight/CloudstreamExtensions"
        return {"id":37970591208,
                "name":"Full Catalog Integrity Review (Read Only)",
                "path":".github/workflows/full-integrity-review.yml",
                "head_branch":"master","status":"completed",
                "conclusion":"success","event":"push",
                "repository":{"full_name":repo},
                "head_repository":{"full_name":repo}}

    def test_real_master_push_event_allowed(self):
        d=self.valid()
        result=validate_origin(d,"admknight/CloudstreamExtensions",37970591208)
        self.assertEqual(result["event"],"push")

    def test_schedule_and_manual_master_allowed(self):
        for event in ("schedule","workflow_dispatch"):
            d=self.valid();d["event"]=event
            self.assertEqual(validate_origin(d,"admknight/CloudstreamExtensions",37970591208)["event"],event)

    def test_untrusted_workflow_run_rejected(self):
        values=[
            ("path",".github/workflows/build.yml"),
            ("head_branch","topic"),
            ("status","in_progress"),
            ("conclusion","failure"),
            ("event","pull_request"),
            ("name","Other audit"),
        ]
        for field,bad in values:
            d=self.valid();d[field]=bad
            with self.subTest(field=field):
                with self.assertRaises(ValueError):
                    validate_origin(d,"admknight/CloudstreamExtensions",37970591208)

    def test_fork_and_run_number_rejected(self):
        d=self.valid();d["head_repository"]={"full_name":"attacker/repo"}
        with self.assertRaisesRegex(ValueError,"head_repository"):
            validate_origin(d,"admknight/CloudstreamExtensions",37970591208)
        with self.assertRaisesRegex(ValueError,"run_id"):
            validate_origin(self.valid(),"admknight/CloudstreamExtensions",42)


if __name__=="__main__":
    unittest.main()
