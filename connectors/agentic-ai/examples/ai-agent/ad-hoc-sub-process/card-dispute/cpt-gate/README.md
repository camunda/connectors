# Card dispute 8.11 done-gate

Run from this directory:

```sh
./gate.sh
```

The gate exits non-zero on any failed/missing check, unavailable runtime, or coverage below
100%. Web search (#83) has one narrow exception: a gate-owned live probe must prove the
regional rejection. Only then is it reported as `N/A: not available on EU account`, never
as live proof of web search. With every other check passing, the gate exits 0 and prints
`GATE: PASS (N/A: #83 web search not available on EU account)`.
Authentication (#113) remains required.
Do not declare the demo done while the gate is red.

Default live scenarios use mocked Daytona and consume no Daytona quota. Start variables
`daytonaApiUrl` and `daytonaApiKey` point the unmodified model to the localhost SDK stub with
a dummy credential. The gate requires stub requests, no `sandboxFallback` flag, no fallback
element execution and no evidence labelled as fallback; silent fallback cannot pass.

The ordinary gate also requires a current real-Daytona proof. It never spends a paid sandbox
automatically. Missing, corrupt, stale or pending/failed proof keeps it red with the remedy
`./gate.sh --e2e`, even when deterministic and default-live checks pass.

After successful deterministic and default-live checks on the same snapshot, `./gate.sh --e2e` runs L1 with
real LLM, real Daytona and at most one real A2A conversation, including revise/accept and
owned-sandbox deletion. This opt-in records separate `target/e2e.log`,
`target/surefire-e2e/` and `target/e2e.exit` evidence without replacing default live results.
It refuses source/deployment drift, runs the separate regional web-search probe, and does not
run L2. It does not deploy CardDispute or change policy: select policy v1 first. Stale proof does
not block this recovery path. A full resnapshot clears transient e2e reports, not persistent proof.

For offline deterministic work, use `./gate.sh --deterministic-only`. To retry only L2 after
a cluster outage, use `./gate.sh --live-only L2`. The retry does its own preflight, starts
only a stubbed-fraud v2 case, cleans up its own instance and restores v1 in finally. It never
repeats L1's real fraud conversation. Its latest result overrides L2's older result while
preserving L1's evidence. The overall gate still exits non-zero for any earlier missing/failed
scenario or required feature check; a successful L2 alone is not a successful full demo.

For the deployed v29 process, the working `source-cluster/demo/card-dispute.bpmn` and the
gate snapshot must match the deployed XML exactly (SHA-256
`b8bea0ef8c77cbf941452cd2da48a719984d481205f5b514fadccb0beac9419a`).
Structural checks accept the engine's default latest linked-prompt binding, check the
formatted initial/revision prompts, and verify the configured Skills ZIP against both its
unpacked files and `src/test/resources/v29/skill-manifest.json`. The older `round3` fixtures
remain unchanged for their historical import-based tests. Deterministic and Modeler parity
results do not certify the live tiers: the live harness still uses the older import-based
skill protocol and must be updated before running it against v29.

To check an existing v29 run without starting, completing, cancelling or modifying anything:

```sh
mvn test -Dtest=V29ConnectionTest -Dgate.processInstanceKey=<existing-v29-instance-key> \
  -Dgate.reports=target/surefire-v29-connection
```

This opt-in read-only check requires the run to have reached review. It checks exact deployed
BPMN bytes, formatted recorded input, linked-prompt provenance, tool call/result IDs, sandbox,
CSV reconciliation and pushed fraud results, the parsed draft, and unchanged process/task state.
It does not certify revision/acceptance, cleanup or the legacy live tier.

Retry is allowed only if both source and copied input checksums still match the prior
deterministic run. If the model/template changed, rerun deterministic first; that intentionally
invalidates all prior live evidence. Do not combine results from different model snapshots.
The final summary also rejects source drift during a run and labels old-snapshot results explicitly.
Retry logs are in `target/live-l2.log` and `target/surefire-live-l2/`.
The gate also runs its offline reporter tests (`python3 -B -m unittest -q test_gate_report.py`)
to check missing-result rejection, strict failures, retry precedence, source/copy drift rejection
and runtime coverage parsing.

## Prerequisites

Java 21, Maven, Python 3, Node.js with npm (Modeler parity; packages come from the npm cache or registry), Docker, the source-built Camunda distribution at
`../source-cluster/camunda/dist/target/camunda-zeebe`, and CPT 8.11.0-SNAPSHOT
(local Maven cache or Camunda snapshot repository access). No 8.10 engine fallback is used.
Use the source cluster's `camunda/camunda:8.11.0-proto-source` image. If absent, it is built from the
distribution using a Java 21 JRE. No c8run or existing stack is started/stopped/reconfigured.

The live prototype must already be healthy: REST `http://localhost:8280`, basic `demo/demo`,
gRPC `localhost:26710`, monitoring `9710`, connectors `http://localhost:8286`, and fraud agent
`http://localhost:38090/a2a`. This is the cluster selected by c8ctl profile `local-proto`;
the tests use the Java client directly, not c8ctl. The cluster must have the global
`cardDisputeLlm` variable with kind `SECRET_REFERENCE` and exactly `endpoint`, `apiKey`, and
`model`, all pointing to `camunda.secrets.*`. Connectors must resolve those references and
`DAYTONA_API_KEY`. The gate never reads secret files, prints credential values, or sets a real
provider/model. The model's OpenAI-compatible adapter is not a provider selection.

## What it proves

| Check | Evidence |
|---|---|
| D1 | Testcontainers, real source-built engine and FEEL; reconciliation, sandbox and fraud tools; CSV reconciliation and document-handle resolution; pushed fraud reply correlation; JSON extraction; revise feedback mapping; accept to completed. HTTP, LLM and sandbox jobs are mocked. |
| D2 | Warm policy case; synthetic APPROVE/482.17 JSON is parsed and accepted. This is a mapping/routing test, **not** proof of LLM policy evaluation. Retained as the explicit tone-demo override, despite zero unique coverage. |
| Sandbox failure regression (four cases) | Clean and parent-seeded CREATE/BASH errors. Activation/error variables remain null; the bare boundary collects exactly `{"error":"SANDBOX_FAILED","message":"The sandbox call failed. The filing deadline is not computed."}`. No stale handle/stdout/deadline, successful failed-task completion, fallback or review is allowed. The test cancels without finishing the agent. |
| Sandbox success regression (two cases) | Clean and parent-seeded CREATE followed by BASH. Distinct call IDs collect exactly the new connector handle/workDir and stdout/exit code, not the old parent result. Activation reset is checked on the current active element-instance scope. |
| D4 | Fraud reply never arrives: time travel beyond PT2M visits the timeout branch, requires a TIMEOUT tool result, then accepts to completion. |
| A2A stub self-test | Offline Java HTTP test verifies SUBMITTED plus an authenticated COMPLETED webhook callback, tokenless rejection and zero polling, without contacting the fraud agent. |
| Daytona stub self-test | The connector's SDK version exercises create/get, work directory, upload/download and command execution against localhost only. SDK responses are synthetic, not remote execution evidence. A separate fidelity test runs the hash-verified original script locally and requires the stub's JSON serialization to match its output exactly. |
| #75 | Exact cold/warm policy bytes and resource versions, with CONFIGURATION linked-prompt history. Both decisions must be APPROVE/482.17; warm rationale must differ from the cold rationale from this run, snapshot and runtime. `decision.policyVersion` is not required. Tone quality remains human storyboard acceptance. |
| #81 | Five document identities (PDF, CSV, SKILL.md, reference, original script), explicit filing date and original hashes. Completed snapshots use the exact owned persisted agent-context URL at the trusted local origin. L1 revision USER history separately reveals the initial completed snapshot. These provenance sources are recorded distinctly. Latest two results retain actual content; older pointers roundtrip by call ID to prior tool output. Docs/feedback and the complete ledger, including reconciliationFacts, survive revise, without a synthetic USER ledger turn. |
| #82 | Original skill imports and both reads precede original-script import. Conversation IDs/arguments/results are joined to completed AnalyzeInSandbox engine scopes and timestamps. Downloaded uploaded bytes, paths, unchanged hash-check/Python/hash-check chain, full JSON stdout and final citation are checked. Default live is explicitly synthetic Daytona output; only e2e proves real remote execution. |
| #83 web search | Applied template exposes the web-search binding, defaults to Off and describes the EU 403 restriction; the BPMN omits the serverTools input. `LiveTest.webSearchProbe` deploys only `CptGateWebSearchProbe`, uses the same saved credential and enables web search on Chat Completions with one job attempt. Only the exact regional rejection permits N/A. Other errors, missing evidence or failed cleanup fail the gate. There is no REST rules stand-in in Round 3. |
| #83 provider-side document parsing | Separate from web fetch: bodyProperties configures file-parser/native; live CONFIGURATION history records the same plugin/engine, USER history records documentParsing for statement-2026-09.pdf, and each draft quotes PDF-only statement ID AP-STM-7Q4K-0926 and rewards points 1,737 under "Card statement (PDF):". Deterministic payload includes a PDF handle; only live proves parsing. |
| #113 | Both A2A operations use protocol 1.0, bearer authentication and the unresolved FRAUD_AGENT_TOKEN secret reference. Live checks only the presence of a bearer header, never its value. |
| #86 | Built template exposes the custom-backend credential selector. BPMN binds `=camunda.vars.env.cardDisputeLlm` to `provider.openai.backend.custom.openAiCustomCredential`, with zero `CAMUNDA_PROVIDED_LLM_*` references. Live preflight checks kind, exact key names and reference prefixes without printing values; both live cases must succeed and scans reject raw key patterns/credential fields in gate-instance variables. Picker UX is not tested. |
| Live hook / hygiene | Both fraud URLs use fraudAgentUrl with the 38090 default; no inline endpoint, key or model inputs. Deterministic engine receives a dummy JSON cluster credential with fake secret references only. |
| L1 | Real LLM, guarded real A2A and synthetic Daytona; cold APPROVE/482.17/CN-DUP-01, TX-0911/TX-0912, PDF-only facts, skill/memory/provenance, revise to second review without another fraud send, accept to COMPLETED. Cold rationale is persisted with run/snapshot/runtime identity. |
| L2 | Warm APPROVE/482.17 with different rationale, exact policy bytes and linked resource version. WireMock A2A pushes authenticated COMPLETED, with no external fraud conversation. Skill and memory evidence use the same strict schemas. Accept to COMPLETED; restore cold policy in finally. |
| E2E | Real-Daytona L1 is the only tier that proves the actual sandbox-computed filing deadline. Requires current policy v1 without changing it. Starts the attested exact process definition key, reuses one sandbox across revise/accept, deletes all owned sandboxes in finally, and certifies only after verified cleanup. |
| Tool provenance | D1 requires reconciliation sourceDocumentId to equal the supplied document ID. Both live cases and e2e require sourceDocumentId, an A2A taskId, and evidence with kind a2a and authScheme bearer. The gate never prints agentUrl. |
| #113 remote instance | L1 additionally requires nonblank remoteProcessInstanceKey and remoteOperateUrl from the real A2A tool result. Only the URL's host:port is logged, never its path or query. L2 does not require real remote-instance metadata. |
| Modeler parity (a) templates resolve | Every `zeebe:modelerTemplate` id@version in the snapshot resolves in `demo/.camunda/element-templates/**` or the pinned Modeler 5.51.1 bundle, passes Modeler's cloud template validator and is engine-compatible with the BPMN's executionPlatformVersion. Fails if a local template shadows a bundled id@version (panel and lint would apply different templates) or if an ancestor `.camunda/element-templates` folder exists that the gate does not snapshot. |
| Modeler parity (b) lint clean | `@camunda/linting` 3.54.1 wired as Modeler 5.51.1 does (`modeler: desktop`, `type: cloud`, element-templates plugin over the templates from (a), the BPMN's executionPlatformVersion) reports 0 errors. Warnings are printed and do not fail. |
| Modeler parity (c) #75 | DisputeAgent has taskHeader `instructionSource=resource`, a `systemPrompt` linked resource of type `system-prompt` with a resourceId, and no inline `data.systemPrompt.prompt` input. The applied template carries every instruction-source/linked-prompt binding of the pinned #75 prototype with the same type, choices and conditions. |
| Modeler parity (d) #83 | Finds provider capabilities by binding name, independent of group id (including `provider-capabilities`). Web search binds to `provider.openai.api.completions.serverTools`, defaults to Off and documents EU/403/Off; PDF parsing binds to `provider.openai.backend.custom.bodyProperties` and offers file-parser/native. |
| Modeler parity (e) #86 | The applied template has a non-hidden property bound to `provider.openai.backend.custom.openAiCustomCredential`, not only a raw input in the XML. |
| Coverage | Full deterministic CPT union must cover every flow node and sequence flow. D1 is the broad segment, D4 supplies Fraud_TimeoutEnd, and sandbox failures supply the bare boundary. D2 is a tone-demo override; all six sandbox cases are explicit clean/stale/success diagnostic-isolation overrides, not six independent topology paths. Dedicated six-case reports alone cover only 6/36 IDs (16.67%), never full certification. |

The filing deadline is **not** a Reconcile_Compute output in the source model. The deterministic
tier does not assert its mocked sandbox value. Default live checks only stub-derived deadline
evidence; `--e2e` checks the actual deadline in real sandbox evidence.
Likewise deterministic tests never present mock-returned values as verified calculations.

The real-proof collector joins reconciliation's sourceDocumentId to the uploaded CSV identity.
Skill imports separately join their document IDs to original downloaded uploaded bytes.
`skill_history.py` requires actual engine scopes, completed timestamps and matching TOOL_RESULT
IDs/arguments/content before producing normalized `skillEvidence`; prose cannot substitute.
Inputs on an ad-hoc activation body are joined to its concrete task through the engine's
immediate-child query, with unambiguous call ID/name and matching scoped results. Scope equality,
numeric key arithmetic and timestamps alone cannot establish that join.
The accepted execution protocol is either one chained prehash/run/posthash call or a completed
separate prehash in the same sandbox followed by a visible run/posthash call. Hashes must match
the original bytes; failed/missing/reordered checks or intervening script writes/uploads fail.
The full JSON stdout must report filing-window-only eligibility, reason code, explicit filing
date 2026-10-20 and deadline 2027-01-13, with matching pre/script/post hashes.
The final prefixed citation must contain exactly one complete JSON object equal to actual stdout:
keys, values and JSON types must match, with no added, dropped or duplicate fields. Only key order
and insignificant whitespace may differ. Raw stdout is separately hashed; original script bytes
and both actual pre/post checks remain mandatory.

The live gate does **not** observe the provider's composed system prompt. Ledger injection into
that prompt remains runtime unit-level evidence, not a claim made from CONFIGURATION base prompt
history. It also makes no measured token-savings, runtime idempotency or general-redaction claim.
Memory URLs are GET-only, no-store, local-origin allowlisted and obtained from owned context/history;
there is no snapshot enumeration. Restart-invalidated URLs fail closed.
Archived native `c8__get_document` results are matched to committed TOOL_RESULT history by call ID,
because native tools do not produce BPMN task-local result variables. BPMN tool results retain their
scoped-variable checks. The A2A stub uses the real agent's `RISK: LOW` response format so the runtime
ledger must extract the same verdict; a missing verdict still fails the gate.

Round 3 offline preparation uses `src/test/resources/round3/offline-demo`, not the historical
deployed snapshot. Run without source synchronization or cluster calls:

```sh
mvn -o --batch-mode --no-transfer-progress test \
  -Dgate.demo=src/test/resources/round3/offline-demo \
  -Dgate.reports=target/surefire-round3-offline \
  -Dcamunda.process-test.coverage.report-directory=target/round3-offline-coverage
python3 -B -m unittest -q test_gate_report test_daytona_proof test_skill_proof test_skill_history test_tone_proof
```

At least 15 GiB free is required before engine runs. These isolated results are not deployed
certification. Re-snapshot final integrated bytes at handoff before the authorized full run.
V19 archives are historical and cannot certify Round 3. Synthetic protocol responses and the localhost
Daytona stub do not execute remote Python or allocate a sandbox. The separate local serialization
fidelity test is not real-Daytona proof.

Historical pre-skill captures are diagnostic only and lack the Round 3 skill/history contract;
they cannot substitute for a passing `--e2e`.

## Modeler parity

The parity group answers "what does Camunda Desktop Modeler 5.51.1 show for this file?" with the
same libraries Modeler uses, so the gate and the Problems panel cannot disagree. It runs after the
Maven deterministic tier in full and `--deterministic-only` runs, writes
`target/surefire-deterministic/TEST-modeler.ModelerParity.xml`, `target/modeler-parity.json` and
`target/modeler-parity.log`, and prints the lint output. A crash leaves no report and counts as
`FAIL (not executed)`. Every sub-check is strict and blocks live eligibility. A missing applied DisputeAgent template fails
(c)-(e) with `applied template not found`.

Pinned inputs and their provenance are recorded in `modeler-parity/SOURCE.json`:

- Bundled templates: `modeler-parity/bundle/camunda-modeler-5.51.1/connector-templates.json`, the
  698 OOTB connector templates the installed Modeler 5.51.1 fetched with its template updater. The
  packaged app ships no templates on disk, and the git tag's `resources/element-templates` holds
  development samples only.
- Lint libraries: `@camunda/linting` 3.54.1, bpmnlint 11.12.1, bpmnlint-plugin-camunda-compat
  2.59.2, bpmn-js-element-templates 2.33.0, pinned via `package.json` overrides to the
  camunda-modeler v5.51.1 `package-lock.json` closure.
- #75 shape: `modeler-parity/reference/linked-prompts-agent.element-template.json`, the
  linked-prompts prototype template.

`npm test` in `modeler-parity/` (run by the gate) checks the checker: the fixed fixture passes
every sub-check and the v14 fixture fails each one with the Modeler findings, plus missing,
shadowed, invalid and mis-bound template cases. `fixtures/make_fixtures.py` regenerates the
fixtures from read-only `modeler/` and `source-cluster/` inputs; the fixed fixture is a test oracle,
not a proposed model.

The live regional probe never reads credential files or prints provider errors. It stores only
the classification and its own process identity in `target/web-search-probe.json`, and cancels
its instance if still active. N/A requires both that evidence and a passing live probe testcase;
a failed or missing testcase cannot be overridden by a marker file. The probe runs once in the
default full live tier; stub-only L2 retries reuse that run's probe evidence. No probe or live
request runs in `--deterministic-only`. Provider parsing evidence is checked by its JSON fields,
not by Operate's display labels ("Provider capabilities" or the earlier server-tools label).
Both live drafts must start their evidence with `Policy v1 limit:` or `Policy v2 limit:`.

## Persistent real-Daytona proof

`proofs/last-real-daytona.json`, `proofs/real-daytona-attempt.json` and
`proofs/artifacts/<instance>-stdout.txt` survive `target/` resets and are git-ignored.
The previous successful proof is atomically replaced only after a successful real L1,
credential scan, exact source/deployment/runtime race checks, completed process, and verified
owned sandbox/task cleanup. A pending or failed attempt keeps freshness red, even if the
previous successful proof otherwise matches. A successful retry clears that state.

Proof identity includes the exact deployed BPMN bytes (DI included), definition key/version,
REST URL and installation/broker identity, connectors attestation (PID, jar path, SHA256 and
start time), gate source hash and complete snapshot manifest hash. Before starting, the source
and deployed BPMN must match, and the sandbox job must have one attempt (`retries="1"`).
The started instance must use the recorded definition key; source, deployment and runtime are
checked again before proof commit. An identical-byte redeployment can reuse a proof only when
all other identity fields still match; a deployment change during a proof attempt fails.

Only `run/connectors.java.pid` and `run/connectors.attestation.json` are read for runtime
attestation. The gate verifies the PID's Java argv, :8286 listener and bundle digest; it never
reads the launcher, environment or secrets. Missing/stale attestation fails closed.
Real evidence records the sandbox handle, observed 1 CPU/1 GiB RAM/3 GiB disk allocation,
CSV document identity and reconciliation, successful remote Python stdout hash and persistent
artifact, computed deadline, one fraud conversation, completed outcome and cleanup status.
Mocks, fallback, missing stdout, a second CREATE/handle, retries or incomplete cleanup cannot
certify the model. Deletion is label-scoped and attempts every owned sandbox, even if one fails.

`./gate.sh --check-proof` is the shared read-only freshness interface for the presenter's script:
exit 0 means current proof; exit 1 prints the reason and `--e2e` remedy. It contacts the cluster
but starts no process or sandbox. To create a new full proof, call `./gate.sh --e2e`; it uses the
same assertions and prerequisites as above. Every mode shares `proofs/gate.lock`, outside
transient resets. The gate checks **15 GiB free local disk** before every engine/live run.
One real proof budgets one default sandbox and **3 GiB transient Daytona disk quota**.

Offline acceptance tests: `python3 -B -m unittest -q test_gate_report.py test_daytona_proof.py`
and `mvn test -Dtest=WebSearchProbeTest,SandboxCleanupTest`. These do not access the prototype
cluster or Daytona. D3's HTTP mock and data assertions are unchanged; only the removed script's
completed-element ID is omitted after the corresponding source-model change.

## Safety and evidence

Every full/deterministic run copies the demo's BPMN, form, policies, rules, data, scripts, variants and `.camunda` element templates (if present) without editing the
source, records per-file SHA256s in `target/input-sha256.json`, prints both the BPMN digest
and the whole manifest's SHA256, and removes stale reports. Maven logs
and per-tier Surefire XML are under `target/`, with the final table in `target/summary.txt`.
`target/owned-instances.txt` records only keys created by this harness. The human-readable deterministic coverage is
`target/coverage-report/report.html`; live coverage is separate and cannot inflate D1/D2 coverage.

L1 uses an in-JVM WireMock forwarding guard to the **real** fraud agent. It forwards at most
one SendMessage and blocks subsequent sends before they reach the agent; repeated attempts fail
the scenario. Polls are forwarded. The A2A request journal is disabled; only counters and
an authentication-present boolean are retained. The final summary scans every file under
`target/` for unredacted bearer-token patterns without printing matches, and fails on a match.
The credential scanner allows the exact dummy Daytona key only when its sibling apiUrl
matches the current run's stub. E2E requires persisted Daytona apiKey to be absent or the
unresolved secret placeholder.
L2 never contacts the real fraud agent. Both endpoints bind
only to localhost on free ports, so connectors must run on this host (as the source prototype does).

CPT remote mode has data deletion **NONE** and clock resets **disabled**. Cleanup cancels only
keys started by these tests and waits for their review tasks to disappear. It never purges
the shared cluster. Only real-Daytona e2e deploys/uses a gate-owned HTTP connector helper to list Daytona sandboxes by the exact
`processInstanceKey` label, verifies ownership before deleting, and polls until none remain.
Its bearer placeholder is resolved only by the connector; the harness sees only sandbox IDs,
owner labels and deletion status. Cleanup failures fail the scenario, even after a successful
decision. `target/sandbox-cleanup.txt` records verified deletion.

The separately invoked `HistoricalCleanupTest` deletes only the three explicitly authorized
historical gate owners (`2251799813691161`, `2251799813691492`, `2251799813691862`); it is excluded
from normal gate runs. Runs are serialized with `target/gate.lock`; stop other policy deployments
during a gate run because `latest` policy binding is shared state. Each live case can take several
minutes; do not repeatedly run it while debugging deterministic checks.
