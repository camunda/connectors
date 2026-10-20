"""Synthetic engine/history regression fixtures, never real sandbox certification."""
import copy
from pathlib import Path
import unittest

import test_skill_proof
import skill_history


class SkillHistoryTest(unittest.TestCase):
    def setUp(self):
        seed = test_skill_proof.SkillProofTest()
        seed.setUp()
        self.directory, self.manifest = seed.directory, seed.manifest
        self.raw = {"fixtureKind": "synthetic-not-runtime-proof", "elements": [], "variables": [],
                    "history": [], "documents": [], "documentSha256": {},
                    "filingDate": "2026-10-20", "deadlineScriptSha256": seed.hash,
                    "draftEvidence": seed.evidence["draftEvidence"]}
        self.scope("create", {"operation": "CREATE", "_meta": {"name": "AnalyzeInSandbox"}}, {"handle": "owned-real-handle",
                   "workDir": "/returned/workdir"}, "2026-10-19T14:59:00+00:00",
                   "2026-10-19T14:59:01+00:00")
        for relative, identity in seed.evidence["documents"].items():
            self.raw["documents"].append({"documentId": identity, "metadata": {"fileName": Path(relative).name}})
            self.raw["documentSha256"][identity] = self.manifest[relative]
        for event in seed.evidence["events"]:
            operation = event["operation"]
            call = {"operation": operation, "handle": event["handle"],
                    "_meta": {"id": event["callId"], "name": "AnalyzeInSandbox"}}
            arguments = {}
            if operation == "IMPORT_DOCUMENT":
                call.update(path=event["path"], document={"documentId": event["documentId"]})
                arguments.update(path=event["path"], id='<doc id="' + event["documentId"] + '" />')
                relative = next(p for p, id in seed.evidence["documents"].items() if id == event["documentId"])
                size = (self.directory / relative).stat().st_size
                result = f"Imported '{Path(relative).name}' ({size} bytes) to {event['path']}."
                name = "sandbox_import_document"
            elif operation == "FS_READ":
                arguments["path"] = call["path"] = event["path"]
                result, name = event["content"], "sandbox_fs_read"
            else:
                arguments["command"] = call["command"] = event["command"]
                result, name = "exit_code: 0\nstdout:\n" + event["stdout"].strip(), "sandbox_bash"
            self.scope(event["scopeKey"], call, result, event["startedAt"], event["completedAt"])
            self.raw["history"].append({"role": "TOOL_RESULT",
                "toolCalls": [{"toolCallId": event["callId"], "toolName": name,
                               "elementId": "AnalyzeInSandbox", "arguments": arguments}],
                "content": [{"contentType": "TEXT", "text": result}]})

    def scope(self, scope, call, result, start, end):
        self.raw["elements"].append({"elementInstanceKey": scope, "elementId": "AnalyzeInSandbox",
            "state": "COMPLETED", "startDate": start, "endDate": end})
        self.raw["variables"].extend({"scopeKey": scope, "name": name, "value": value}
                                    for name, value in (("toolCall", call), ("toolCallResult", result)))

    def validate(self):
        return skill_history.normalize(self.raw, self.directory, self.manifest)

    def test_synthetic_original_bytes_joined_to_actual_engine_shapes(self):
        self.assertEqual(self.validate()["documents"]["scripts/deadline.py"], "doc-2")

    def test_history_id_operation_arguments_and_output_cannot_be_fabricated(self):
        for field in ("toolCallId", "toolName", "arguments", "content"):
            with self.subTest(field=field):
                altered = copy.deepcopy(self.raw)
                item = altered["history"][0]
                if field == "content":
                    item["content"][0]["text"] = "fabricated import"
                else:
                    item["toolCalls"][0][field] = {} if field == "arguments" else "foreign"
                with self.assertRaises(ValueError):
                    skill_history.normalize(altered, self.directory, self.manifest)

    def test_changed_download_failed_scope_and_missing_history_fail_closed(self):
        for change in ("bytes", "scope", "history"):
            altered = copy.deepcopy(self.raw)
            if change == "bytes":
                altered["documentSha256"]["doc-2"] = "wrong"
            elif change == "scope":
                altered["elements"][1]["state"] = "TERMINATED"
            else:
                altered["history"] = []
            with self.subTest(change=change), self.assertRaises(ValueError):
                skill_history.normalize(altered, self.directory, self.manifest)

    def test_document_identity_requires_a_single_tag_not_prose_or_a_reference_object(self):
        self.assertEqual(skill_history.document_id('<doc id="own" />'), "own")
        for value in ("own", '<doc id="own" /><doc id="foreign"/>', {"documentId": "own"},
                      '<!DOCTYPE doc><doc id="own"/>', '<doc id="own">fabrication</doc>'):
            with self.subTest(value=value), self.assertRaises(ValueError):
                skill_history.document_id(value)

    def inner_scopes(self):
        altered = copy.deepcopy(self.raw)
        altered["scopeChildren"] = {}
        for element in list(altered["elements"]):
            scope = element["elementInstanceKey"]
            element["elementName"] = "Analyze in sandbox"
            body_scope = "body-" + scope
            body = {**element, "elementInstanceKey": body_scope, "elementId": "DisputeAgent#innerInstance",
                    "type": "AD_HOC_SUB_PROCESS_INNER_INSTANCE"}
            altered["elements"].append(body)
            altered["scopeChildren"][body_scope] = [scope]
            for variable in list(altered["variables"]):
                if variable["scopeKey"] != scope:
                    continue
                if variable["name"] == "toolCall":
                    variable["scopeKey"] = body_scope
                    variable["value"].setdefault("_meta", {})["name"] = "AnalyzeInSandbox"
                else:
                    altered["variables"].append({**variable, "scopeKey": body_scope})
        return altered

    def test_engine_body_inputs_join_exact_child_results_using_recorded_ancestry(self):
        self.assertEqual(len(skill_history.normalize(self.inner_scopes(), self.directory, self.manifest)["events"]), 6)

    def test_cross_call_wrong_or_ambiguous_ancestry_is_rejected(self):
        for change in ("foreign", "missing", "ambiguous"):
            altered = self.inner_scopes()
            if change == "foreign":
                altered["scopeChildren"]["body-scope-0"] = ["scope-1"]
            elif change == "missing":
                altered["scopeChildren"] = {}
            else:
                body = copy.deepcopy(altered["elements"][-1])
                body["elementInstanceKey"] = "duplicate-body"
                altered["elements"].append(body)
                altered["scopeChildren"]["duplicate-body"] = ["scope-5"]
                for v in list(altered["variables"]):
                    if v["scopeKey"] == "body-scope-5":
                        altered["variables"].append({**v, "scopeKey": "duplicate-body"})
            with self.subTest(change=change), self.assertRaises(ValueError):
                skill_history.normalize(altered, self.directory, self.manifest)


if __name__ == "__main__":
    unittest.main()
