#!/usr/bin/env bash
# Card dispute demo: one unattended rehearsal over the Camunda 8 REST API (v2).
# Unsupported prototype. Synthetic data only. Never prints secrets.
#
#   rehearse.sh --dry-run              Read-only preflight. Search endpoints and GETs only.
#   rehearse.sh --summary <piKey>      Read-only. Print the draft of an existing instance.
#   rehearse.sh                        Start one dispute, wait for the review task, print the draft.
#   rehearse.sh --accept               Same, then complete the review task with "accept".
#   rehearse.sh --revise "<feedback>"  Same, then send the draft back once and print the revision.
#
# Environment:
#   CAMUNDA_URL      Orchestration cluster REST base URL (default http://localhost:8280, the from-source prototype cluster).
#   CAMUNDA_TOKEN    Optional bearer token. Read from the environment, never printed.
#   FRAUD_AGENT_URL  A2A fraud agent base URL for the preflight (default http://localhost:38090).
#   DEMO_DIR         Demo folder with data/ (default: this script's folder).
#   TIMEOUT_SECONDS  How long to wait for the review task (default 300).
#   EXTRA_VARS       Optional JSON object merged into the start variables, e.g. '{"daytonaApiUrl":"http://localhost:9"}'.
set -euo pipefail
set +x

CAMUNDA_URL="${CAMUNDA_URL:-http://localhost:8280}"
CAMUNDA_URL="${CAMUNDA_URL%/}"
FRAUD_AGENT_URL="${FRAUD_AGENT_URL:-http://localhost:38090}"
DEMO_DIR="${DEMO_DIR:-$(cd "$(dirname "$0")" && pwd)}"
TIMEOUT_SECONDS="${TIMEOUT_SECONDS:-300}"
PROCESS_ID="CardDispute"

auth_args=()
if [[ -n "${CAMUNDA_TOKEN:-}" ]]; then
  auth_args=(-H "Authorization: Bearer ${CAMUNDA_TOKEN}")
fi

die() { echo "ERROR: $*" >&2; exit 1; }
say() { echo "==> $*"; }

api() {
  # api METHOD PATH [JSON_BODY]
  local method="$1" path="$2" body="${3:-}"
  local args=(-sS --fail-with-body -X "${method}" "${CAMUNDA_URL}${path}" -H 'accept: application/json')
  if [[ -n "${body}" ]]; then
    args+=(-H 'content-type: application/json' -d "${body}")
  fi
  curl "${args[@]}" ${auth_args[@]+"${auth_args[@]}"}
}

json() { python3 -c "import json,sys; d=json.load(sys.stdin); $1"; }

print_draft() {
  # Reads the draft JSON on stdin and prints an analyst-style summary.
  python3 -c '
import json, sys
raw = sys.stdin.read().strip()
if not raw:
    print("   (no draft variable found)"); sys.exit(0)
d = json.loads(raw)
if isinstance(d, str):
    d = json.loads(d)
print("   Decision      : %s" % d.get("decision"))
print("   Refund amount : %s" % d.get("refundAmount"))
print("   Reason code   : %s" % d.get("reasonCode"))
print("   Rationale     : %s" % d.get("rationale"))
print("   Evidence:")
for e in d.get("evidence") or []:
    print("     - %s" % e)
fraud = [e for e in d.get("evidence") or [] if "fraud" in str(e).lower()]
print("   Fraud opinion : %s" % (fraud[0] if fraud else "NOT CITED (the form shows a warning)"))
'
}

latest_policy_version() {
  local key
  key="$(api POST /v2/resources/search \
    '{"filter":{"resourceName":"dispute-compliance-policy.md"},"sort":[{"field":"version","order":"DESC"}],"page":{"limit":1}}' \
    | json 'i=d.get("items") or []; print(i[0]["resourceKey"] if i else "")')"
  [[ -n "${key}" ]] || { echo "none"; return; }
  curl -sS --fail "${CAMUNDA_URL}/v2/resources/${key}/content/binary" ${auth_args[@]+"${auth_args[@]}"} \
    | python3 -c 'import sys; text=sys.stdin.read(); print("v1 (cold)" if "cold and professional" in text else "v2 (warm)" if "warm and friendly" in text else "unknown")'
}

expected_decision() {
  case "$1" in
    "v1 (cold)"|"v2 (warm)") echo "APPROVE" ;;
    *) echo "unknown" ;;
  esac
}

review_task_key() {
  api POST /v2/user-tasks/search \
    "{\"filter\":{\"processInstanceKey\":\"$1\",\"elementId\":\"ReviewDraftedDecision\",\"state\":\"CREATED\"},\"page\":{\"limit\":1}}" \
    | json 'i=d.get("items") or []; print(i[0]["userTaskKey"] if i else "")'
}

task_draft() {
  api POST "/v2/user-tasks/$1/variables/search" '{"filter":{"name":"draft"},"page":{"limit":1}}' \
    | json 'i=d.get("items") or []; print(i[0]["value"] if i else "")'
}

instance_draft() {
  # Latest draft of an instance, including completed review tasks.
  api POST /v2/variables/search \
    "{\"filter\":{\"processInstanceKey\":\"$1\",\"name\":\"draft\"},\"page\":{\"limit\":50}}" \
    | json 'i=d.get("items") or []; print(i[-1]["value"] if i else "")'
}

wait_for_review() {
  # wait_for_review PI_KEY [EXCLUDE_TASK_KEY]
  local pi="$1" exclude="${2:-}" waited=0 key state
  while (( waited < TIMEOUT_SECONDS )); do
    key="$(review_task_key "${pi}")"
    if [[ -n "${key}" && "${key}" != "${exclude}" ]]; then
      echo "${key}"; return 0
    fi
    state="$(api GET "/v2/process-instances/${pi}" 2>/dev/null | json 'print(d.get("state",""))' 2>/dev/null || true)"
    if [[ -n "${state}" && "${state}" != "ACTIVE" ]]; then
      die "instance ${pi} is ${state}. Check incidents in Operate."
    fi
    incidents="$(api POST /v2/incidents/search \
      "{\"filter\":{\"processInstanceKey\":\"${pi}\",\"state\":\"ACTIVE\"},\"page\":{\"limit\":1}}" \
      | json 'print(d.get("page",{}).get("totalItems",0))')"
    if [[ "${incidents}" != "0" ]]; then
      die "instance ${pi} has an active incident. Open it in Operate: ${CAMUNDA_URL}/operate/processes/${pi}"
    fi
    sleep 5; waited=$((waited + 5)); echo "   ... waiting for the agent (${waited}s)" >&2
  done
  die "no review task after ${TIMEOUT_SECONDS}s for instance ${pi}"
}

complete_task() {
  # complete_task TASK_KEY VARIABLES_JSON
  CAMUNDA_URL="${CAMUNDA_URL}" "${DEMO_DIR}/scripts/demo.sh" complete-review "$1" "$2"
}

dry_run() {
  local ok=0
  say "Preflight against ${CAMUNDA_URL} (read-only)"
  local topology
  topology="$(api GET /v2/topology 2>/dev/null)" || die "cluster not reachable at ${CAMUNDA_URL}"
  echo "${topology}" | json 'b=d.get("brokers") or []; print("   Cluster: gateway %s, %d broker(s)" % (d.get("gatewayVersion"), len(b)))'

  api POST /v2/process-definitions/search \
    "{\"filter\":{\"processDefinitionId\":\"${PROCESS_ID}\"},\"sort\":[{\"field\":\"version\",\"order\":\"DESC\"}],\"page\":{\"limit\":1}}" \
    | json 'i=d.get("items") or []; print("   Process %s: version %s (key %s)" % (i[0]["processDefinitionId"], i[0]["version"], i[0]["processDefinitionKey"]) if i else "   FAIL: CardDispute not deployed")' \
    || ok=1

  local pv; pv="$(latest_policy_version)"
  echo "   Latest compliance policy: ${pv:-unknown}. Expected agent decision: $(expected_decision "${pv}")"

  api POST /v2/user-tasks/search \
    '{"filter":{"elementId":"ReviewDraftedDecision","state":"CREATED"},"page":{"limit":20}}' \
    | json 'n=d.get("page",{}).get("totalItems",0); print("   Open review tasks: %s%s" % (n, "  (complete or cancel them before the meeting for a clean Tasklist)" if n else ""))' \
    || ok=1

  api POST /v2/process-instances/search \
    "{\"filter\":{\"processDefinitionId\":\"${PROCESS_ID}\",\"state\":\"ACTIVE\"},\"page\":{\"limit\":20}}" \
    | json 'n=d.get("page",{}).get("totalItems",0); print("   Active CardDispute instances: %s" % n)' || ok=1

  api POST /v2/incidents/search '{"filter":{"processDefinitionId":"CardDispute","state":"ACTIVE"},"page":{"limit":20}}' \
    | json 'n=d.get("page",{}).get("totalItems",0); print("   Active CardDispute incidents: %s" % n)' || ok=1

  if curl -sS --fail -m 5 "${FRAUD_AGENT_URL%/}/.well-known/agent-card.json" \
       | json 'print("   Fraud agent card: %s (A2A %s)" % (d.get("name"), (d.get("supportedInterfaces") or [{}])[0].get("protocolVersion", d.get("protocolVersion","?"))))' 2>/dev/null; then :
  else
    echo "   WARN: fraud agent not reachable at ${FRAUD_AGENT_URL}. The draft will say the opinion is inconclusive and ESCALATE."
  fi

  for f in data/statement-2026-09.pdf data/transactions-2026-09.csv skills/chargeback-deadline.zip skills/chargeback-deadline/SKILL.md skills/chargeback-deadline/references/network-rules.md skills/chargeback-deadline/scripts/deadline.py; do
    if [[ -f "${DEMO_DIR}/${f}" ]]; then echo "   Data file: ${f}"; else echo "   FAIL: missing ${DEMO_DIR}/${f}"; ok=1; fi
  done
  python3 - "${DEMO_DIR}/card-dispute.bpmn" <<'PY' || ok=1
import sys
import xml.etree.ElementTree as ET
ns = {"z": "http://camunda.org/schema/zeebe/1.0"}
root = ET.parse(sys.argv[1])
prompt = root.find('.//z:input[@target="data.userPrompt.prompt"]', ns).get("source")
documents = root.find('.//z:input[@target="data.userPrompt.documents"]', ns).get("source")
skills = root.find('.//z:input[@target="data.skills"]', ns)
if documents != "=disputeDocuments" or not all(
    name in prompt for name in ("disputeFilingDate", "sandbox_bash",
                               ".agents/skills/chargeback-deadline")
) or skills is None or skills.get("source") != "=deadlineSkillBundles":
    print("   FAIL: model is missing the configured Skills ZIP/date/tool input contract")
    sys.exit(1)
print("   Skill input contract: configured ZIP, preview documents, explicit filing date, sandbox tool execution")
PY
  if (( ok == 0 )); then say "Preflight passed"; else die "Preflight found problems"; fi
}

start_instance() {
  local variables
  variables="$(CAMUNDA_URL="${CAMUNDA_URL}" "${DEMO_DIR}/scripts/demo.sh" start-variables \
    | python3 -c 'import json,sys; print(json.dumps({"processDefinitionId": sys.argv[1], "variables": json.load(sys.stdin)}))' "${PROCESS_ID}")"
  api POST /v2/process-instances "${variables}" | json 'print(d["processInstanceKey"])'
}

mode="${1:-run}"
case "${mode}" in
  --dry-run) dry_run ;;
  --summary)
    pi="${2:-}"; [[ -n "${pi}" ]] || die "usage: rehearse.sh --summary <processInstanceKey>"
    say "Draft of instance ${pi} (read-only)"
    instance_draft "${pi}" | print_draft
    ;;
  run|--accept|--revise)
    feedback="${2:-}"
    [[ "${mode}" != "--revise" || -n "${feedback}" ]] || die "usage: rehearse.sh --revise \"<feedback>\""
    pv="$(latest_policy_version)"; expected="$(expected_decision "${pv}")"
    say "Starting a dispute. Latest policy ${pv}, expected decision ${expected}"
    pi="$(start_instance)"
    say "Instance ${pi}: ${CAMUNDA_URL}/operate/processes/${pi}"
    task="$(wait_for_review "${pi}")"
    say "Review task ${task}: ${CAMUNDA_URL}/tasklist/${task}"
    draft="$(task_draft "${task}")"
    echo "${draft}" | print_draft
    actual="$(echo "${draft}" | json 'd=json.loads(d) if isinstance(d,str) else d; print(d.get("decision"))' 2>/dev/null || echo "?")"
    if [[ "${actual}" == "${expected}" ]]; then say "Decision matches policy ${pv}"; else echo "WARN: decision ${actual} differs from expected ${expected}. Check the fraud opinion in the evidence."; fi
    if [[ "${mode}" == "--revise" ]]; then
      complete_task "${task}" "$(python3 -c 'import json,sys; print(json.dumps({"analystVerdict":"revise","analystFeedback":sys.argv[1]}))' "${feedback}")"
      say "Sent back with feedback. Waiting for the revision"
      task="$(wait_for_review "${pi}" "${task}")"
      task_draft "${task}" | print_draft
      say "Revision is in Tasklist: ${CAMUNDA_URL}/tasklist/${task}"
    elif [[ "${mode}" == "--accept" ]]; then
      complete_task "${task}" '{"analystVerdict":"accept"}'
      say "Accepted. The instance completes at Dispute decided."
    else
      say "Left open in Tasklist for the presenter"
    fi
    ;;
  *) sed -n '2,17p' "$0"; exit 1 ;;
esac
