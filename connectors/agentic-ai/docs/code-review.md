---
applyTo: "connectors/agentic-ai/**,connectors-e2e-test/connectors-e2e-test-agentic-ai/**"
excludeAgent: "coding-agent"
---

# Agentic AI code review

This is how to review a change in `connectors/agentic-ai` and
`connectors-e2e-test/connectors-e2e-test-agentic-ai`. It applies to human reviewers, Copilot and the
AI PR review bot. Find problems. Do not fix them.

Report a finding only when you can point to the code. Prefer a few findings with evidence over many
guesses. Report only on code that the diff adds or changes. There are two exceptions:

- A consolidation finding (step 5).
- Code outside the diff that the change made wrong, or that the rules require the change to update.
  Examples: a missed location (step 4), Javadoc or a document that names a changed method, an SPI
  method that is no longer called, a rule that conflicts with the change. Cite the unchanged line.

## Rules to read

These documents are the only rules. Do not invent rules. Read the parts that apply to the change.

1. [`implementation-rules.md`](implementation-rules.md): all sections.
2. [`design-guidelines.md`](design-guidelines.md)
3. [`target-architecture.md`](target-architecture.md)
4. [`AGENTS.md`](../AGENTS.md): all sections. Start with Gotchas, Architectural invariants, Null
   safety, Definition of Done and Extension points.
5. The repo-root [`AGENTS.md`](../../../AGENTS.md), where the module docs do not cover a topic.
6. The e2e [`AGENTS.md`](../../../connectors-e2e-test/connectors-e2e-test-agentic-ai/AGENTS.md), when
   the change touches e2e tests.
7. The `README.md` and `docs/` of each module that the change touches.

## Steps

1. **Reuse.** For each new type, method, helper, constant or converter, search for existing code with
   the same purpose. Search by the domain concept, then in sibling packages. If existing code does
   the job, or does it with a small change, report it with the `file:line` of the existing symbol.
2. **Simplicity.** Find code that the task does not need: a speculative feature, configuration nobody
   asked for, error handling for a value that types or validation already exclude, an abstraction
   with one use, an interface with one implementation that neither an SPI nor the interface
   invariant in `design-guidelines.md` (I3) requires.
3. **Rules.** Check the change against each rule. Name the rule by its heading or its bold label, for
   example `Code design / Tell, don't ask` or `Domain rules / 3`.
4. **Locations.** Check the list in `implementation-rules.md` under "Before you plan": both flavors,
   all element template versions, all conversation stores, all providers, running process instances.
   Report a location that the change must reach but does not.
5. **Consolidation.** When the change adds a copy of existing code, or makes existing code redundant
   or inconsistent, report it as an advisory finding with the risk of keeping it and the risk of
   changing it. A human decides.
6. **Build checks and rules.** When a build check (ArchUnit, ErrorProne, NullAway, Spotless, a docs
   test) can find the problem, name the check. When a rule conflicts with the change, or a build check
   in the change now enforces a rule, name the rule for revision. A maintainer decides the follow-up.
7. **Escalation paths.** When the change adds or moves a file, folder or module that a criterion in
   "Human review required" covers, check it against the path rules in
   `.github/pr-review-agent/agentic-ai.yml` (`*` also matches `/`). If no path rule matches, report an
   advisory finding that names the path to add. Report a rule that a rename or a removal in the change
   leaves with no match. Examples: a class that handles credentials or authentication, outbound proxy
   code, a folder of released element templates, a module with an SPI.

## Human review required

A human reviewer must approve the change when any of these applies. Say so in the review, and name the
number:

1. The change reads, stores, logs or forwards a credential, a token or an API key.
2. The change affects running process instances or released element templates. It changes a stored
   shape, a wire format, a variable name, a template ID, a job type or a released template.
3. The change modifies a type or member of `aiagent-api` or `aiagent-model`, an SPI named in
   `design-guidelines.md` (I4), or anything that `breaking-changes.md` lists as a break.
4. The change misses a location from step 4.
5. The change weakens or removes a rule in the module docs or a build check. Examples: a new entry in
   an ArchUnit allowlist, an accepted break in `api-changes.json`, a deleted architecture test, a new
   `@NullUnmarked` or `@SuppressWarnings`.
6. The change adds or upgrades a dependency, changes build configuration, changes outbound proxy
   code, or edits the module docs or `AGENTS.md`.

A review that finds none of these does not need a human because of this section. It can still need one
for other reasons, such as a blocking finding or low confidence.
