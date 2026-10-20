#!/usr/bin/env python3
"""Fail-closed, persistent real-Daytona certification. No secret files are read."""
import base64
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.request
import xml.etree.ElementTree as ET
import skill_proof

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT.parent / "source-cluster"
REST = "http://localhost:8280"
PROOFS = ROOT / "proofs"
PROOF = PROOFS / "last-real-daytona.json"
ATTEMPT = PROOFS / "real-daytona-attempt.json"
IDENTITY_FIELDS = ("bpmnSha256", "runtime", "gateSourceSha256", "inputManifestSha256", "cluster", "skillManifest")


def sha(data):
    return hashlib.sha256(data).hexdigest()


def atomic(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    temporary = path.with_suffix(".tmp")
    with temporary.open("w") as stream:
        json.dump(value, stream, indent=2)
        stream.write("\n")
        stream.flush()
        os.fsync(stream.fileno())
    os.replace(temporary, path)


def read(path):
    return json.loads(path.read_text())


def request(path, body=None, binary=False):
    data = None if body is None else json.dumps(body).encode()
    headers = {"Authorization": "Basic " + base64.b64encode(b"demo:demo").decode(),
               "Content-Type": "application/json", "Accept": "*/*" if binary else "application/json"}
    req = urllib.request.Request(REST + path, data=data, headers=headers)
    with urllib.request.urlopen(req, timeout=20) as response:
        return response.read() if binary else json.load(response)


def gate_source_hash():
    files = [ROOT / name for name in ("gate.sh", "gate_report.py", "daytona_proof.py", "skill_proof.py",
                                    "skill_history.py", "tone_proof.py", "pom.xml")]
    files += [p for p in (ROOT / "src/test").rglob("*") if p.is_file()
              and "resources/demo" not in str(p.relative_to(ROOT))]
    files += [p for p in (ROOT / "modeler-parity").rglob("*") if p.is_file()
              and not {"node_modules", "dist", "fixtures"}.intersection(p.relative_to(ROOT).parts)]
    return sha(json.dumps({str(p.relative_to(ROOT)): sha(p.read_bytes()) for p in sorted(files)},
                          sort_keys=True).encode())


def runtime_identity():
    # Only these two run/ files are safe inputs; never inspect run/secrets or the launcher.
    attestation = read(SOURCE / "run/connectors.attestation.json")
    pid = int((SOURCE / "run/connectors.java.pid").read_text().strip())
    if attestation["pid"] != pid or not attestation.get("startedAt"):
        raise ValueError("Runtime attestation PID/start time mismatch")
    jar = Path(attestation["jarPath"]).resolve()
    expected = (SOURCE / "connectors/apps/bundle/default-bundle/target/"
                "connector-runtime-bundle-8.11.0-SNAPSHOT-with-dependencies.jar").resolve()
    if jar != expected:
        raise ValueError("Unexpected runtime bundle path")
    args = subprocess.check_output(["ps", "-o", "args=", "-p", str(pid)], text=True).strip().split()
    if "-jar" not in args or Path(args[args.index("-jar") + 1]).resolve() != jar:
        raise ValueError("Runtime attestation does not match the live Java process")
    listeners = subprocess.check_output(["lsof", "-nP", "-a", "-p", str(pid),
                                        "-iTCP:8286", "-sTCP:LISTEN", "-t"], text=True).split()
    if str(pid) not in listeners:
        raise ValueError("Attested Java process does not listen on :8286")
    digest = sha(jar.read_bytes())
    if digest != attestation["sha256"]:
        raise ValueError("Runtime bundle digest differs from attestation")
    return {"pid": pid, "jarPath": str(jar), "sha256": digest, "startedAt": attestation["startedAt"]}


def current_identity():
    import gate_report
    gate_report.verify(SOURCE)
    definitions = request("/v2/process-definitions/search", {
        "filter": {"processDefinitionId": "CardDispute"},
        "sort": [{"field": "version", "order": "DESC"}], "page": {"limit": 1}})["items"]
    if not definitions:
        raise ValueError("CardDispute is not deployed")
    definition = definitions[0]
    key = str(definition["processDefinitionKey"])
    deployed = request("/v2/process-definitions/" + key + "/xml", binary=True)
    digest = sha(deployed)
    if digest != sha((SOURCE / "demo/card-dispute.bpmn").read_bytes()):
        raise ValueError("Source/deployed BPMN mismatch; refusing sandbox start or proof")
    sandbox = ET.fromstring(deployed).find(".//{http://www.omg.org/spec/BPMN/20100524/MODEL}serviceTask[@id='AnalyzeInSandbox']")
    if sandbox is None:
        raise ValueError("Sandbox tool missing from deployed model")
    job = sandbox.find(".//{http://camunda.org/schema/zeebe/1.0}taskDefinition")
    if job is None:
        raise ValueError("Sandbox job configuration missing")
    topology = request("/v2/topology")
    brokers = sorted(({"nodeId": b["nodeId"], "host": b["host"], "port": b["port"]}
                      for b in topology["brokers"]), key=lambda b: b["nodeId"])
    if not brokers:
        raise ValueError("Cluster has no brokers")
    skill_directory = ROOT / "src/test/resources/demo/skills/chargeback-deadline"
    skills = skill_proof.manifest(skill_directory) if skill_directory.exists() else {}
    if ET.fromstring(deployed).find(".//*[@id='FetchCardNetworkRules']") is None and not skills:
        raise ValueError("Skill-based model requires all three original skill documents")
    return {"bpmnSha256": digest, "processDefinitionKey": key, "processDefinitionVersion": definition["version"],
            "sandboxRetries": job.get("retries"),
            "runtime": runtime_identity(), "gateSourceSha256": gate_source_hash(),
            "inputManifestSha256": sha((ROOT / "target/input-sha256.json").read_bytes()), "skillManifest": skills,
            "cluster": {"restUrl": REST, "installation": str(SOURCE.resolve()), "brokers": brokers,
                        "gatewayVersion": topology["gatewayVersion"]}}


def require_same(before, after, race=False):
    for field in IDENTITY_FIELDS:
        if before.get(field) != after.get(field):
            raise ValueError("Proof identity changed: " + field)
    if race and any(before.get(f) != after.get(f) for f in ("processDefinitionKey", "processDefinitionVersion")):
        raise ValueError("Deployment changed during real-Daytona run")


def freshness(identity):
    try:
        if ATTEMPT.exists():
            attempt = read(ATTEMPT)
            if not isinstance(attempt, dict) or attempt.get("status") != "SUCCEEDED":
                raise ValueError("last real-Daytona attempt failed or is pending")
        proof = read(PROOF)
        if not isinstance(proof, dict) or proof.get("schemaVersion") != 1 or not proof.get("succeededAt"):
            raise ValueError("unsupported proof schema")
        require_same(proof, identity)
        validate_execution(proof["execution"], proof.get("skillManifest"))
        return True, "PASS (current real-Daytona proof)"
    except (OSError, ValueError, KeyError, TypeError):
        return False, "FAIL (missing, corrupt, stale or failed real-Daytona proof); run ./gate.sh --e2e"


def validate_execution(evidence, skills=None):
    if not isinstance(evidence, dict):
        raise ValueError("Invalid real-Daytona evidence")
    required = ("sandboxId", "processInstanceKey", "transactionDocumentId", "stdoutSha256",
                "stdoutArtifact", "reconciliation", "sandboxAllocation")
    if any(not evidence.get(field) for field in required):
        raise ValueError("Real-Daytona execution evidence is incomplete")
    if evidence.get("realDaytona") is not True or evidence.get("mockOverride") is not False:
        raise ValueError("Mocked Daytona cannot certify a version")
    if evidence.get("fallback") is not False or evidence.get("remotePython") is not True:
        raise ValueError("Fallback or missing remote Python execution")
    if evidence.get("sandboxCreateCount") != 1 or evidence.get("fraudConversationCount") != 1:
        raise ValueError("Exactly one sandbox and one fraud conversation required")
    if evidence.get("filingDeadline") != "2027-01-13":
        raise ValueError("Missing computed deadline in actual shell stdout")
    if evidence.get("finalOutcome") != "COMPLETED":
        raise ValueError("Owned process did not complete")
    if evidence.get("sandboxCleanup") is not True or evidence.get("instanceCleanup") is not True:
        raise ValueError("Cleanup not verified")
    if evidence["sandboxAllocation"] != {"cpu": 1, "memory": 1, "disk": 3}:
        raise ValueError("Unexpected sandbox allocation")
    stdout = Path(evidence["stdoutArtifact"])
    if not stdout.is_absolute():
        stdout = ROOT / stdout
    if stdout.parent.resolve() != (PROOFS / "artifacts").resolve():
        raise ValueError("Stdout must be a persistent gate-owned artifact")
    if sha(stdout.read_bytes()) != evidence["stdoutSha256"]:
        raise ValueError("Remote stdout missing or digest mismatch")
    if skills:
        if not isinstance(evidence.get("skillEvidence"), dict):
            raise ValueError("Missing correlated skill execution evidence")
        skill_proof.validate(evidence["skillEvidence"],
                             ROOT / "src/test/resources/demo/skills/chargeback-deadline", skills)


def begin(identity):
    atomic(ATTEMPT, {"status": "PENDING", "identity": identity,
                     "startedAt": datetime.now(timezone.utc).isoformat()})
    atomic(ROOT / "target/real-daytona-before.json", identity)


def commit(before, after, execution):
    require_same(before, after, race=True)
    if execution.get("processDefinitionKey") != before["processDefinitionKey"]:
        raise ValueError("Started instance references a different process definition key")
    validate_execution(execution, before.get("skillManifest"))
    proof = {**before, "schemaVersion": 1, "succeededAt": datetime.now(timezone.utc).isoformat(),
             "execution": execution}
    atomic(PROOF, proof)
    atomic(ATTEMPT, {"status": "SUCCEEDED", "succeededAt": proof["succeededAt"]})


def main(command):
    if command == "begin":
        # Persist a failed/pending attempt even if preflight fails, without destroying old proof.
        atomic(ATTEMPT, {"status": "PENDING"})
        identity = current_identity()
        if identity["sandboxRetries"] != "1":
            raise ValueError("Real proof requires one sandbox job attempt; disable CREATE retries in the model")
        begin(identity)
    elif command == "commit":
        import gate_report
        if (ROOT / "target/e2e.exit").read_text().strip() != "0" or not gate_report.token_scan():
            raise ValueError("Real scenario or credential scan failed")
        if gate_report.results("e2e").get(("demo.LiveTest", "l1")) != "PASS":
            raise ValueError("Real scenario report missing or failed")
        commit(read(ROOT / "target/real-daytona-before.json"), current_identity(),
               read(ROOT / "target/real-daytona-execution.json"))
    elif command == "check":
        ok, reason = freshness(current_identity())
        atomic(ROOT / "target/real-daytona-check.json", {"passed": ok, "reason": reason})
        print("RealDaytona.proof:", reason)
        return 0 if ok else 1
    else:
        raise ValueError("Unknown proof command")
    return 0


if __name__ == "__main__":
    try:
        sys.exit(main(sys.argv[1]))
    except (OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        # Never echo exception bodies: REST errors can contain resolved connector inputs.
        print("FAIL real-Daytona identity/proof verification; run ./gate.sh --e2e (details withheld)")
        if sys.argv[1] == "check":
            atomic(ROOT / "target/real-daytona-check.json", {
                "passed": False, "reason": "FAIL (deployment/runtime identity unavailable); run ./gate.sh --e2e"})
        sys.exit(1)
