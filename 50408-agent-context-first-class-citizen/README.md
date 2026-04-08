# Agent Context as a First-Class Citizen — Research

This folder contains the research and proposal artifacts for making agent execution context a first-class concept in the Camunda platform.

---

## 📌 Start Here: Final Proposal

**If you want the consolidated recommendation, read this:**

👉 **[Final Proposal: Agent Context as a First-Class Citizen](final-proposal/README.md)**

The final proposal is self-contained and presents the recommended architecture for:
- `AGENT_CONTEXT` — committed agent state as a Zeebe record type
- `AGENT_TRAIL_EVENT` — execution trail published independently of job completion
- The separation of committed state (via `CompleteJob`) from attempt-level trail data (via a new publication path)
- How the design handles non-happy-path scenarios (retries, stale jobs, event subprocess supersession)
- Phased implementation plan

The final proposal was built by consolidating all exploratory research below, using the corrected architecture from the latest follow-up as the authoritative basis.

---

## Exploratory Research Documents

The following documents record the research journey that led to the final proposal. They remain useful as background reading for understanding the reasoning process and alternatives considered, but the final proposal supersedes them wherever there are differences.

### 1. `agent-context-research.md`
**Foundational research.** Establishes the two-concept model:
- `AgentContext` for committed execution state
- `AgentTrailEvent` for execution trail / observability

Analyzes why a single concept is insufficient and explores scope, lifecycle, and data flow options.

### 2. `tool-call-results-research.md`
**Tool call / tool result representation.** Investigates how tool calls and their results should be represented in the proposed architecture:
- What belongs in `AgentContext` (current/pending tool activity)
- What belongs in `AgentTrailEvent` (invocation/result history)
- How to handle different tool types (ad-hoc sub-process, MCP, A2A)

### 3. `corrected-architecture-followup.md`
**Architecture correction.** Corrects the earlier proposal's approach of sending all data through `CompleteJob`:
- Introduces the separate attempt-level trail publication path
- Explains why trail data must be independent of job completion
- Addresses non-happy-path scenarios that the completion-only model cannot handle

> ⚠️ **Important:** Where the earlier documents suggest a completion-only design, the corrected follow-up takes precedence. The final proposal reflects this correction.

---

## Document Lineage

```
agent-context-research.md          (foundational concepts)
        │
        ▼
tool-call-results-research.md      (tool call representation details)
        │
        ▼
corrected-architecture-followup.md (non-happy-path corrections)
        │
        ▼
final-proposal/README.md           (consolidated, authoritative proposal) ← START HERE
```
