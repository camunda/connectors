# Card dispute demo: presenter runbook (v24)

For the customer session on Tuesday 20 Oct 2026. The goal is real design feedback on the 8.11 agent epics. All data is synthetic.

This runbook matches **CardDispute v24** (definition 2251799813777504, cold policy resource 79) on the prototype cluster. The v24 reference run is 2251799813777523. It is COMPLETED after a guarded `complete-review` accept. The agent history is unchanged, with one A2A call and one sandbox. The sandbox is deleted. Tone drafts come from the v22 runs 2251799813767864 (COMPLETED) and 2251799813768614. Policy v1 (concise, technical tone) is the default.

| What                 | Where                                                       |
| -------------------- | ----------------------------------------------------------- |
| Operate and Tasklist | http://localhost:8280 (login `demo` / `demo`)               |
| Connectors runtime   | localhost:8286                                              |
| Fraud agent (A2A)    | http://localhost:38090, remote Operate on localhost:38080   |
| Backup stack         | alpha4 on http://localhost:8180                             |
| c8ctl profile        | `local-proto`                                               |
| Working directory    | `card-dispute-proto/source-cluster/demo`                    |

Do not use c8run on :8080 for this demo. It has no saved credential and no 8.11 Operate cards.

## The story in one paragraph

Jordan Avery says that Northwind Electronics charged card 4417 twice for one $482.17 headphones order. The dispute is DSP-2026-10-0042. An AI agent reads the PDF statement and the CSV export. It reconciles TX-0911 and TX-0912. It reads the packaged skill `chargeback-deadline`, configured on the Daytona sandbox, and runs its script unchanged to get the filing deadline. It asks an external fraud agent over A2A. Then it drafts a decision under a governed compliance policy. An analyst reviews the draft in Tasklist and accepts it or sends it back. The case is APPROVE under both tone versions of the policy. Only the voice of the draft changes.

## Timings measured on v22 and v24

| Step                           | Typical    | Worst seen | Source                                  |
| ------------------------------ | ---------- | ---------- | --------------------------------------- |
| Start to review task           | 38 to 50 s | 2 min 30 s | warm 768614 and cold 767864             |
| Fraud agent answer (A2A push)  | 4 to 15 s  | 2 min      | `Fraud_Race`                            |
| Sandbox skill run (Daytona)    | under 20 s | n/a        | skill reads at 15:43:38, script at 15:43:45 in run 767864 |
| Revision after "Send back"     | 7 s        | n/a        | cold run 767864                         |

The worst case is a modeled bound, not a measurement. The fraud agent has 2 minutes to answer. After that, the timer "No answer in 2 min" ends the tool call without an answer. On v15, a polled fraud call stalled for 17 minutes. Push delivery and the timer, live since v17, remove that risk. Always keep pre-staged cases (see T-60).

## T-60 checklist

Run every command from `card-dispute-proto/source-cluster/demo` unless noted.

1. Cluster up: `../cluster.sh status`. If it is down, run `../cluster.sh start` and wait for http://localhost:8280 to load.
2. Fraud agent up: `docker compose -p camunda-a2a-e113 start`. Then check `curl -s http://localhost:38090/.well-known/agent-card.json` returns "Fictional Bank Fraud Risk Agent".
3. Saved credential: it already exists on :8280 as `cardDisputeLlm` (GLOBAL, kind CREDENTIAL). Run `scripts/credential.sh` only if the preflight in step 6 fails on the model call.
4. Deploy the demo resources: `scripts/demo.sh deploy`. This deploys the BPMN, the form and the policy. The skill files are not deployed. The start step uploads them as documents.
5. Reset the policy to v1, the cold tone: `scripts/demo.sh policy-v1`.
6. Preflight, read-only: `./rehearse.sh --dry-run`. It must end with "Preflight passed", "Latest compliance policy: v1 (cold)" and "Open review tasks: 0".
7. One real end-to-end check: `scripts/demo.sh e2e-check`. It confirms that the deployed BPMN equals `card-dispute.bpmn` and that policy v1 is latest. It also confirms that the connectors carry the secret names. Then it runs one real case with one Daytona sandbox and one A2A conversation. It must end with "e2e-check PASS". It starts an instance, so run it once only.
8. Check that the custom memory store answers. Open the store URL of a recent run. It is in `source-cluster/logs/reference-<pi>/report.json`. The store is in memory, local to the connectors runtime, and without authentication. A connectors restart wipes it. Never restart connectors before the session.
9. Pre-stage two backup cases: run `./rehearse.sh` twice without `--accept`. Each stops at an open review task. Write down both instance keys.
10. Modeler (#86): open `card-dispute.bpmn` in Camunda Modeler 5.51. Set the tab's deployment target to **Camunda 8 Self-Managed**, REST `http://localhost:8280`. Select **Dispute agent**. The model credential chooser must show "Card dispute LLM" without a warning. If it says "Configuration not found", the tab is still connected to :8080. Steps: `card-dispute-proto/modeler/round2/86/README.md`.
11. Browser tabs, in this order: Modeler, Operate (process CardDispute v24), Tasklist (All open tasks), the backup instance in Operate, remote Operate at http://localhost:38080.
12. Close Slack and mail notifications. Set the browser zoom to 110 percent.

## Reset commands

```bash
scripts/demo.sh policy-v1          # policy back to v1 (cold tone)
./rehearse.sh --dry-run            # confirm a clean state
./rehearse.sh                      # new case, stops at the review task
./rehearse.sh --summary <pi>       # print the draft of an instance
```

Cancel leftover demo instances in Operate: select the instance, then **Cancel**.

### Complete an existing review safely

Use the validated helper, not a hand-written REST completion command:

```bash
scripts/demo.sh complete-review <taskKey> '{"analystVerdict":"accept"}'
scripts/demo.sh complete-review <taskKey> '{"analystVerdict":"revise","analystFeedback":"Quote the statement ID and fraud confidence."}'
```

The helper checks the variables before any API call. `analystVerdict` must be exactly `accept` or `revise`. Unknown keys, including `analystAction`, are rejected. Accept permits no additional fields. Revise also requires nonempty `analystFeedback`. The task must be an open `ReviewDraftedDecision`. `rehearse.sh --accept` and `--revise` use the same helper. Do not bypass it. A missing verdict follows the model's default re-entry path.

## Ten-minute flow

Each beat lists the epic, the clicks, and what to say. Keep the customer talking. Their reaction is the deliverable.

### 0:00 to 1:00: the problem

- **Show:** Operate, CardDispute v24 diagram.
- **Say:** "A dispute analyst spends most of the time collecting facts. Here an agent collects them and drafts the decision. The analyst stays in charge of the verdict."
- **Point at:** Dispute agent, the four tools inside it, and Review drafted decision.

### 1:00 to 2:30: governed instructions (#75)

- **Modeler:** select Dispute agent. Open **System prompt**. Show the linked resource `dispute-compliance-policy.md` with binding **latest**.
- **Operate:** open a completed instance. Select **Dispute agent**, then **Details**. Show the **System prompt** card with the tag **Linked resource**: Prompt ID, Version, Binding, Resource key.
- **Say:** "Compliance owns this policy as a Markdown file. The process links it. The engine pins the exact version on each run, so audit can prove which policy decided this case."

### 2:30 to 4:00: start a case live, documents in (#81, #83)

- **Terminal:** `./rehearse.sh`. It uploads five documents: the PDF statement, the CSV export, and the three skill files. Then it starts the case.
- **Operate:** open the new instance. Select the annotation on Dispute agent: "documents in, passed to tools by doc tag". Open **Reconcile transactions (document in)**, then **Read export document**. Show the variable `reconcileResult`: `sourceDocument` `transactions-2026-09.csv`, `rowsRead` 18, `matchCount` 2, `overchargeAmount` 482.17.
- **Modeler:** select Dispute agent. Open the group **Provider capabilities**. **PDF parsing (file-parser plugin)** is set to **Native (EU-safe)**.
- **Operate, agent Details:** show the **Provider capabilities** card. Its **Configured** row reads "file-parser (plugin, engine native)". Below it, show the **PDF parsing** card with the tag **file-parser plugin, provider-run**: Engine native, Configured via `bodyProperties.plugins`, Documents `statement-2026-09.pdf`.
- **Show the proof:** open the rationale of the draft. It quotes the statement ID from the PDF.
- **Say:** "The model provider parses the PDF. We did not write a parser. The rationale quotes the statement, so you can see it really read the file."
- **Say also:** "Provider capabilities are settings on the agent, not BPMN tasks. You switch them on per agent and see in Operate which ones ran."
- **Say also (#81):** "The agent passes the document to the tool by its tag. The tool reads the stored file itself."
- **Tasklist, later:** **Source documents** lists the PDF and the CSV. It can also list the skill files.

### 4:00 to 5:30: sandbox and skills (#82)

- **Modeler:** show the **Sandbox unavailable** error boundary event on the sandbox call. It is the only error handling. Nothing else changed in the diagram.
- **Modeler (manual):** select the sandbox task. Open **Sandbox Configuration**. The **Skills** field holds the ZIP `chargeback-deadline.zip`. Source: `modeler/round3/82-skills/CONFIG-SKILL.md`.
- **Operate, Conversation history:** the agent reads `SKILL.md` and `network-rules.md` from `/home/daytona/.agents/skills/chargeback-deadline/`. Then it runs `scripts/deadline.py` unchanged. The platform extracted the ZIP when it created the sandbox.
- **Operate, Read document:** in the user message, open the **Documents** row. Click the eye on `SKILL.md`. It shows formatted Markdown. Click the eye on `deadline.py`. It shows plain text.
- **Show that the script is not generated:** the agent checks the SHA-256 before and after the run. The hash is `816444bd…8416` in both checks.
- **Operate, Sandbox card:** the green tag **Daytona**, the command, Exit code 0. The script output is JSON with `filingDeadline` 2027-01-13.
- **Say:** "A skill is a package of instructions, a reference and a script. You add it by configuration. The agent reads it first, then runs the script as written. It does not rewrite the code."
- **Do not claim:** that this is a finished skills feature. Say it is a prototype of the Agent Skills format (agentskills.io) on the existing sandbox.
- **Run it once with care:** the sandbox call takes under 20 s. Keep talking about the diagram while it runs.

### 5:00 to 6:00: external fraud agent (#113)

- **Operate, Conversation history:** open the AskFraudRiskAgent result. Show the **Remote A2A agent** card with the card: Agent URL, Auth scheme, Task state, Remote process instance. The card has no chip and no caveat text. There is no **Polls** row, because the answer arrives by push.
- **Expected answer:** risk LOW, confidence 0.95. The fraud agent is a2a-agent process v5. Its agent card says 1.0.0. It applies ordered rules: a compromise signal gives HIGH, a confirmed duplicate gives LOW, anything else gives MEDIUM. This case is a confirmed duplicate.
- **Click:** the **Remote process instance** link. It opens the fraud agent's own Operate on :38080.
- **Say:** "The fraud team runs its own agent. We call it over A2A 1.0 with its own authentication. Neither side shares its internals. Operate links both instances."
- **Diagram (v24):** open **Ask fraud risk agent**. Show **Send message to fraud agent**, then the gateway **Answer or timeout**. **Fraud agent answered** catches the pushed reply. **No answer in 2 min** is the timer.
- **Say also:** "Camunda does not poll. The fraud agent pushes its answer back, and the process waits at most 2 minutes."

### 6:00 to 7:00: saved credential (#86)

- **Modeler:** Dispute agent, model credential chooser shows "Card dispute LLM".
- **Operate, agent Details:** show the **Model credential** card with the green tag **saved credential**: Credential binding `=camunda.vars.env.cardDisputeLlm`, Secret field "apiKey".
- **Say:** "The developer picks a named credential. The platform team owns the key and the model. Rotation does not touch the process."
- **Say also:** "Modeler checks the credential on the cluster it is connected to. That is why this tab connects to the demo cluster."
- **If the chooser shows "Configuration not found":** the tab is connected to the wrong cluster. Fix the deployment target as in T-60 step 10.

### 7:00 to 8:30: the analyst review, revise, then accept

- **Tasklist:** open **Review drafted decision**. Show the case header, decision APPROVE, refund $482.17 and reason code CN-DUP-01.
- **Refund:** the refund is `overchargeAmount` $482.17. `refundTotal` $34.99 is the unrelated TX-0914 refund already posted, and the evidence says so. If asked, say "The agent separated the earlier accessory refund from the duplicate charge."
- **Show:** the evidence list, **Source documents**, and the **Checks** group (fraud opinion, deadline 2027-01-13).
- **Click:** **Send back to the agent with feedback**. Type: "Quote the statement ID and the fraud agent's confidence in the rationale." Click **Complete task**.
- **Say:** "The feedback goes back into the agent's memory. It revises, it does not restart."
- **Wait 5 to 15 s.** Open the new review task. Show the revised rationale. Click **Accept the draft**, then **Complete task**.
- **Operate:** show the two Dispute agent runs in Instance History and the final end event.

### 8:30 to 9:30: the tone moment and the memory frame (#75, #81)

Source: `card-dispute-proto/modeler/round3/`. Both drafts come from stored reference runs. Do not start a new run for this beat.

- **Show the diff:** `diff policies/dispute-compliance-policy.md policies/v2/dispute-compliance-policy.md`. One line changes: "cold and professional" becomes "warm and friendly". The $500 limit is the same in both.
- **Operate, cold run 2251799813767864:** show the draft rationale. It opens "ReconcileTransactions confirmed matchCount=2 for Northwind Electronics at $482.17..." and ends with the LOW fraud result.
- **Operate, warm run 2251799813768614:** show the initial draft. It reads "Reconciliation confirmed two identical $482.17 charges ... consistent with Jordan's single-order claim." It cites the 0.95 confidence and the 120-day window.
- **Say:** "Compliance changed one sentence in a Markdown file. The decision is the same, APPROVE $482.17. The voice is different. You judge which one you would send to a customer."
- **Do not claim:** that the tones differ more than the two texts show. Both texts are factual.
- **Operate, System prompt card:** the linked resource version differs between the two runs. The warm run used resource 66. Ignore the first line of `logs/run-v22-warm.log`. It mislabels the policy as cold because of a stale presenter index.
- **Memory frame:** open the cold run memory URL from `logs/reference-2251799813767864/report.json`. The strategy reads "observation-masking". It holds nine tool results. The older seven are masked by pointer. The latest two are full. The ledger pins TX-0911 and TX-0912, overcharge 482.17, fraud LOW and deadline 2027-01-13.
- **Say:** "Old tool output is masked, but a small ledger keeps the facts. The agent keeps its context and the cost stays flat."
- **Limit:** the store is local, unauthenticated and volatile. The saved copy is `storyboard/sources/evidence/memory-cold.json`. The originals remain in Operate.
- **Reset:** `scripts/demo.sh policy-v1` after any warm run.

### 9:30 to 10:00: extensions (#114) and close

- **Slide only.** "Everything you saw is configuration. Where would you plug in your own code?"
- Move into the feedback questions.

## Recovery

### R1: fraud agent down or slow

- **Signal:** AskFraudRiskAgent stays active longer than 30 s, or the agent card check fails.
- **Do:** switch to a pre-staged case from T-60 step 9. Restart the stack later with `docker compose -p camunda-a2a-e113 start`.
- **After 2 minutes:** the timer ends the tool call with state TIMEOUT and "The fraud agent did not answer within 2 minutes." The agent drafts without a fraud opinion. Expect ESCALATE, but check the draft. Say: "When a remote agent fails, the case escalates to a person. It does not guess." Ask fraud question 4.

### R2: Daytona down

- **Signal:** the sandbox call fails and the **Sandbox unavailable** boundary event fires.
- **Do:** open a pre-staged case. The process has no fallback calculation by design.
- **Say:** "The process catches the error at a boundary event. A sandbox error ends the attempt. The agent does not guess dates."

### R3: model slow or wrong

- **Slow:** if there is no review task after 60 s, open a pre-staged case. Keep talking about the diagram.
- **Wrong decision under v1:** use it. Send it back with feedback that cites the rule. That is the revise beat.
- **Incident in Operate:** do not retry live. Open the backup case and continue.
- **Cluster gone:** use the backup stack on http://localhost:8180.

## Design-feedback questions

Ask open questions. Ask for an example from their own work. Write down exact quotes with the speaker's role.

### Review form and human in the loop

1. What do your analysts look at first when a case opens? Is it here, in the right order?
2. What would make you trust this draft enough to accept it? What would make you reject it at a glance?
3. When you send a draft back, what do you typically write? Free text, a checklist, or a reason code?
4. Who must see the agent's evidence after the decision? Auditors, the cardholder, or a second analyst?
5. Should the analyst be able to edit the refund amount or the reason code directly?

### Governed external instructions (#75)

1. Who writes and approves instructions like this compliance policy today? Which tool do they use?
2. What evidence do auditors need that a specific case used a specific policy version?
3. When a policy changes, must cases in flight keep the old version or switch to the new one?
4. Where do your prompts and policies live today? A repository, a registry, or a document system?
5. What does a policy rollback look like for you?
6. Would you need approval before a new version reaches production agents?

### Document flow (#81)

1. Which documents does an analyst open for a dispute? Statements, receipts, chat logs, or images?
2. Should the agent attach documents to its draft, for example a marked-up statement?
3. Which documents may the agent read, and which must it never see?
4. Where must documents end up after the case? A case file, an archive, or nowhere?
5. How large and how many files does a typical case have?

### AI Agent sandbox (#82)

1. Which calculations must be exact and repeatable? Totals, deadlines, interest, or fees?
2. Would you accept agent-written code if the code and its output are visible in Operate?
3. What would security need to approve code that an agent writes and runs?
4. Should the analyst see the code the agent ran, or only the result?
5. When the sandbox is unavailable, is a marked fallback acceptable, or must the case stop?

### Provider capabilities: PDF parsing and server tools (#83)

1. Is it acceptable that the model provider parses your PDFs? Which documents would be off limits?
2. Which external sources would the agent fetch? Card network rules, merchant data, or regulators?
3. Is it acceptable for the model provider to fetch the web on your behalf?
4. Do regional endpoints limit you, like the EU endpoint here? Which features would you give up for residency?
5. How do you prove later what a fetched page said at decision time?

### Saved credentials and template management (#86)

1. Who in your organization chooses the model and owns the API keys?
2. How many model providers and accounts do you use today? How often do they change?
3. Should a process developer ever see a provider setting, or only a named credential?
4. How do you rotate keys today, and what breaks when you do?
5. Do different teams or environments need different models for the same agent?

### A2A 1.0 client (#113)

1. Which agents outside Camunda would you call? Fraud, KYC, or a vendor's agent?
2. How do those agents authenticate? OAuth client credentials, mTLS, or API keys?
3. How long can a remote agent take? Seconds, minutes, or hours?
4. When a remote agent fails, should the case escalate, retry, or wait?
5. What must you log about each agent-to-agent call for audit?

### Pro-code extensions (#114)

1. Have you forked or wrapped a connector template to add something? What was it?
2. Which part would you replace first? The prompt source, memory, the model call, or guardrails?
3. Who would own that extension code, and how would they test it?
4. Would you rather configure a policy source in Modeler or write Java for it?
5. How do you upgrade today when Camunda ships a new template version?

## After the session

1. Write the quotes into the epic issues within 24 hours. Link the session notes.
2. Reset the policy with `scripts/demo.sh policy-v1`.
3. Cancel open demo instances in Operate.
