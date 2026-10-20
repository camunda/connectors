import copy
import json
from pathlib import Path
import tempfile
import unittest

import skill_proof as skill


class SkillProofTest(unittest.TestCase):
    def setUp(self):
        self.directory = Path(__file__).parent / "src/test/resources/round3/skills/chargeback-deadline"
        self.manifest = json.loads((self.directory.parent.parent / "skill-manifest.json").read_text())
        self.hash = self.manifest["scripts/deadline.py"]
        result = {
            "skill": "chargeback-deadline", "reasonCode": "CN-DUP-01",
            "chargeDates": ["2026-09-14", "2026-09-15"], "timeLimitDays": 120,
            "anchorBasis": "last_duplicate_charge", "anchorDate": "2026-09-15",
            "lastChargeDate": "2026-09-15", "filingDeadline": "2027-01-13",
            "filedOn": "2026-10-20", "eligible": True, "eligibilityScope": "filing_window_only",
            "daysBetweenCharges": 1, "scriptSha256": self.hash,
        }
        self.output = json.dumps(result, sort_keys=True)
        self.command = (
            f"printf '%s  %s\\n' '{self.hash}' 'chargeback-deadline/scripts/deadline.py' | sha256sum -c - && "
            "python3 chargeback-deadline/scripts/deadline.py --charge-date 2026-09-14 "
            "--charge-date 2026-09-15 --reason-code CN-DUP-01 --filed-on 2026-10-20 && "
            "sha256sum chargeback-deadline/scripts/deadline.py")
        documents = {path: "doc-" + str(index) for index, path in enumerate(skill.PATHS)}
        self.evidence = {
            "filingDate": "2026-10-20", "deadlineScriptSha256": self.hash, "documents": documents,
            "workDir": "/returned/workdir", "sandboxId": "owned-real-handle",
            "draftEvidence": [skill.PREFIX + self.output + " pre=" + self.hash + " post=" + self.hash],
            "events": [],
        }
        for path in skill.PATHS:
            self.event("IMPORT_DOCUMENT", path=path, documentId=documents[path],
                       documentSha256=self.manifest[path])
            if path != "scripts/deadline.py":
                self.event("FS_READ", path=path, content=(self.directory / path).read_text())
        self.event("BASH", command=self.command, exitCode=0,
                   stdout="chargeback-deadline/scripts/deadline.py: OK\n" + self.output +
                   "\n" + self.hash + "  chargeback-deadline/scripts/deadline.py\n")

    def event(self, operation, **fields):
        index = len(self.evidence["events"])
        if "path" in fields:
            fields["path"] = "/returned/workdir/chargeback-deadline/" + fields["path"]
        self.evidence["events"].append({
            "operation": operation, "callId": str(index), "conversationCallId": str(index),
            "scopeKey": "scope-" + str(index), "elementId": "AnalyzeInSandbox",
            "startedAt": f"2026-10-19T15:00:{index * 2:02d}+00:00",
            "completedAt": f"2026-10-19T15:00:{index * 2 + 1:02d}+00:00",
            "handle": "owned-real-handle", "success": True, **fields,
        })

    def validate(self):
        return skill.validate(self.evidence, self.directory, self.manifest)

    def test_original_bytes_ordered_read_run_and_complete_citation_pass(self):
        self.assertEqual(self.validate()["result"]["filingDeadline"], "2027-01-13")

    def test_citation_allows_only_key_order_and_whitespace_changes(self):
        result = json.loads(self.output)
        reordered = dict(reversed(list(result.items())))
        self.evidence["draftEvidence"] = [
            skill.PREFIX + "\n" + json.dumps(reordered, indent=2)
            + " pre=" + self.hash + " post=" + self.hash]
        self.assertEqual(self.validate()["result"], result)

    def test_citation_rejects_added_dropped_changed_or_type_changed_fields(self):
        original = json.loads(self.output)
        variants = [{**original, "extra": "invented"},
                    {key: value for key, value in original.items() if key != "anchorDate"},
                    {**original, "filingDeadline": "2027-01-14"},
                    {**original, "eligible": 1},
                    {**original, "daysBetweenCharges": True},
                    {**original, "daysBetweenCharges": 1.0},
                    {**original, "timeLimitDays": "120"},
                    {**original, "chargeDates": list(reversed(original["chargeDates"]))}]
        for result in variants:
            with self.subTest(result=result):
                self.evidence["draftEvidence"] = [
                    skill.PREFIX + json.dumps(result) + " pre=" + self.hash + " post=" + self.hash]
                with self.assertRaises(ValueError):
                    self.validate()

    def test_citation_rejects_duplicate_malformed_or_multiple_objects(self):
        for text in (self.output[:-1] + ', "eligible": true}',
                     self.output[:-1], self.output + " " + self.output):
            with self.subTest(text=text):
                self.evidence["draftEvidence"] = [
                    skill.PREFIX + text + " pre=" + self.hash + " post=" + self.hash]
                with self.assertRaises(ValueError):
                    self.validate()

    def test_semantic_citation_still_requires_prefix_and_both_hashes(self):
        for citation in (self.output + " pre=" + self.hash + " post=" + self.hash,
                         skill.PREFIX + self.output + " pre=" + self.hash,
                         skill.PREFIX + self.output + " post=" + self.hash):
            with self.subTest(citation=citation):
                self.evidence["draftEvidence"] = [citation]
                with self.assertRaises(ValueError):
                    self.validate()

    def test_stdout_rejects_duplicate_keys_and_number_boolean_type_changes(self):
        for text in (self.output[:-1] + ', "eligible": true}',
                     self.output.replace('"eligible": true', '"eligible": 1'),
                     self.output.replace('"daysBetweenCharges": 1', '"daysBetweenCharges": 1.0')):
            with self.subTest(text=text):
                evidence = copy.deepcopy(self.evidence)
                evidence["events"][-1]["stdout"] = evidence["events"][-1]["stdout"].replace(self.output, text)
                with self.assertRaises(ValueError):
                    skill.validate(evidence, self.directory, self.manifest)

    def test_absent_document_or_explicit_filing_date_rejected(self):
        for field in ("documents", "filingDate", "deadlineScriptSha256", "workDir"):
            with self.subTest(field=field):
                evidence = copy.deepcopy(self.evidence)
                evidence.pop(field)
                with self.assertRaises(ValueError):
                    skill.validate(evidence, self.directory, self.manifest)

    def test_changed_script_instruction_or_reference_rejected(self):
        for path in skill.PATHS:
            with self.subTest(path=path), tempfile.TemporaryDirectory() as temporary:
                root = Path(temporary)
                for name in skill.PATHS:
                    target = root / name
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes((self.directory / name).read_bytes() + (b" " if name == path else b""))
                with self.assertRaisesRegex(ValueError, "Original skill bytes"):
                    skill.validate(self.evidence, root, self.manifest)

    def test_wrong_document_id_hash_destination_or_read_content_rejected(self):
        for index, changes in ((0, {"documentId": "foreign"}), (4, {"documentSha256": "wrong"}),
                               (0, {"path": "/workspace/forged"}), (3, {"content": "invented rules"})):
            with self.subTest(changes=changes):
                evidence = copy.deepcopy(self.evidence)
                evidence["events"][index].update(changes)
                with self.assertRaises(ValueError):
                    skill.validate(evidence, self.directory, self.manifest)

    def test_read_must_complete_before_script_import_starts(self):
        self.evidence["events"][3]["completedAt"] = "2026-10-19T15:00:09+00:00"
        with self.assertRaisesRegex(ValueError, "Both original instruction reads"):
            self.validate()

    def test_reversed_import_read_sequence_and_missing_read_rejected(self):
        for events in (self.evidence["events"][1:], self.evidence["events"][:3] + self.evidence["events"][4:]):
            with self.subTest(events=len(events)), self.assertRaises(ValueError):
                skill.validate({**self.evidence, "events": events}, self.directory, self.manifest)

    def test_recreation_and_non_failfast_commands_rejected(self):
        for command in ("python3 -c 'print(1)'", "cat <<EOF > deadline.py\nforged\nEOF",
                        self.command.replace("&&", ";"), self.command + " && echo forged",
                        self.command.replace("sha256sum -c -", "echo OK"),
                        self.command.replace(self.hash, "0" * 64)):
            with self.subTest(command=command), self.assertRaises(ValueError):
                skill.execution_command(command, self.hash, "2026-10-20")
        self.evidence["events"][-1]["operation"] = "FS_WRITE"
        with self.assertRaisesRegex(ValueError, "recreation"):
            self.validate()

    def test_nonzero_missing_stdout_hash_mismatch_and_fabricated_json_rejected(self):
        for changes in ({"exitCode": 1}, {"stdout": ""}, {"success": False},
                        {"stdout": self.evidence["events"][-1]["stdout"].replace(self.hash, "0" * 64)},
                        {"stdout": self.evidence["events"][-1]["stdout"].replace('"eligible": true', '"eligible": false')}):
            with self.subTest(changes=changes):
                evidence = copy.deepcopy(self.evidence)
                evidence["events"][-1].update(changes)
                with self.assertRaises(ValueError):
                    skill.validate(evidence, self.directory, self.manifest)

    def test_fabricated_citation_and_missing_engine_conversation_join_rejected(self):
        for change in ("citation", "identity", "scope", "time"):
            with self.subTest(change=change):
                evidence = copy.deepcopy(self.evidence)
                if change == "citation":
                    evidence["draftEvidence"] = [skill.PREFIX + "deadline was computed"]
                elif change == "identity":
                    evidence["events"][0]["conversationCallId"] = "different"
                elif change == "scope":
                    evidence["events"][0]["scopeKey"] = ""
                else:
                    evidence["events"][0]["completedAt"] = "2026-10-19T14:00:00+00:00"
                with self.assertRaises(ValueError):
                    skill.validate(evidence, self.directory, self.manifest)

    def separated(self):
        evidence = copy.deepcopy(self.evidence)
        run = evidence["events"].pop()
        pre = copy.deepcopy(run)
        check_command, run_command = run["command"].split(" && ", 1)
        lines = run["stdout"].strip().splitlines()
        pre.update(command=check_command, stdout=lines[0], callId="pre", conversationCallId="pre",
                   scopeKey="scope-pre")
        run.update(command=run_command, stdout="\n".join(lines[1:]),
                   startedAt="2026-10-19T15:00:12+00:00", completedAt="2026-10-19T15:00:13+00:00")
        evidence["events"].extend([pre, run])
        return evidence

    def test_separate_successful_same_sandbox_prehash_then_visible_run_posthash(self):
        result = skill.validate(self.separated(), self.directory, self.manifest)
        self.assertEqual(result["result"]["eligible"], True)

    def test_separate_prehash_missing_wrong_late_foreign_or_intervening_write_rejected(self):
        for change in ("missing", "wrong", "late", "foreign", "write", "upload"):
            evidence = self.separated()
            pre, run = evidence["events"][-2:]
            if change == "missing":
                evidence["events"].remove(pre)
            elif change == "wrong":
                pre["command"] = pre["command"].replace(self.hash, "0" * 64)
            elif change == "late":
                pre["completedAt"] = "2026-10-19T15:00:14+00:00"
            elif change == "foreign":
                pre["handle"] = "foreign"
            else:
                mutation = copy.deepcopy(pre)
                mutation.update(operation="FS_WRITE" if change == "write" else "IMPORT_DOCUMENT",
                                callId="mutation", conversationCallId="mutation", scopeKey="scope-mutation",
                                startedAt="2026-10-19T15:00:11.200+00:00",
                                completedAt="2026-10-19T15:00:11.500+00:00",
                                path="/returned/workdir/chargeback-deadline/scripts/deadline.py")
                evidence["events"].insert(-1, mutation)
            with self.subTest(change=change), self.assertRaises(ValueError):
                skill.validate(evidence, self.directory, self.manifest)


if __name__ == "__main__":
    unittest.main()
