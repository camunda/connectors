# Agent State Machine & Initialization

The agent context tracks state via `AgentState` enum:

```
INITIALIZING ──────────────────────────────────> READY
      │                                            ▲
      │ (if gateway tools present)                 │
      ▼                                            │
TOOL_DISCOVERY ──(all results present)────────────┘
      │
      │ (not all results yet)
      ▼
  DeferConversation
  (complete job as no-op, wait for more results)
```

**`AgentInitializerImpl.initializeAgent()`** is the entry point:

- **INITIALIZING**: First execution. Loads ad-hoc tool schema, determines tool definitions. If gateway tools exist, initiates discovery (returns tool calls to activate MCP/A2A clients). Otherwise transitions to READY.
- **TOOL_DISCOVERY**: Waiting for gateway tool discovery results. Checks if all expected results are present. If not, returns `DeferConversation` (no-op completion). If yes, processes results and transitions to READY.
- **READY** (or any other state): Normal operation. Validates that the process definition hasn't changed (for migration detection). Updates tool definitions if needed.
