# Agentic AI architecture decision records

These authoring rules apply to ADRs in this directory. See the
[design guidelines](../design-guidelines.md#architecture-decision-records) for when an ADR is needed.

**Write the ADR before implementation.** Capture the decision and its drivers during planning so
review happens on the decision rather than after the code exists.

**An ADR is immutable once merged.** It may still be revised within the scope of the PR that
introduces it. After it lands on `main`, leave it as-is: supersede or extend it with a new ADR, marking
the old one's Status as `Superseded` and linking the replacement.

Follow [ADR 001](001-replace-mcp-client-framework.md): Title, Deciders/Date, Status, Context &
Problem, Decision Drivers, Considered Options, Decision Outcome.

**ADRs own the why; reference docs own the current state.** State the problem, drivers, options
and choice in the ADR. Keep current behavior and implementation detail in code or reference docs;
link to the ADR when history matters instead of narrating it there. No code excerpts, line-number
citations or class-by-class walkthroughs. Include implementation detail in an ADR only when needed
to justify the decision; cut paragraphs explaining *how* rather than *why*.

**Known gaps get a named Follow-up, not repeated disclaimers.** Record a rough edge once in a
`## Follow-up` section, with the concrete condition and mechanism that closes it. State the fact
where observed and link to the ADR for the reasoning.
