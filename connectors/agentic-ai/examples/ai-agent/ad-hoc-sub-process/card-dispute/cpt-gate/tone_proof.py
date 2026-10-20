"""Run-bound tone comparison; qualitative tone remains human acceptance."""
import hashlib
import json
from pathlib import Path
import sys
import uuid


def policy_bytes(actual, approved, resource_version):
    if actual != approved or not isinstance(resource_version, int) or resource_version <= 0:
        raise ValueError("Linked policy bytes/version do not match the approved tone contract")
    return {"sha256": hashlib.sha256(actual).hexdigest(), "resourceVersion": resource_version}


def draft_result(draft):
    if draft.get("decision") != "APPROVE" or draft.get("refundAmount") != 482.17:
        raise ValueError("Both approved tone variants require APPROVE / 482.17")
    rationale = draft.get("rationale")
    if not isinstance(rationale, str) or not rationale.strip():
        raise ValueError("Missing generated draft rationale")
    return rationale


def cold_record(draft, run_id, identity, policy):
    if not run_id or not identity.get("bpmnSha256") or not identity.get("inputManifestSha256") \
            or not isinstance(identity.get("runtime"), dict) or not identity["runtime"].get("sha256"):
        raise ValueError("Cold rationale requires current run/snapshot/runtime identity")
    return {"runId": run_id, "identity": identity, "policy": policy, "rationale": draft_result(draft)}


def compare(cold, warm, run_id, identity):
    if cold.get("runId") != run_id or cold.get("identity") != identity:
        raise ValueError("Cold rationale belongs to another run, snapshot or runtime")
    if not isinstance(cold.get("rationale"), str) or not cold["rationale"].strip():
        raise ValueError("Missing current-run cold rationale")
    if draft_result(warm).strip() == cold["rationale"].strip():
        raise ValueError("Warm rationale must differ from the current-run cold rationale")
    return True


def main():
    root = Path(__file__).resolve().parent
    target = root / "target"
    mode = sys.argv[1]
    if mode == "begin":
        from daytona_proof import current_identity
        state = {"runId": str(uuid.uuid4()), "identity": current_identity()}
        (target / "tone-run.json").write_text(json.dumps(state))
        (target / "tone-cold.json").unlink(missing_ok=True)
        (target / "tone-warm.json").unlink(missing_ok=True)
        return
    state = json.loads((target / "tone-run.json").read_text())
    from daytona_proof import runtime_identity
    if runtime_identity() != state["identity"]["runtime"]:
        raise ValueError("Runtime changed during cold/warm comparison")
    request = json.loads(Path(sys.argv[2]).read_text())
    policy = policy_bytes(bytes(request["policyBytes"]),
                          (root / request["approvedPolicy"]).read_bytes(), request["resourceVersion"])
    if mode == "cold":
        record = cold_record(request["draft"], state["runId"], state["identity"], policy)
        (target / "tone-cold.json").write_text(json.dumps(record))
    elif mode == "warm":
        cold = json.loads((target / "tone-cold.json").read_text())
        compare(cold, request["draft"], state["runId"], state["identity"])
        warm = {
            "runId": state["runId"], "identity": state["identity"], "policy": policy,
            "rationale": draft_result(request["draft"]), "humanToneAcceptance": "not automated"}
        (target / "tone-warm.json").write_text(json.dumps(warm))
        artifact = root / "proofs/artifacts" / ("tone-" + state["runId"] + ".json")
        artifact.parent.mkdir(parents=True, exist_ok=True)
        artifact.write_text(json.dumps({"cold": cold, "warm": warm}, indent=2))
    else:
        raise ValueError("Unknown tone evidence operation")


if __name__ == "__main__":
    main()
