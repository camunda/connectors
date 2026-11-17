# Element template generation

The transform scripts in [`../bin/`](../bin/) change the generated AI Agent templates in
[`../element-templates/`](../element-templates/). This doc describes what they add.

## Provider steps in the v2 templates

All four v2 templates share the provider and backend steps. Backend IDs and labels come from the
generated dropdowns. Edit preset defaults in
[`../bin/transform-ai-agent-provider-steps.groovy`](../bin/transform-ai-agent-provider-steps.groovy), not
the generated JSON.

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
