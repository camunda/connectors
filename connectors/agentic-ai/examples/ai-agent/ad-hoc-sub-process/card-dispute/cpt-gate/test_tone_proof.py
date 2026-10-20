import copy
import unittest

import tone_proof as tone


class ToneProofTest(unittest.TestCase):
    def setUp(self):
        self.identity = {"bpmnSha256": "model", "inputManifestSha256": "inputs",
                         "runtime": {"sha256": "jar", "startedAt": "start", "pid": 123}}
        self.cold = {"decision": "APPROVE", "refundAmount": 482.17, "rationale": "Cold generated rationale."}
        self.warm = {**self.cold, "rationale": "Different generated rationale."}
        self.policy = tone.policy_bytes(b"approved cold", b"approved cold", 65)
        self.record = tone.cold_record(self.cold, "run-1", self.identity, self.policy)

    def test_current_run_identical_outcome_different_rationale_passes(self):
        self.assertTrue(tone.compare(self.record, self.warm, "run-1", self.identity))

    def test_policy_bytes_and_resource_version_are_strict(self):
        for actual, approved, version in ((b"changed", b"approved", 65), (b"same", b"same", 0)):
            with self.assertRaises(ValueError):
                tone.policy_bytes(actual, approved, version)

    def test_old_run_snapshot_or_runtime_cannot_supply_cold_rationale(self):
        with self.assertRaisesRegex(ValueError, "another run"):
            tone.compare(self.record, self.warm, "run-2", self.identity)
        for field in self.identity:
            changed = copy.deepcopy(self.identity)
            changed[field] = "changed"
            with self.subTest(field=field), self.assertRaises(ValueError):
                tone.compare(self.record, self.warm, "run-1", changed)

    def test_identical_or_missing_rationale_and_different_outcome_fail(self):
        for draft in (self.cold, {**self.warm, "rationale": ""},
                      {**self.warm, "decision": "ESCALATE"}, {**self.warm, "refundAmount": 250}):
            with self.subTest(draft=draft), self.assertRaises(ValueError):
                tone.compare(self.record, draft, "run-1", self.identity)


if __name__ == "__main__":
    unittest.main()
