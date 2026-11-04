# Adopt a target architecture for the agentic AI module

* Deciders: Agentic AI Team
* Date: Nov 4, 2026

## Status

**Accepted**.

## Context and Problem Statement

Coding agents write a large part of the changes in the agentic AI module. The changes arrive fast,
and each change solves a local problem. Without a shared end state, each change makes its own
structural choices. Policies get more than one owner, interfaces appear without a second adapter,
and the core reads binding records that the boundary must translate. The architecture erodes.

The reference docs describe the current state of the module. They do not say where the structure
must go. An agent that plans a structural change has no written target to compare its plan with.

The issues under #9532 move the existing code to a better structure. They need a shared end state
to agree with each other and to set their order.

Where does the team write the target state of the module, and how does an agent find it?

## Decision Drivers

* **Erosion by fast change**: agent-authored changes arrive faster than review can keep the
  structure consistent. A written target gives each change and each review the same reference.
* **Target before the plan**: an agent must read the target before it plans a structural change,
  not after the review finds a problem.
* **Shared end state for #9532**: the issues in the #9532 graph need one end state that they all
  move to.

## Considered Options

1. A living target architecture document, linked from the module `AGENTS.md` and recorded by this
   ADR.
2. A target section inside `docs/reference/ai-agent.md`.
3. No written target. The issues describe the target one by one.

## Decision Outcome

Chosen option: **Option 1, a living target architecture document**, because it gives agents and
reviewers one place to read the target before they plan, and it keeps the reference docs about the
current state.

The outcome is [`target-architecture.md`](../target-architecture.md). The module `AGENTS.md` links
to it from the instructions for structural changes and from the architectural invariants.

The document changes without a new ADR when the team adds, removes or makes a trait more precise.
A change of direction needs a new ADR that supersedes this one.

### Positive Consequences

* Agents read the target before they plan a structural change.
* Reviews compare a change with a written target, not with the memory of a reviewer.
* The issues under #9532 share one end state.
* The reference docs stay about the current state.

### Negative Consequences

* The document can become out of date. The team updates it when a migration issue closes or a trait
  changes.
* The target and the current state differ until the migration is complete. The document must say
  which state it describes.

## Pros and Cons of the Options

### Option 1: A living target architecture document (chosen)

* Good, because it is one document that agents and reviewers can find from `AGENTS.md`.
* Good, because it can change as the team learns, without a new ADR for each change.
* Bad, because it is one more document to keep up to date.

### Option 2: A target section inside `docs/reference/ai-agent.md`

* Good, because it adds no new document.
* Bad, because the reference docs describe the current state. A target section mixes the current
  state and the target state in one document.

### Option 3: No written target

* Good, because it needs no document.
* Bad, because agents plan without a target, and the architecture continues to erode.
* Bad, because each issue states its own part of the target, and the parts can disagree.
