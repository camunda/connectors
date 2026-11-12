## Description

<!-- Please explain the changes you made here. -->

👤 `origin/main` has no checked-in eval for testing whether Agentic AI instructions help agents perform realistic tasks. This change adds one seeded provider-switch case, with a rubric that scores the fix separately from instruction interpretation. It also trims module guidance, removes two Error Prone checks, and updates the AI PR review workflow pin.

**Observed result:** In `instrumented-repeat`, the instructed run passed 7/7 rubric criteria and the control passed 4/7. Both agents fixed the bug and passed focused and related-converter tests. The score gap came from complete-output assertions and scope/test rationale, not basic task completion. This shows the rubric can distinguish these outcomes in this case.

**What this result cannot tell us:** It does not show whether the instruction changes in this PR help. The agents used an older version of the instructions from commit `0539bc9`. One agent got those instructions; the other got none of the repository instructions. The test did not compare the instructions currently on `main` with the proposed edits. We ran each setup once, so another run might produce different results. Treat this as a useful first test, not proof that the proposed edits work. Human review is pending.

The eval is still useful as a diagnostic: it preserves a Git-pinned case, rubric, validator, patches and test evidence for future comparisons. To use results to justify these instruction changes, rerun the same task against the current base and proposed instructions, then add cases that exercise the guidance being changed. The recorded setup, grades, commands and test counts are in `connectors/agentic-ai/evals/agent-instructions-workspace/instrumented-repeat/`.

The Error Prone check removal and workflow pin update are outside this eval's scope; this result does not assess them.

Validation: `python3 connectors/agentic-ai/evals/agent-instructions/validate.py` — passed. `git diff --check` — passed. The independent focused converter run passed 20 control and 21 instructed test cases. The combined content/request/response converter run passed 73 control and 74 instructed test cases. Both new regressions failed on the seeded defect. Exact Maven commands and reports are in `connectors/agentic-ai/evals/agent-instructions-workspace/instrumented-repeat/verification.json`.

## Related issues

<!-- Which issues are closed by this PR or are related -->

None provided.

## Checklist

- [ ] Backport labels are added if these code changes should be backported. No backport label is added to the latest
  release, as this branch will be rebased onto main before the next release. Example backport labels:
    - `backport stable/8.8`: for changes that should be included in the next 8.8.x release.
    - **or** `backport release-8.8.7`: for changes that should be included in the specific release 8.8.7, and this
      *release has already been created*. The release branch will be merged back into stable/8.8 later, so the change
      will be included in future 8.8.x releases as well.
- [ ] Tests/Integration tests for the changes have been added if applicable.
- [x] If the change requires a documentation update, it has been added to the appropriate section in the documentation.
