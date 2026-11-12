# Two Flavors: AI Agent Task vs AI Agent Sub-process

## AI Agent Task (Outbound Connector)

- **BPMN element**: Service task with connector template applied
- **Class**: `AgentTaskV1Function` implements `OutboundConnectorFunction`
- **Type**: `io.camunda.agenticai:aiagent:1`
- **Execution context**: `AgentTaskExecutionContext`, built by `AgentExecutionContextFactory`
- **Request handler**: `AgentTaskRequestHandler`
- **Tool resolution**: Fetches process definition XML via the Camunda API to resolve tool elements from a referenced ad-hoc sub-process (eventually consistent — can fail on first deploy)
- **Feedback loop**: Must be **modeled explicitly** in BPMN — the process must route tool calls to a multi-instance ad-hoc sub-process and route results back to the AI Agent task
- **Agent context**: Flows through process variables — the modeler must wire `agent.context` back as input for the next iteration
- **No event handling support**: Does not support event sub-processes
- **Job completion**: Handled by the connector runtime (auto-complete) — returns `AgentResponse` directly

## AI Agent Sub-process (Job Worker)

- **BPMN element**: Ad-hoc sub-process with job worker element template applied
- **Class**: `AgentSubProcessV1Function` with `@OutboundConnector`
- **Type**: `io.camunda.agenticai:aiagent-job-worker:1`
- **Execution context**: `AgentSubProcessExecutionContext`, built by `AgentExecutionContextFactory`
- **Request handler**: `AgentSubProcessRequestHandler`
- **Tool resolution**: Tools come directly from the `adHocSubProcessElements` variable (populated by Zeebe) — no API call needed
- **Feedback loop**: **Implicit** — the job worker completes the job with element activation commands, and Zeebe automatically creates a new job when those elements complete
- **Agent context**: Stored as `agentContext` variable within the ad-hoc sub-process scope
- **Event handling**: Supports non-interrupting event sub-processes with configurable behavior
- **Job completion**: Manual via `jobClient.newCompleteCommand(job).withResult(...)` including ad-hoc sub-process directives

## Key Differences Summary

| Aspect                               | AI Agent Task                            | AI Agent Sub-process                                |
|--------------------------------------|------------------------------------------|-----------------------------------------------------|
| BPMN element                         | Service task                             | Ad-hoc sub-process                                  |
| Feedback loop                        | Explicit (modeled)                       | Implicit (engine-managed)                           |
| Tool resolution source               | Camunda API (XML fetch)                  | `adHocSubProcessElements` variable                  |
| Agent context management             | Via process variable wiring              | Scoped within sub-process                           |
| Event sub-process support            | No                                       | Yes (non-interrupting)                              |
| Config re-evaluation per iteration   | Yes (input mappings per task execution)  | No (input mappings evaluated once on AHSP entry)    |
| Process migration config changes     | Supported                                | Not supported (frozen at entry)                     |
| Job completion                       | Auto (connector runtime)                 | Custom (`AdHocSubProcessConnectorResponse`)         |
