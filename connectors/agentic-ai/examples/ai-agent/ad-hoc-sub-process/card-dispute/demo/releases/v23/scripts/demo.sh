#!/usr/bin/env bash
# Card dispute demo driver. Unsupported prototype; synthetic data only.
#   demo.sh deploy        Deploy the process, the form, and policy v1.
#   demo.sh policy-v1     Reset: deploy only the v1 policy as the newest version.
#   demo.sh policy-v2     Deploy only the v2 policy (same resource name, new version).
#   demo.sh start         Upload the PDF, CSV, skill previews and configured Skills ZIP, then start.
#   demo.sh start-variables  Upload the same documents and print only the start variables.
#   demo.sh complete-review TASK '{"analystVerdict":"accept"}'  Validate and complete a review.
#   demo.sh e2e-check     Real-Daytona proof for the deployed model: preflight here, then the
#                         CPT gate's shared proof (cpt-gate/gate.sh --e2e). Uses one Daytona
#                         sandbox (1 vCPU, 1 GiB RAM, 3 GiB disk quota) and one A2A conversation.
# Environment: CAMUNDA_URL (default http://localhost:8280), C8_PROFILE (default local-proto),
#   PROCESS_ID (default CardDispute).
#   EXTRA_VARS (optional JSON object merged into the start variables, e.g.
#     '{"daytonaApiUrl":"http://localhost:9"}' to force a sandbox outage).
set -euo pipefail

DEMO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
CAMUNDA_URL="${CAMUNDA_URL:-http://localhost:8280}"
C8_PROFILE="${C8_PROFILE:-local-proto}"
PROCESS_ID="${PROCESS_ID:-CardDispute}"
GATE_DIR="${GATE_DIR:-$(cd "${DEMO_DIR}/../../cpt-gate" 2>/dev/null && pwd || true)}"
STATE_DIR="${DEMO_DIR}/.state"

latest_policy_version() {
  curl -sf -X POST "${CAMUNDA_URL}/v2/resources/search" -H 'content-type: application/json' \
    -d '{"filter":{"resourceName":"dispute-compliance-policy.md"},"sort":[{"field":"version","order":"DESC"}],"page":{"limit":1}}' \
    | python3 -c 'import json,sys; items=json.load(sys.stdin)["items"]; print(items[0]["version"] if items else 0)'
}

record_policy() {
  local variant="$1" previous="$2" version tries=0
  # Other clients deploy policies too, so compare against the live pre-deploy version.
  while :; do
    version="$(latest_policy_version)"
    (( version > previous || ++tries >= 30 )) && break
    sleep 1
  done
  if (( version <= previous )); then
    echo "policy search still shows version ${version}; state was not updated" >&2
    return 1
  fi
  mkdir -p "${STATE_DIR}"
  printf '{"variant":"%s","version":%s}\n' "${variant}" "${version}" > "${STATE_DIR}/policy-latest.json"
}

upload_document() {
  local path="$1" content_type="$2"
  local auth_args=()
  [[ -z "${CAMUNDA_TOKEN:-}" ]] || auth_args=(-H "authorization: Bearer ${CAMUNDA_TOKEN}")
  curl -sf -X POST "${CAMUNDA_URL}/v2/documents" \
    ${auth_args[@]+"${auth_args[@]}"} \
    -F "file=@${path};type=${content_type}" \
    -F "metadata={\"contentType\":\"${content_type}\",\"fileName\":\"$(basename "${path}")\"};type=application/json"
}

case "${1:-}" in
  complete-review)
    [[ $# == 3 && "$2" =~ ^[0-9]+$ ]] || {
      echo 'usage: demo.sh complete-review TASK_KEY VARIABLES_JSON' >&2
      exit 1
    }
    variables="$(python3 - "$3" <<'PY'
import json, sys
variables = json.loads(sys.argv[1])
if not isinstance(variables, dict) or set(variables) - {"analystVerdict", "analystFeedback"}:
    raise ValueError("Only analystVerdict and optional revise analystFeedback are permitted")
verdict = variables.get("analystVerdict")
if verdict not in ("accept", "revise"):
    raise ValueError("analystVerdict must be exactly accept or revise")
if verdict == "accept" and set(variables) != {"analystVerdict"}:
    raise ValueError("accept permits only analystVerdict")
if verdict == "revise" and (
    not isinstance(variables.get("analystFeedback"), str)
    or not variables["analystFeedback"].strip()
):
    raise ValueError("revise requires nonempty analystFeedback")
print(json.dumps({"variables": variables}))
PY
)"
    auth_args=()
    [[ -z "${CAMUNDA_TOKEN:-}" ]] || auth_args=(-H "authorization: Bearer ${CAMUNDA_TOKEN}")
    task="$(curl -fsS "${CAMUNDA_URL}/v2/user-tasks/$2" ${auth_args[@]+"${auth_args[@]}"})"
    python3 - "${task}" <<'PY'
import json, sys
task = json.loads(sys.argv[1])
if task["elementId"] != "ReviewDraftedDecision" or task["state"] != "CREATED":
    raise ValueError("Task must be an open ReviewDraftedDecision")
PY
    curl -fsS -o /dev/null -X POST "${CAMUNDA_URL}/v2/user-tasks/$2/assignment" \
      ${auth_args[@]+"${auth_args[@]}"} -H 'content-type: application/json' \
      -d '{"assignee":"demo","allowOverride":true}'
    curl -fsS -o /dev/null -X POST "${CAMUNDA_URL}/v2/user-tasks/$2/completion" \
      ${auth_args[@]+"${auth_args[@]}"} -H 'content-type: application/json' -d "${variables}"
    echo "Review task $2 completed with validated analystVerdict"
    ;;
  deploy)
    previous="$(latest_policy_version)"
    c8ctl deploy --profile "${C8_PROFILE}" \
      "${DEMO_DIR}/card-dispute.bpmn" \
      "${DEMO_DIR}/review-dispute.form" \
      "${DEMO_DIR}/policies/dispute-compliance-policy.md"
    record_policy v1 "${previous}"
    ;;
  policy-v1)
    previous="$(latest_policy_version)"
    c8ctl deploy --profile "${C8_PROFILE}" "${DEMO_DIR}/policies/dispute-compliance-policy.md"
    record_policy v1 "${previous}"
    ;;
  policy-v2)
    previous="$(latest_policy_version)"
    c8ctl deploy --profile "${C8_PROFILE}" "${DEMO_DIR}/policies/v2/dispute-compliance-policy.md"
    record_policy v2 "${previous}"
    ;;
  e2e-check)
    started=$(date +%s)
    fail() { echo "e2e-check FAIL: $*" >&2; exit 1; }
    [[ -x "${GATE_DIR}/gate.sh" ]] || fail "CPT gate not found (set GATE_DIR)"
    curl -sf -o /dev/null "${CAMUNDA_URL}/v2/topology" || fail "engine not reachable at ${CAMUNDA_URL}"
    curl -sf -o /dev/null "http://localhost:8286/actuator/health" || fail "connectors not reachable on :8286"
    # Exact deployment identity: the latest deployed BPMN bytes must equal the source file.
    read -r def_key def_version < <(curl -sf -X POST "${CAMUNDA_URL}/v2/process-definitions/search" -H 'content-type: application/json' \
      -d "{\"filter\":{\"processDefinitionId\":\"${PROCESS_ID}\"},\"sort\":[{\"field\":\"version\",\"order\":\"DESC\"}],\"page\":{\"limit\":1}}" \
      | python3 -c 'import json,sys; i=json.load(sys.stdin)["items"][0]; print(i["processDefinitionKey"], i["version"])') \
      || fail "no deployed ${PROCESS_ID}"
    deployed_sha="$(curl -sf "${CAMUNDA_URL}/v2/process-definitions/${def_key}/xml" | shasum -a 256 | cut -d' ' -f1)"
    source_sha="$(shasum -a 256 "${DEMO_DIR}/card-dispute.bpmn" | cut -d' ' -f1)"
    [[ "${deployed_sha}" == "${source_sha}" ]] || fail "deployed v${def_version} differs from card-dispute.bpmn; run 'demo.sh deploy' deliberately first"
    # Policy v1 must be the latest version; this command never changes the policy.
    latest="$(curl -sf -X POST "${CAMUNDA_URL}/v2/resources/search" -H 'content-type: application/json' \
      -d '{"filter":{"resourceName":"dispute-compliance-policy.md"},"sort":[{"field":"version","order":"DESC"}],"page":{"limit":1}}' \
      | python3 -c 'import json,sys; print(json.load(sys.stdin)["items"][0]["version"])')"
    python3 - "${STATE_DIR}/policy-latest.json" "${latest}" <<'PY' || fail "policy v1 is not the latest version; run 'demo.sh policy-v1' first"
import json, sys
state = json.load(open(sys.argv[1]))
sys.exit(0 if state["variant"] == "v1" and state["version"] == int(sys.argv[2]) else 1)
PY
    # Credentials by reference only: the connectors process must carry the secret names.
    pid="$(cat "${DEMO_DIR}/../run/connectors.java.pid")"
    for name in SECRET_DAYTONA_API_KEY SECRET_FRAUD_AGENT_TOKEN SECRET_A2A_PUSH_TOKEN; do
      ps eww "${pid}" | grep -q "${name}=" || fail "connectors lack ${name}"
    done
    echo "e2e-check: ${PROCESS_ID} v${def_version} key ${def_key} sha256 ${source_sha}, policy v1 (resource version ${latest})"
    echo "e2e-check: quota: one Daytona sandbox (1 vCPU, 1 GiB RAM, 3 GiB disk) and one A2A conversation"
    result=PASS
    "${GATE_DIR}/gate.sh" --e2e || result=FAIL
    proof="${GATE_DIR}/proofs/last-real-daytona.json"
    if [[ -f "${proof}" ]]; then
      python3 - "${proof}" <<'PY'
import json, sys
p = json.load(open(sys.argv[1]))
keys = ("processInstanceKey", "version", "bpmnSha256", "sandboxId", "cleanup")
print("e2e-check proof: " + ", ".join(f"{k}={p.get(k)}" for k in keys))
PY
    fi
    echo "e2e-check ${result} in $(( $(date +%s) - started ))s"
    [[ "${result}" == PASS ]]
    ;;
  start-variables)
    skill_dir="${DEMO_DIR}/skills/chargeback-deadline"
    skill_bundle="${DEMO_DIR}/skills/chargeback-deadline.zip"
    python3 - "${skill_dir}" "${skill_bundle}" <<'PY'
import sys, zipfile
from pathlib import Path
skill, bundle = map(Path, sys.argv[1:])
paths = {"SKILL.md", "references/network-rules.md", "scripts/deadline.py"}
with zipfile.ZipFile(bundle) as archive:
    if len(archive.namelist()) != len(paths) or set(archive.namelist()) != paths:
        raise ValueError("Skills ZIP must contain exactly the three original skill files")
    for name in paths:
        if archive.read(name) != (skill / name).read_bytes():
            raise ValueError(f"Skills ZIP differs from the original file: {name}")
PY
    statement="$(upload_document "${DEMO_DIR}/data/statement-2026-09.pdf" application/pdf)"
    export_doc="$(upload_document "${DEMO_DIR}/data/transactions-2026-09.csv" text/csv)"
    skill_doc="$(upload_document "${skill_dir}/SKILL.md" text/markdown)"
    reference_doc="$(upload_document "${skill_dir}/references/network-rules.md" text/markdown)"
    script_doc="$(upload_document "${skill_dir}/scripts/deadline.py" text/x-python)"
    bundle_doc="$(upload_document "${skill_bundle}" application/zip)"
    variables="$(python3 - "${statement}" "${export_doc}" "${CAMUNDA_URL}" "${skill_doc}" "${reference_doc}" "${script_doc}" "${skill_dir}" "${bundle_doc}" "${skill_bundle}" <<'PY'
import hashlib, json, os, sys
from pathlib import Path
statement, export_doc, url, skill_doc, reference_doc, script_doc, skill_dir, bundle_doc, bundle_path = sys.argv[1:]
extra = json.loads(os.environ.get("EXTRA_VARS") or "{}")
skill = Path(skill_dir)
manifest = {name: hashlib.sha256((skill / name).read_bytes()).hexdigest()
            for name in ("SKILL.md", "references/network-rules.md", "scripts/deadline.py")}
print(json.dumps({
    "disputeId": "DSP-2026-10-0042",
    "cardholderName": "Jordan Avery",
    "cardLast4": "4417",
    "claim": "I was charged twice for one headphones order from Northwind Electronics in mid-September. I only placed one order.",
    "disputedAmount": 482.17,
    "clusterApiUrl": url,
    **extra,
    "disputeFilingDate": extra.get("disputeFilingDate", "2026-10-20"),
    "disputeDocuments": [json.loads(doc) for doc in
                         (statement, export_doc, skill_doc, reference_doc, script_doc)],
    "deadlineScriptSha256": manifest["scripts/deadline.py"],
    "deadlineSkillManifest": manifest,
    "deadlineSkillBundles": [json.loads(bundle_doc)],
    "deadlineSkillBundleSha256": hashlib.sha256(Path(bundle_path).read_bytes()).hexdigest(),
}))
PY
)"
    printf '%s\n' "${variables}"
    ;;
  start)
    variables="$("$0" start-variables)"
    c8ctl create pi --profile "${C8_PROFILE}" --id "${PROCESS_ID}" --variables "${variables}"
    ;;
  *)
    sed -n '2,11p' "$0"
    exit 1
    ;;
esac
