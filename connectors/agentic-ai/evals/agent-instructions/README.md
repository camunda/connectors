# 👤 Background (for humans)

This pilot evaluates whether repository instructions help an agent fix a seeded
provider-switch bug in the Agentic AI connector. The rubric scores bug repair
separately from applying the instructions to scope, compatibility and testing.
It adapts the [Agent Skills evaluation method](https://agentskills.io/skill-creation/evaluating-skills)
to repository instructions.

The value is diagnostic: a run shows whether an agent's work meets this case's
rubric and which instruction-relevant expectations it missed. One case and one
paired run provide only case-specific evidence, not a general or causal
estimate of instruction uplift. Run-specific scores and limitations belong in
the run's `benchmark.json`, `setup.json`, `grading.json` and `verification.json`.
Those files are snapshots of the exact Git commit, prompt and settings, not live
metrics for later repository states. After code or instruction changes, add a
new named run rather than updating or reusing old results.

To extend the eval, add a distinct case to `evals.json` with a realistic task
prompt, expected outcome, output contract and evidence-based assertions.
Choose a behavior that exercises a specific instruction; keep task success and
instruction interpretation as separate scoring dimensions. Add only the
case-specific fixture and references it needs. Update `validate.py` to validate
the new case and its run artifacts: it currently requires exactly one case and
validates the provider-switch fixture. Run with and without instructions for
each case, capture the same setup and evidence, and have a person review new
criteria before drawing conclusions. Repeated runs are needed to estimate
run-to-run variation.

# 🤖 Instructions for running it

1. Use the full Git commit SHA in `source_revision` in `evals.json` as the code
   baseline. Verify it with `git show`, then create two disposable copies.
   Apply [`files/provider-switch-regression.patch`](files/provider-switch-regression.patch)
   to both with `git apply --check` first. The pinned source already contains
   the fix; only the disposable copies receive the seeded defect.
2. Run independent agents with identical prompts, model/runtime settings,
   tools and limits. Give the instruction arm the listed instructions and
   aliases. Remove those repository instructions, aliases and linked skills
   from the control arm, and record what was removed. Keep common policies and
   technical references identical.
3. Keep scoring criteria, fixture history and other run outputs hidden from
   both agents. Remove Git history and remotes that reveal the fix. Save each
   arm's patch, explanation, commands and results under a descriptive run
   directory with separate `with_instructions` and `without_instructions`
   folders. Record unavailable metadata as `null` with a reason.
4. Grade the patch and explanation against every assertion. Use `pass`, `fail`
   or `not_run`, with a file reference, command result or quote as evidence.
   Full success requires every criterion to pass. Do not treat rule quotations
   or claims about internal reasoning as evidence.
5. Verify the regression fails on the seeded source and passes with the repair.
   Run the focused converter tests in both arms:

   ```bash
   ./mvnw test -f connectors/agentic-ai/pom.xml -pl connector-aiagent -am \
     -Dtest=AnthropicContentConverterTest -Dsurefire.failIfNoSpecifiedTests=false
   ```

   These tests do not prove live-provider compatibility. Record blockers rather
   than substituting a simplified test.
6. Validate the saved records and patch applicability:

   ```bash
   python3 connectors/agentic-ai/evals/agent-instructions/validate.py
   ```

   This validates JSON, score arithmetic, fixture references and patch
   applicability. It does not execute Java or judge model quality.

For pull-request preparation, use the actual Git base branch and review
`git diff <base>...HEAD`; do not use a product release version as the code
baseline. Do not open a PR as part of running this eval.
