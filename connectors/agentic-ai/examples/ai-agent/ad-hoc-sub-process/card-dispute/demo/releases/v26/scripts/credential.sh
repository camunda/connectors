#!/usr/bin/env bash
# Creates a saved LLM credential (#86, OpenAI-compatible custom backend) on the prototype cluster.
#
# The credential is a SECRET_REFERENCE cluster variable. Its value holds only camunda.secrets.*
# references; the engine resolves them from the cluster's file secret store ($RUN/secrets) when
# the agent job is activated, so the API key never becomes a process variable in Operate.
#
#   scripts/credential.sh                        # cardDisputeLlm from CAMUNDA_PROVIDED_LLM_*
#   NAME=cardDisputeLlmGlobal PREFIX=CARD_DISPUTE_LLM_GLOBAL \
#     SRC=GLOBAL_LLM scripts/credential.sh       # reads GLOBAL_LLM_API_ENDPOINT/_API_KEY/_DEFAULT_MODEL
#
# Values are read from the environment and never printed.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
CAMUNDA_URL="${CAMUNDA_URL:-http://localhost:8280}"
NAME="${NAME:-cardDisputeLlm}"
PREFIX="${PREFIX:-CARD_DISPUTE_LLM}"
SRC="${SRC:-CAMUNDA_PROVIDED_LLM}"
STORE="$ROOT/run/secrets"

endpoint_var="${SRC}_API_ENDPOINT"; key_var="${SRC}_API_KEY"; model_var="${SRC}_DEFAULT_MODEL"
for v in "$endpoint_var" "$key_var" "$model_var"; do
  [ -n "${!v:-}" ] || { echo "missing env $v" >&2; exit 1; }
done

umask 077
mkdir -p "$STORE"; chmod 700 "$STORE"
printf '%s' "${!endpoint_var}" >"$STORE/${PREFIX}_ENDPOINT"
printf '%s' "${!key_var}" >"$STORE/${PREFIX}_API_KEY"
printf '%s' "${!model_var}" >"$STORE/${PREFIX}_MODEL"

body=$(python3 - "$NAME" "$PREFIX" <<'PY'
import json, sys
name, prefix = sys.argv[1], sys.argv[2]
print(json.dumps({
    "name": name,
    "kind": "SECRET_REFERENCE",
    "metadata": {
        "kind": "CREDENTIAL",
        "configurationTemplate": "io.camunda:agentic-ai-openai-custom-credential:1",
        "configurationTemplateVersion": "1",
        "displayName": "Card dispute LLM" + (" (global endpoint)" if "GLOBAL" in prefix else ""),
    },
    "value": {
        "endpoint": f"camunda.secrets.{prefix}_ENDPOINT",
        "apiKey": f"camunda.secrets.{prefix}_API_KEY",
        "model": f"camunda.secrets.{prefix}_MODEL",
    },
}))
PY
)

code=$(curl -sS -o /dev/null -w '%{http_code}' "$CAMUNDA_URL/v2/cluster-variables/global/$NAME")
if [ "$code" = 200 ]; then
  code=$(printf '%s' "$body" | python3 -c 'import json,sys; b=json.load(sys.stdin); b.pop("name"); b.pop("kind"); print(json.dumps(b))' |
    curl -sS -o /dev/null -w '%{http_code}' -X PUT "$CAMUNDA_URL/v2/cluster-variables/global/$NAME" \
      -H 'Content-Type: application/json' --data-binary @-)
  echo "updated credential $NAME -> HTTP $code"
else
  code=$(printf '%s' "$body" | curl -sS -o /dev/null -w '%{http_code}' -X POST \
    "$CAMUNDA_URL/v2/cluster-variables/global" -H 'Content-Type: application/json' --data-binary @-)
  echo "created credential $NAME -> HTTP $code"
fi
[ "$code" = 200 ] || [ "$code" = 201 ] || [ "$code" = 204 ]
