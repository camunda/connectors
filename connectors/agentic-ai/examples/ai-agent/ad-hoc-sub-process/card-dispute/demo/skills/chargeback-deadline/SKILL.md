---
name: chargeback-deadline
description: Use when a synthetic card dispute needs a filing deadline and filing-window eligibility from charge dates and a network reason code.
---

# Chargeback deadline

This skill supports the unsupported, synthetic card-dispute demo.
It does not implement real card-network rules or authorize refunds.

## 1. Read the rules

Read this file, then [network-rules.md](references/network-rules.md), through `sandbox_fs_read` before running the script.
Use the reason code and evidence conditions from that reference.
Use reconciliation results for matched charge dates, transaction IDs, and amounts.
Do not infer a duplicate from dates alone.

## 2. Ask the sandbox to run the configured script

The Daytona Skills configuration supplies this skill as a ZIP bundle.
Sandbox CREATE materializes its files under `.agents/skills/chargeback-deadline/` within the returned working directory.
Use that configured directory for the instructions, reference, and script.
Find `scripts/deadline.py` within that directory.
Do not import an attached document over the configured script.
Do not copy, write, regenerate, patch, or replace the script.
The sandbox executes the script and returns the results; do not generate or execute code yourself.

For the reference case, use this command from the sandbox working directory.

```bash
python3 .agents/skills/chargeback-deadline/scripts/deadline.py \
  --charge-date 2026-09-14 --charge-date 2026-09-15 \
  --reason-code CN-DUP-01 --filed-on 2026-10-20
```

Use actual reconciled dates and the supplied `disputeFilingDate` for other cases.
Never use the sandbox clock or calculate dates yourself.
The script accepts Python's standard library only.
Do not install packages or use another image.

## 3. Check and cite the result

Require exit code zero.
Read the script's JSON stdout.
For the reference case, require `filingDeadline=2027-01-13` and `eligible=true`.
Eligibility here covers only the filing window.
Apply the reference's evidence conditions and the linked policy separately.

Add an evidence item starting with `Chargeback deadline skill:`.
Cite the complete JSON stdout without changes.
Do not request hash-check commands. Package byte verification happens outside the agent.
Do not claim successful execution when skill delivery, a command, or the sandbox fails.
Stop the investigation and report the missing evidence instead.
If the sandbox returns `error=SANDBOX_FAILED`, report "deadline not verified".
Do not invent a deadline or treat the error as successful script output.
