import contextlib
import io
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch
import gate_report


class ReporterTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.target = self.root / "target"
        self.target.mkdir()
        (self.target / "real-daytona-check.json").write_text(json.dumps({
            "passed": True, "reason": "PASS (current real-Daytona proof)"}))
        self.patches = [patch.object(gate_report, "ROOT", self.root),
                        patch.object(gate_report, "TARGET", self.target)]
        for item in self.patches:
            item.start()
            self.addCleanup(item.stop)

    def reports(self, tier, cases):
        path = self.target / ("surefire-" + tier)
        path.mkdir(exist_ok=True)
        xml = "<testsuite>" + "".join(
            f'<testcase classname="{cls}" name="{name}">{"" if passed else "<failure/>"}</testcase>'
            for cls, name, passed in cases) + "</testsuite>"
        (path / "TEST-results.xml").write_text(xml)

    def deterministic(self):
        self.reports("deterministic", [
            (cls, name, True)
            for cls, names in gate_report.EXPECTED.items() if cls != "demo.LiveTest"
            for name in names
        ])

    def live(self, outcome="REGIONAL_UNAVAILABLE"):
        self.reports("live", [("demo.LiveTest", name, True) for name in ("l1", "l2", "webSearchProbe")])
        (self.target / "web-search-probe.json").write_text(json.dumps({
            "processId": "CptGateWebSearchProbe", "processInstanceKey": "123", "outcome": outcome,
        }))

    def test_missing_or_unexpected_failed_test_blocks_live(self):
        self.assertFalse(gate_report.eligible())
        self.deterministic()
        with patch.object(gate_report, "coverage", return_value=True):
            self.assertTrue(gate_report.eligible())
        self.reports("deterministic", [("demo.DeterministicTest", "unexpected", False)])
        self.assertFalse(gate_report.eligible())

    def test_retry_failure_cannot_reuse_old_l2_pass(self):
        self.reports("live", [("demo.LiveTest", "l1", True), ("demo.LiveTest", "l2", True)])
        (self.target / "live-l2-attempted").touch()
        result = gate_report.results("live")
        self.assertEqual(result[("demo.LiveTest", "l1")], "PASS")
        self.assertNotEqual(result[("demo.LiveTest", "l2")], "PASS")
        self.reports("live-l2", [("demo.LiveTest", "l2", True)])
        self.assertEqual(gate_report.results("live")[("demo.LiveTest", "l2")], "PASS")

    def test_auth_failure_blocks_live_despite_web_exception(self):
        self.deterministic()
        self.reports("deterministic", [
            (cls, name, name != "authenticatedA2a")
            for cls, names in gate_report.EXPECTED.items() if cls != "demo.LiveTest"
            for name in names])
        with patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.eligible())

    def test_e2e_failure_or_missing_evidence_cannot_pass(self):
        self.reports("deterministic", [(cls, name, True)
            for cls, names in gate_report.EXPECTED.items() if cls != "demo.LiveTest"
            for name in names])
        self.live()
        (self.target / "e2e-attempted").touch()
        with contextlib.redirect_stdout(io.StringIO()), patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.summary("0", "0"))
            self.reports("e2e", [("demo.LiveTest", "l1", True)])
            (self.target / "e2e.exit").write_text("1")
            self.assertFalse(gate_report.summary("0", "0"))
            (self.target / "e2e.exit").write_text("0")
            self.assertTrue(gate_report.summary("0", "0"))

    def test_e2e_requires_successful_default_live_evidence(self):
        self.assertFalse(gate_report.eligible_e2e())
        (self.target / "live.exit").write_text("0")
        self.assertFalse(gate_report.eligible_e2e())
        self.live()
        self.assertTrue(gate_report.eligible_e2e())
        (self.target / "live.exit").write_text("1")
        self.assertFalse(gate_report.eligible_e2e())

    def test_strict_auth_failure_keeps_whole_gate_red(self):
        self.deterministic()
        self.live()
        self.reports("deterministic", [(cls, name, name != "authenticatedA2a")
            for cls, names in gate_report.EXPECTED.items() if cls != "demo.LiveTest"
            for name in names])
        with contextlib.redirect_stdout(io.StringIO()), patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.summary("0", "0"))
        self.reports("deterministic", [(cls, name, True)
            for cls, names in gate_report.EXPECTED.items() if cls != "demo.LiveTest"
            for name in names])
        with contextlib.redirect_stdout(io.StringIO()), patch.object(gate_report, "coverage", return_value=True):
            self.assertTrue(gate_report.summary("0", "0"))
            self.assertFalse(gate_report.summary("1", "0"))

    def test_coverage_and_leave_one_out_use_runtime_data(self):
        demo = self.root / "src/test/resources/demo"
        demo.mkdir(parents=True)
        (demo / "card-dispute.bpmn").write_text(
            '<definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"><process id="CardDispute">'
            '<startEvent id="start"/><endEvent id="end"/><boundaryEvent id="fallback"/><intermediateCatchEvent id="timeout"/></process></definitions>')
        report = self.target / "coverage-report"
        report.mkdir()
        data = {
            "processModels": [{"processDefinitionId": "CardDispute", "totalElementCount": 4}],
            "suites": [{"runs": [
                {"name": name, "processCoverages": [{"processDefinitionId": "CardDispute",
                    "completedElements": elements, "takenSequenceFlows": []}]}
                for name, elements in ([("d1", ["start", "end"]), ("d2", ["start"]),
                    ("d4", ["start", "timeout"])] + [
                    (name, ["start", "fallback"]) for name in gate_report.EXPECTED["demo.SandboxFailureCandidateTest"]])
            ]}],
        }
        (report / "report.html").write_text("window.COVERAGE_DATA = " + json.dumps(data))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertTrue(gate_report.coverage())
        data["suites"][0]["runs"][0]["processCoverages"][0]["completedElements"] = ["start"]
        (report / "report.html").write_text("window.COVERAGE_DATA = " + json.dumps(data))
        with contextlib.redirect_stdout(io.StringIO()):
            self.assertFalse(gate_report.coverage())

    def test_retry_rejects_changed_source_and_copied_inputs(self):
        source = self.root / "source"
        demo = source / "demo"
        demo.mkdir(parents=True)
        for name in ("card-dispute.bpmn", "review-dispute.form", "rehearse.sh"):
            (demo / name).write_text(name)
        for folder in ("data", "policies", "rules", "scripts", "variants"):
            (demo / folder).mkdir()
            (demo / folder / "example").write_text(folder)
        template = source / "connectors/connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-subprocess.v2.json"
        template.parent.mkdir(parents=True)
        template.write_text("{}")
        with contextlib.redirect_stdout(io.StringIO()):
            gate_report.sync(source)
            gate_report.verify(source)
        (demo / "card-dispute.bpmn").write_text("changed")
        with self.assertRaisesRegex(ValueError, "drift"):
            gate_report.verify(source)
        (demo / "card-dispute.bpmn").write_text("card-dispute.bpmn")
        (self.root / "src/test/resources/demo/review-dispute.form").write_text("changed")
        with self.assertRaisesRegex(ValueError, "drift"):
            gate_report.verify(source)
        (demo / "scripts/new").write_text("new")
        with self.assertRaisesRegex(ValueError, "file set"):
            gate_report.verify(source)

    def test_new_snapshot_invalidates_live_and_e2e_evidence(self):
        source = self.root / "source"
        demo = source / "demo"
        demo.mkdir(parents=True)
        for name in ("card-dispute.bpmn", "review-dispute.form", "rehearse.sh"):
            (demo / name).write_text(name)
        template = source / "connectors/connectors/agentic-ai/connector-agentic-ai/element-templates/agenticai-ai-agent-subprocess.v2.json"
        template.parent.mkdir(parents=True)
        template.write_text("{}")
        for tier in ("live", "live-l2", "e2e"):
            self.reports(tier, [("demo.LiveTest", "l1", True)])
        for name in ("live-l2-attempted", "e2e-attempted", "e2e.exit", "live.exit"):
            (self.target / name).write_text("0")
        with contextlib.redirect_stdout(io.StringIO()):
            gate_report.sync(source)
        for tier in ("live", "live-l2", "e2e"):
            self.assertEqual(gate_report.results(tier), {})
        self.assertFalse(gate_report.eligible_e2e())
        self.assertFalse((self.target / "e2e-attempted").exists())

    def test_modeler_parity_failure_blocks_live_and_is_reported_with_reasons(self):
        self.deterministic()
        with patch.object(gate_report, "coverage", return_value=True):
            self.assertTrue(gate_report.eligible())
        (self.target / "surefire-deterministic/TEST-modeler.ModelerParity.xml").write_text(
            '<testsuite><testcase classname="modeler.ModelerParity" name="templatesResolve">'
            '<failure message="x">Fraud_Send: dev.hansel.a2a.client.v1@1 not found</failure></testcase></testsuite>')
        with patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.eligible())
        self.live()
        output = io.StringIO()
        with contextlib.redirect_stdout(output), patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.summary("0", "0"))
        text = output.getvalue()
        self.assertIn("| ModelerParity.templatesResolve | FAIL |", text)
        self.assertIn("- templatesResolve: Fraud_Send: dev.hansel.a2a.client.v1@1 not found", text)
        self.assertIn("GATE: FAIL (N/A: #83 web search not available on EU account; "
                      "ModelerParity.templatesResolve)", text)

    def test_missing_modeler_parity_report_fails_closed(self):
        self.reports("deterministic", [(cls, name, True)
            for cls, names in gate_report.EXPECTED.items() if cls not in ("demo.LiveTest", "modeler.ModelerParity")
            for name in names])
        with patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.eligible())

    def test_regional_exception_alone_passes_with_exact_gate_wording(self):
        self.deterministic()
        self.live()
        output = io.StringIO()
        with contextlib.redirect_stdout(output), patch.object(gate_report, "coverage", return_value=True):
            self.assertTrue(gate_report.summary("0", "0"))
        self.assertIn("| LiveTest.webSearchProbe | N/A: not available on EU account |", output.getvalue())
        self.assertIn("GATE: PASS (N/A: #83 web search not available on EU account)\n", output.getvalue())

    def test_non_regional_web_search_error_fails(self):
        self.deterministic()
        self.live("OTHER_ERROR")
        with contextlib.redirect_stdout(io.StringIO()), patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.summary("0", "0"))
        self.assertIn("non-regional", gate_report.web_search_result())

    def test_successful_web_search_fails_until_model_enables_it(self):
        self.deterministic()
        self.live("AVAILABLE")
        with contextlib.redirect_stdout(io.StringIO()), patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.summary("0", "0"))
        self.assertIn("web search now available; enable it in the model", gate_report.web_search_result())

    def test_regional_marker_cannot_override_failed_or_missing_probe(self):
        self.deterministic()
        self.live()
        self.reports("live", [("demo.LiveTest", "webSearchProbe", False)])
        self.assertIn("FAIL", gate_report.web_search_result())
        with contextlib.redirect_stdout(io.StringIO()), patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.summary("0", "0"))

    def test_stale_real_proof_fails_summary_but_not_recovery_eligibility(self):
        self.deterministic()
        self.live()
        (self.target / "live.exit").write_text("0")
        (self.target / "real-daytona-check.json").write_text(json.dumps({
            "passed": False, "reason": "FAIL (stale proof); run ./gate.sh --e2e"}))
        with contextlib.redirect_stdout(io.StringIO()), patch.object(gate_report, "coverage", return_value=True):
            self.assertFalse(gate_report.summary("0", "0"))
            self.assertTrue(gate_report.eligible())
            self.assertTrue(gate_report.eligible_e2e())

    def test_token_scan_rejects_unredacted_bearer_without_printing_it(self):
        path = self.target / "example.log"
        path.write_text("Authorization: Bearer REDACTED")
        self.assertTrue(gate_report.token_scan())
        path.write_text("Authorization: Bearer " + "synthetic-token-for-unit-test")
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            self.assertFalse(gate_report.token_scan())
        self.assertEqual(output.getvalue(), "")


if __name__ == "__main__":
    unittest.main()
