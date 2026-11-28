# Element template generation

The transform scripts in [`../bin/`](../bin/) change the generated AI Agent templates in
[`../element-templates/`](../element-templates/). This doc describes what they add, and how to maintain the
element template READMEs.

## Provider steps in the v2 templates

All four v2 templates share the provider and backend steps. Backend IDs and labels come from the
generated dropdowns. Edit preset defaults in
[`../bin/transform-ai-agent-provider-steps.groovy`](../bin/transform-ai-agent-provider-steps.groovy), not
the generated JSON.

## Properties the frozen templates leave out

A template can leave out properties of the request data it shares with other templates. The frozen v1
templates use this to get no setting that was added after them. The `omittedProperties` property in
[`../pom.xml`](../pom.xml) lists them as one `templateId=id,id` tuple per line, keyed by the id of the
generated template. Both transform scripts drop the listed properties from the template with that id; a
template without an entry keeps every property. A group whose properties are all left out is dropped
too, so the template has no empty group.

## Markers added by the transform scripts

### Agent definition marker

The generated AI Agent Task and Sub-process templates in `element-templates/` and in
`element-templates/hybrid/` (v1 and v2) carry a hidden `zeebe:agentDefinition` property so the engine
detects a native agent at deploy time (camunda/connectors#8176). The snapshots in
`element-templates/versioned/` do not carry it. A job from such a template has no agent definition key
header, so the connector creates no agent instance for it.
[`../bin/transform-ai-agent-task-template.groovy`](../bin/transform-ai-agent-task-template.groovy) adds it
to each task template (`agentType=aiAgentTask`);
[`../bin/transform-ai-agent-sub-process-template.groovy`](../bin/transform-ai-agent-sub-process-template.groovy)
adds its own (`agentType=aiAgentSubProcess`) to the derived sub-process template. Both take `agentType` as a
Maven property. Both scripts drop any incoming `zeebe:agentDefinition` before adding their own, so
execution order does not matter. The sub-process transformer cannot carry the task marker into the
derived template regardless of when the task-marker script runs.

### Deprecation marker (v1 only)

Only the current main/hybrid v1 AI Agent Task and Sub-process templates (version 13, minimum Camunda
8.10) carry a top-level `deprecated` block pointing modelers to the v2 template, and have `(Deprecated)`
appended to their `name`. Their `element-templates/versioned/` snapshots at versions 10 to 12 do not: Web
Modeler never offers an Update action on a deprecated template, so deprecating those older versions too
would strand anyone still on them with no click-path to version 13 (see #8431's follow-up revert). Earlier
v1 versions (8.8/8.9-minimum, versions 0 to 7) carry neither, and neither do v2 templates. For the
generated main/hybrid templates, both transform scripts accept optional `deprecationMessage` /
`deprecationDocumentationRef` Maven properties (wired only into the v1 executions in
[`../pom.xml`](../pom.xml)), and each script strips any inherited `deprecated` block before deciding its
own and appending `(Deprecated)` to `name` if not already present, the same pattern used for the agent
definition marker above. `element-templates/versioned/` snapshots are static and are patched by hand
instead (three pre-8.10 snapshots already carried an unrelated legacy `"deprecated": true` boolean flag
and were left untouched).

## Element template READMEs

When a version is bumped, a template moves into `versioned/`, or a connector is added, update the README in the
`element-templates` folder of the connector module (for example [`../element-templates/README.md`](../element-templates/README.md))
and the [index of all modules](../../connector-agentic-ai/element-templates/README.md) by these rules:

1. **Identify the new minimum Camunda version** by checking the `engines.camunda` field of the new
   template (e.g. `^8.10`).
2. **Same minimum as the current top row**: bump the top row's template version and keep its link
   pointing to the latest `./<file>.json` in the `element-templates` folder. The superseded version moves into `versioned/`
   but is not listed (the table shows only the latest template per minimum Camunda version).
3. **Higher minimum than the current top row**: insert a new top row with the new minimum Camunda
   version and template version, and move the previous top row's link under `versioned/`. Skip
   template versions so the previous minor keeps room for patch releases: use the smallest multiple
   of 5 that leaves at least two versions free (7 to 10 for Camunda 8.10, 1 to 5 for Camunda 8.11).
   The patch releases of the previous minor take the skipped versions on its `stable/*` branch. The
   validator rule `current-version-bump` expects the highest `versioned/` snapshot plus one, so
   suppress it for the bumped templates in the `element-template-suppressions.json` of the module.
   The next regular bump fails on the unused suppression; remove it then.
4. **AI Agent has two tables** (Task and Sub-process) sharing the same version numbers. Update both.
5. **New connector**: in an existing module, add a section to the README of the module with an intro
   paragraph linking to the `docs.camunda.io` overview page. In a new module, add a README like the
   existing ones, including the "Which template do I need?" section, and add the module to the table
   and the sections of the index in the same order as the existing ones (AI Agent, MCP Client, A2A,
   Ad-hoc tools schema).

Do not list `hybrid/` templates in the README. They are intentionally omitted.
