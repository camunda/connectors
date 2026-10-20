import json
from pathlib import Path
import tempfile
import subprocess
import shutil
import unittest
from unittest.mock import patch

import daytona_proof as proof


class ProofTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        (self.root / "target").mkdir()
        self.proofs = self.root / "proofs"
        for name, value in {"ROOT": self.root, "PROOFS": self.proofs,
                            "PROOF": self.proofs / "last-real-daytona.json",
                            "ATTEMPT": self.proofs / "real-daytona-attempt.json"}.items():
            item = patch.object(proof, name, value)
            item.start()
            self.addCleanup(item.stop)
        artifact = self.proofs / "artifacts/123-stdout.txt"
        artifact.parent.mkdir(parents=True)
        artifact.write_text('{"filingDeadline":"2027-01-13"}')
        self.identity = {"bpmnSha256": proof.sha(b"<bpmn/>"), "processDefinitionKey": "10",
                         "processDefinitionVersion": 1, "runtime": {"sha256": "runtime", "startedAt": "now"},
                         "gateSourceSha256": "gate", "inputManifestSha256": "inputs",
                         "cluster": {"restUrl": "http://localhost:8280", "installation": "test"}}
        self.execution = {"realDaytona": True, "mockOverride": False, "fallback": False, "remotePython": True,
                          "sandboxId": "real-sandbox", "sandboxCreateCount": 1,
                          "processInstanceKey": "123", "processDefinitionKey": "10",
                          "transactionDocumentId": "doc", "stdoutArtifact": str(artifact),
                          "stdoutSha256": proof.sha(artifact.read_bytes()),
                          "reconciliation": {"sourceDocumentId": "doc"},
                          "sandboxAllocation": {"cpu": 1, "memory": 1, "disk": 3},
                          "fraudConversationCount": 1, "filingDeadline": "2027-01-13",
                          "finalOutcome": "COMPLETED", "sandboxCleanup": True, "instanceCleanup": True}

    def certify(self):
        proof.begin(self.identity)
        proof.commit(self.identity, self.identity, self.execution)

    def test_missing_corrupt_or_unsupported_proof_fails_with_remedy(self):
        self.assertEqual(proof.freshness(self.identity)[0], False)
        self.assertIn("./gate.sh --e2e", proof.freshness(self.identity)[1])
        proof.PROOF.write_text("{")
        self.assertFalse(proof.freshness(self.identity)[0])
        proof.atomic(proof.PROOF, {"schemaVersion": 999})
        self.assertFalse(proof.freshness(self.identity)[0])

    def test_exact_proof_passes_but_unrelated_failure_is_not_overridden(self):
        self.certify()
        self.assertTrue(proof.freshness(self.identity)[0])
        self.assertEqual(json.loads(proof.PROOF.read_text())["execution"]["processInstanceKey"], "123")
        # The proof API returns freshness only, not a whole-gate verdict.
        self.assertNotIn("gatePassed", json.loads(proof.PROOF.read_text()))

    def test_one_byte_including_diagram_change_invalidates(self):
        self.certify()
        changed = {**self.identity, "bpmnSha256": proof.sha(b"<bpmn/> ")}
        self.assertFalse(proof.freshness(changed)[0])

    def test_skill_bytes_are_identity_and_missing_skill_evidence_cannot_stamp(self):
        self.certify()
        changed = {**self.identity, "skillManifest": {"SKILL.md": "changed"}}
        self.assertFalse(proof.freshness(changed)[0])
        with self.assertRaisesRegex(ValueError, "Missing correlated skill"):
            proof.commit(changed, changed, self.execution)

    def test_source_deployment_mismatch_fails_before_begin_returns(self):
        with patch.object(proof, "current_identity", side_effect=ValueError("Source/deployed BPMN mismatch")):
            with self.assertRaisesRegex(ValueError, "mismatch"):
                proof.main("begin")
        self.assertFalse(proof.PROOF.exists())
        self.assertEqual(proof.read(proof.ATTEMPT)["status"], "PENDING")

    def test_actual_source_deployment_byte_mismatch_rejects_identity_before_runtime_access(self):
        source = self.root / "source"
        (source / "demo").mkdir(parents=True)
        (source / "demo/card-dispute.bpmn").write_bytes(b"source")
        with patch.object(proof, "SOURCE", source), patch("gate_report.verify"), \
                patch.object(proof, "request", side_effect=[
                    {"items": [{"processDefinitionKey": "10", "version": 1}]}, b"deployed"]), \
                patch.object(proof, "runtime_identity") as runtime:
            with self.assertRaisesRegex(ValueError, "mismatch"):
                proof.current_identity()
            runtime.assert_not_called()

    def test_gate_and_manual_check_share_persistent_lock(self):
        script = Path(proof.__file__).parent / "gate.sh"
        shutil.copyfile(script, self.root / "gate.sh")
        (self.proofs / "gate.lock").mkdir()
        for mode in ("--e2e", "--check-proof", "all"):
            result = subprocess.run(["bash", str(self.root / "gate.sh"), mode],
                                    capture_output=True, text=True)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn("another gate owns proofs/gate.lock", result.stderr)
        self.assertTrue((self.proofs / "gate.lock").exists())

    def test_identical_bytes_redeployment_reuses_only_other_matching_identities(self):
        self.certify()
        redeployed = {**self.identity, "processDefinitionKey": "11", "processDefinitionVersion": 2}
        self.assertTrue(proof.freshness(redeployed)[0])
        for field in ("runtime", "inputManifestSha256", "cluster", "gateSourceSha256"):
            changed = {**redeployed, field: "changed"}
            self.assertFalse(proof.freshness(changed)[0], field)

    def test_mock_fallback_missing_stdout_cannot_certify(self):
        for changes in ({"realDaytona": False}, {"mockOverride": True}, {"fallback": True},
                        {"remotePython": False}, {"stdoutSha256": ""}, {"stdoutSha256": "wrong"},
                        {"stdoutArtifact": "missing"}, {"filingDeadline": "wrong"}):
            with self.subTest(changes=changes), self.assertRaises((ValueError, OSError)):
                proof.commit(self.identity, self.identity, {**self.execution, **changes})
        self.assertFalse(proof.PROOF.exists())

    def test_second_sandbox_failed_execution_cleanup_or_race_cannot_stamp(self):
        for changes in ({"sandboxCreateCount": 2}, {"fraudConversationCount": 2},
                        {"sandboxCleanup": False}, {"instanceCleanup": False},
                        {"finalOutcome": "TERMINATED"}, {"processDefinitionKey": "wrong"}):
            with self.subTest(changes=changes), self.assertRaises(ValueError):
                proof.commit(self.identity, self.identity, {**self.execution, **changes})
        for field in ("bpmnSha256", "processDefinitionKey", "processDefinitionVersion", "runtime"):
            after = {**self.identity, field: "changed"}
            with self.subTest(race=field), self.assertRaises(ValueError):
                proof.commit(self.identity, after, self.execution)
        self.assertFalse(proof.PROOF.exists())

    def test_pending_failure_survives_transient_reset_and_preserves_old_proof(self):
        self.certify()
        original = proof.PROOF.read_bytes()
        proof.begin(self.identity)
        self.assertFalse(proof.freshness(self.identity)[0])
        (self.root / "target/real-daytona-before.json").unlink()
        self.assertFalse(proof.freshness(self.identity)[0])
        self.assertEqual(proof.PROOF.read_bytes(), original)
        proof.commit(self.identity, self.identity, self.execution)
        self.assertTrue(proof.freshness(self.identity)[0])

    def test_artifact_tampering_invalidates_existing_proof(self):
        self.certify()
        Path(self.execution["stdoutArtifact"]).write_text("changed")
        self.assertFalse(proof.freshness(self.identity)[0])

    def test_runtime_attestation_checks_pid_listener_hash_and_start_time(self):
        source = self.root / "source"
        run = source / "run"
        run.mkdir(parents=True)
        jar = source / ("connectors/apps/bundle/default-bundle/target/"
                        "connector-runtime-bundle-8.11.0-SNAPSHOT-with-dependencies.jar")
        jar.parent.mkdir(parents=True)
        jar.write_bytes(b"jar")
        (run / "connectors.java.pid").write_text("123")
        attestation = {"pid": 123, "jarPath": str(jar), "sha256": proof.sha(b"jar"), "startedAt": "time"}
        proof.atomic(run / "connectors.attestation.json", attestation)
        with patch.object(proof, "SOURCE", source), patch.object(proof.subprocess, "check_output",
                side_effect=[f"java -Xmx1g -jar {jar}", "123"]):
            self.assertEqual(proof.runtime_identity()["sha256"], proof.sha(b"jar"))
        for changes in ({"pid": 124}, {"startedAt": ""}, {"sha256": "wrong"}, {"jarPath": "/elsewhere"}):
            proof.atomic(run / "connectors.attestation.json", {**attestation, **changes})
            with patch.object(proof, "SOURCE", source), patch.object(proof.subprocess, "check_output",
                    side_effect=[f"java -jar {jar}", "123"]), self.assertRaises(ValueError):
                proof.runtime_identity()
        proof.atomic(run / "connectors.attestation.json", attestation)
        for outputs in (["java -jar /wrong"], [f"java -jar {jar}", "999"]):
            with patch.object(proof, "SOURCE", source), patch.object(proof.subprocess, "check_output",
                    side_effect=outputs), self.assertRaises(ValueError):
                proof.runtime_identity()


if __name__ == "__main__":
    unittest.main()
