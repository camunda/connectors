# Architecture Decision Records

This directory records significant architectural decisions made for the Camunda Connectors project.

## What is an ADR?

An Architecture Decision Record (ADR) captures a single significant decision: the context that drove it, what was decided, and the consequences. ADRs are lightweight — a few hundred words — and live in the repo so they evolve alongside the code.

## When to write one

Write an ADR when a decision:
- Affects multiple modules or teams
- Involves a meaningful trade-off between alternatives
- Would be re-litigated without a recorded rationale
- Changes an established pattern in the connector SDK, runtime, or out-of-box connectors

You do **not** need an ADR for implementation details, bug fixes, or decisions that follow an existing pattern without deviation.

## File naming

```
ADR-NNNN-short-kebab-title.md
```

- `NNNN` is a zero-padded sequence number (find the highest existing number and increment).
- The title slug is lowercase, hyphen-separated, descriptive but concise.

Examples: `ADR-0002-connector-discovery-via-serviceloader.md`, `ADR-0003-feel-annotation-for-dynamic-properties.md`

## Statuses

| Status | Meaning |
|--------|---------|
| `Proposed` | Open for discussion; not yet merged to `main` |
| `Accepted` | Merged; the decision is in effect |
| `Deprecated` | No longer recommended but not yet replaced |
| `Superseded by ADR-XXXX` | Replaced by a later decision |

## Template

```markdown
# ADR-NNNN: Title

## Status
Proposed

## Context
<!-- What situation, constraint, or problem prompted this decision? -->

## Decision
<!-- What was decided? Be specific. -->

## Consequences

### Positive
<!-- Benefits that result from this decision -->

### Negative
<!-- Costs, risks, or limitations introduced -->
```

## Process

1. Copy the template, fill out all sections, open a PR targeting `main`.
2. Discuss in the PR; update the ADR status to `Accepted` before merging.
3. If a later decision supersedes this one, update the original ADR's status to `Superseded by ADR-XXXX` and open a new ADR.

## Agent instructions

When asked to create an ADR:

1. Check existing files in this directory to find the next sequence number.
2. Copy the template above into a new file named `ADR-NNNN-<kebab-title>.md`.
3. Fill out all four sections (Status, Context, Decision, Consequences) based on the information provided.
4. Set Status to `Proposed` unless told otherwise.
5. Add a one-line entry to the index below.
6. Do not invent consequences or context that wasn't given — ask if anything is unclear.

## Index

| ADR | Title | Status |
|-----|-------|--------|
| [ADR-0001](ADR-0001-annotation-driven-element-template-generation.md) | Annotation-Driven Element Template Generation | Accepted |
| [ADR-0002](ADR-0002-annotation-based-operations-for-outbound-connectors.md) | Annotation-Based Operations for Outbound Connectors | Accepted |
| [ADR-0003](ADR-0003-element-scoped-inbound-connector-properties.md) | Element-Scoped Properties for Inbound Connectors | Accepted |
| [ADR-0004](ADR-0004-configuration-templates-in-element-templates.md) | Configuration Templates in Element Templates | Proposed |
| [ADR-0005](ADR-0005-storage-connector-flexibility.md) | Storage Connector Flexibility — Two Paths for Document Handling | Proposed |
