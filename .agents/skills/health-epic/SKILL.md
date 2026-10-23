---
name: health-epic
description: Guide the creation and planning of Camunda Health Epics in the Core PDP or Experience PDP, including repository selection, issue template, required label, skipped stages, and planning communication.
---
# Camunda Health Epic

Use this skill when the user asks how to create, classify, plan, or move a Health Epic at Camunda.

## Decide whether a Health Epic is appropriate

Use a Health Epic for significant foundation work that needs explicit capacity protection or coordination: typically about 2 FTE-weeks or more, multiple weeks, multiple teams, Core contracts/shared platforms/public APIs, reliability, security, compliance, CI/CD, architecture, or substantial technical debt.

Do not create one for cheap, opportunistic fixes; handle those as normal engineering work.

If the work crosses the Experience/Core boundary and concerns a governed Core contract, route it to Core rather than creating an Experience pod Health Epic.

## Connectors context

The user/team works on **Camunda Connectors**. When drafting or assessing a Health Epic, use Connectors-specific context where relevant: connector runtime and SDK, inbound/outbound connector reliability, security, CI/CD and release stability, shared connector infrastructure, or production hardening.

Treat “Connectors” as team context, not as an automatic PDP classification. Select Core versus Experience based on ownership and the nature of the work. If the topic concerns a governed Core contract or shared platform, use Core; do not route it to Experience merely because a pod is involved.

## Identify the PDP

- **Core PDP:** use for Core contracts, execution/backend foundations, shared platform, or other Core-owned foundation work.
- **Experience PDP:** use for a pod-owned Experience foundation topic, including hardening an AI-generated prototype for production.
- If the PDP is unclear, ask the user which domain owns the work before giving repository-specific instructions.

## Core PDP procedure

1. Open the Product Hub Health Epic template: https://github.com/camunda/product-hub/issues/new?template=health-epic.md
2. Complete the template with the problem, scope, expected engineering investment, impact/risk, ownership, and planning information.
3. Ensure the issue has the mandatory label `kind:healthEpic`.
4. Create the GitHub issue in `camunda/product-hub`.
5. Move it directly to **Define**; skip **Discovery**.
6. Build through **Implement** and close it when implemented; skip **Validate**.
7. Raise it with the PM/EM before planning so its capacity trade-off is visible. This is visibility, not an approval request.
8. Use the normal PDP commitment, priority (P0–P3), confidence, and Target/Potential conventions.

Do not create a Core Health Epic through Productboard.

## Experience PDP procedure

1. Open the Experience PDP Health Epic template: https://github.com/camunda/experience-pdp/issues/new?template=health-epic.md
2. Complete the template with the problem, scope, expected engineering investment, impact/risk, ownership, and planning information.
3. Ensure the issue has the mandatory label `kind:healthEpic`.
4. Create the GitHub issue in `camunda/experience-pdp`.
5. Move it directly to **Prototype**; skip **Plan**.
6. Use Prototype to shape and de-risk the approach, then move to **Production** to build, test, and harden.
7. Close it once productized; skip **Validate**.
8. Raise it with the Pod Lead and the EM before planning so capacity is visible and can be traded off against value work.

## Planning guidance

- Health Epics and value/roadmap epics compete for the same capacity.
- Use the shared release-planning cadence and the normal Target/Potential commitment model.
- Use rough T-shirt sizing when requested: XS <2 weeks, S 2 weeks, M 6 weeks, L 1 quarter, XL >1 quarter.
- If the user asks for issue content, draft it but do not create or modify the GitHub issue unless they explicitly authorize that action.

## Source of truth

For current details, verify against the handbook page before asserting a changed process:
https://lamppost.camunda-it.rocks/handbook/#c9ede4a2/pdp-health-epics-in-quarterly-planning
