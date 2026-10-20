#!/usr/bin/env bash
# One-step switch for #83 (OpenRouter server-tool web search). Off by default.
#
# OpenRouter rejects openrouter:web_search on region-specific endpoints (HTTP 403), and the demo
# credential cardDisputeLlm uses a regional endpoint. Turning web search on means sending prompts to
# the global endpoint https://openrouter.ai/api/v1, which changes data residency, so only run this
# after that has been approved.
#
#   export GLOBAL_LLM_API_ENDPOINT=https://openrouter.ai/api/v1 GLOBAL_LLM_API_KEY=... GLOBAL_LLM_DEFAULT_MODEL=...
#   scripts/web-search.sh on      # creates credential cardDisputeLlmGlobal, deploys the web-search variant
#   scripts/web-search.sh off     # redeploys the default model (regional endpoint, no web search)
#   scripts/web-search.sh render  # only writes variants/card-dispute-web-search.bpmn
set -euo pipefail

DEMO="$(cd "$(dirname "$0")/.." && pwd)"
SRC_MODEL="$DEMO/card-dispute.bpmn"
VARIANT="$DEMO/variants/card-dispute-web-search.bpmn"

render() {
  mkdir -p "$DEMO/variants"
  python3 - "$SRC_MODEL" "$VARIANT" <<'PY'
import sys
src, dst = sys.argv[1], sys.argv[2]
s = open(src).read()

def swap(old, new):
    global s
    assert s.count(old) == 1, f"expected exactly one match for: {old[:80]}"
    s = s.replace(old, new)

swap('<zeebe:input source="responses" target="provider.openai.api.type" />',
     '<zeebe:input source="completions" target="provider.openai.api.type" />')
swap('<zeebe:input source="=camunda.vars.env.cardDisputeLlm" target="provider.openai.backend.custom.openAiCustomCredential" />',
     '<zeebe:input source="=camunda.vars.env.cardDisputeLlmGlobal" target="provider.openai.backend.custom.openAiCustomCredential" />')
swap('<zeebe:input source="modelDefault" target="provider.openai.api.responses.effort" />',
     '<zeebe:input source="=[{type: &#34;web_search&#34;, parameters: {maxResults: 3, allowedDomains: [&#34;mastercard.com&#34;]}}]" target="provider.openai.api.completions.serverTools" />')
swap('Fetch the network rule before reconciliation. ',
     'Fetch the network rule before reconciliation. Also use the provider web search, limited to mastercard.com, '
     'to find the published duplicate-processing rule, and cite its source URL in the evidence. '
     'The fetched network rule stays authoritative for the decision. ')
start = s.index('<bpmn:textAnnotation id="Note_WebFetch">')
t0 = s.index('<bpmn:text>', start) + len('<bpmn:text>')
t1 = s.index('</bpmn:text>', t0)
s = s[:t0] + ('8.11 prototype (#83): the agent also has an OpenRouter server tool, openrouter:web_search, '
              'limited to mastercard.com. The model provider runs it, not a BPMN task, and returns cited URLs '
              '(agent context: openai.citations). Uses the global OpenRouter endpoint. This REST task stays '
              'as the authoritative source of the synthetic rules.') + s[t1:]
open(dst, 'w').write(s)
print(f"wrote {dst}")
PY
}

case "${1:-}" in
  render) render ;;
  on)
    render
    NAME=cardDisputeLlmGlobal PREFIX=CARD_DISPUTE_LLM_GLOBAL SRC=GLOBAL_LLM "$DEMO/scripts/credential.sh"
    c8ctl deploy "$VARIANT" "$DEMO/review-dispute.form" --profile local-proto
    ;;
  off)
    c8ctl deploy "$SRC_MODEL" "$DEMO/review-dispute.form" --profile local-proto
    ;;
  *) echo "usage: $0 on|off|render" >&2; exit 2 ;;
esac
