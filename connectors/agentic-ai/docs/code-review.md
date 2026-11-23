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
8. [`acceptance-testing.md`](acceptance-testing.md): the test level, the e2e decision and the
   real-provider coverage for a provider change. Check the tests of the change against it.

## Steps

1. **Reuse.** For each new type, method, helper, constant or converter, search for existing code with
   the same purpose. Search by the domain concept, then in sibling packages. If existing code does
   the job, or does it with a small change, report it with the `file:line` of the existing symbol.
2. **Simplicity.** Find code that the task does not need: a speculative feature, configuration nobody
   asked for, error handling for a value that types or validation already exclude, an abstraction
   with one use, an interface with one implementation that is neither an SPI nor a seam with two
   or more adapters.
3. **Rules.** Check the change against each rule. Name the rule by its heading or its bold label, for
   example `Code design / Tell, don't ask` or `Domain rules / 3`.
4. **Locations.** Check the list in `implementation-rules.md` under "Before you plan": both flavors,
   all element template versions, all conversation stores, all providers, running process instances.
   Report a location that the change must reach but does not.
5. **Consolidation.** When the change adds a copy that breaks the reuse rule or the one-owner rule,
   report it as ❌. When the change extends or depends on an existing copy, or makes existing code
   redundant or inconsistent, report it as 🔧 or 🙃 with the risk of keeping it and the risk of
   changing it. A human decides.
6. **Build checks and rules.** When a build check (ArchUnit, ErrorProne, NullAway, Spotless, a docs
   test) can find the problem, name the check. When a rule conflicts with the change, or a build check
   in the change now enforces a rule, report a 💭 that names the rule for revision. A maintainer
   decides the follow-up.
7. **Escalation paths.** When the change adds or moves a file, folder or module that a criterion in
   "Human review required" covers, check it against the path rules in
   `.github/pr-review-agent/agentic-ai.yml` (`*` also matches `/`). If no path rule matches, report a
   🔧 or 🙃 that names the path to add. Report a rule that a rename or a removal in the change
   leaves with no match. Examples: a class that handles credentials or authentication, outbound proxy
   code, a folder of released element templates, a module with an SPI.
8. **Generated templates.** `element-templates/*.json`, including `hybrid/`, are generated. A changed
   template JSON needs a matching change to a generator input in the diff. Examples of inputs: an
   `@ElementTemplate` or `@TemplateProperty` annotation, a validation annotation or another field of a
   binding model, a transform script in `bin/`, a file in `element-template-overrides/`, or the generator
   properties in `pom.xml`. Report a JSON change without one. The snapshots in `versioned/` are static,
   so this step does not apply to them.
9. **Decisions.** A change that adds a stored shape, a customer-facing name, a default or a limit needs
   its decision and rationale in the PR description (Definition of Done in `AGENTS.md`). If the
   description lacks it, report a ❓.

## Severity

Start each finding with one code. These are the codes of the AI PR review bot.

- ❌ Blocking. The change breaks a rule, or a failure is reachable, and the code has to change.
- ❓ A possible problem that you cannot confirm. The author must clarify.
- 🔧 Advisory. The finding is worth fixing: the likelihood of the scenario or its effect on end users is
  medium or high.
- 🙃 Nitpick. The likelihood and the effect on end users are both low.
- 💭 A rule discussion or an open design question. Nothing has to change in this PR.

A rule break with low likelihood and low end-user effect, such as wording or placement, is 🙃, not ❌.
A finding that an accepted ADR, or a decision that step 9 asks for in the PR description (a stored
shape, a name, a default or a limit), already covers is 💭: report it, but nothing has to change in
this PR. A declared deviation from a rule is not such a decision.

Rate the likelihood and the end-user severity (`high`, `medium` or `low`) of each ❌, ❓ and 🔧
finding.

## Human review required

A human reviewer must approve the change when any of these applies. Say so in the review, and name the
number:

1. The change reads, stores, logs or forwards a credential, a token or an API key.
2. The change affects running process instances or released element templates. It changes a stored
   shape, a wire format, a variable name, a template ID, a job type, a released template or an
   element template migration recipe.
3. The change weakens or removes a rule in the module docs or a build check. Examples: a new entry in
   an ArchUnit allowlist, a deleted architecture test, a new `@NullUnmarked` or `@SuppressWarnings`.
4. The change adds a dependency.
5. The PR description declares a deviation from a rule. The declaration never removes a finding.

The path rules in `.github/pr-review-agent/agentic-ai.yml` also require a human, whatever the review
finds. They cover released element templates, element template migration recipes, `aiagent-api`, `aiagent-model`, `api-changes.json`, the rule documents
(`implementation-rules.md`, `design-guidelines.md`, `target-architecture.md`, `code-review.md`,
`breaking-changes.md`, `acceptance-testing.md`, the module `AGENTS.md`), and authentication, credential and outbound proxy code.

A review that finds none of these does not need a human because of this section. It can still need one
for other reasons, such as a blocking finding or low confidence.
