# A2A Remote Agent Interaction Guide

## Overview
Tools prefixed with `A2A_` are autonomous remote agents. 
You interact with them via structured JSON messages. Each tool description specifies the agent's capabilities.

## Response Structure

Every response has a `kind` property that determines how to proceed:

### `kind: "message"` → Complete Response
- The agent has fully answered your request
- Extract data from `contents` array
- No follow-up needed unless starting a new request

### `kind: "task"` → Ongoing Workflow
- Contains `taskId`, `contextId`, and `status`
- Check `status.state` to determine next action
- May require follow-up based on state

## Critical Fields

- **`kind`**: Always check first (`"message"` or `"task"`)
- **`taskId`**: Task identifier assigned by remote agent (in task responses only)
- **`contextId`**: Session identifier assigned by remote agent on first response
- **`status.state`**: Current task state (in task responses only)
- **`contents`**: Array of content objects (in message responses)
- **`artifacts`**: Final outputs (only in completed tasks)
- **`referenceTaskIds`**: Optional array linking to prior task dependencies

## Task States & Actions

| State | Action                                                                              |
|-------|-------------------------------------------------------------------------------------|
| `input-required` | **Reuse** `taskId` + `contextId`, provide requested input in the subsequent message |
| `completed` | Extract `artifacts`, use results. Optionally reference `taskId` in future requests  |
| `submitted`/`working` | Stop processing. The agent didn't response in a timely manner                       |
| `failed` | Check error details. Start **new** request (omit `taskId`) if retrying              |
| `cancelled`/`rejected` | Stop processing. Start new request if needed                                        |

## ID Management (Critical)

### taskId
- **Include when**: Continuing existing task (responding to `input-required`, querying status)
- **Omit when**: Starting any new request
- **Never generate**: Always reuse from response or omit
- **Separate interactions**: Track distinct `taskId`s for concurrent requests to same agent (they may share `contextId`)

### contextId
- **Omit**: Only on your very first message to an agent
- **Include**: In ALL subsequent messages (extract from first response)
- **Never generate**: Always reuse from response

### referenceTaskIds
- **Use when**: New request depends on results from prior completed task(s)

## Multi-Turn Flow Example

```
Request 1:  {contents: [...]}
Response 1: {kind: "task", taskId: "T1", contextId: "C1", status: {state: "input-required"}}

Request 2:  {taskId: "T1", contextId: "C1", contents: [...]}
Response 2: {kind: "task", taskId: "T1", contextId: "C1", status: {state: "completed"}, artifacts: [...]}

Request 3:  {contextId: "C1", referenceTaskIds: ["T1"], contents: [...]}
Response 3: {kind: "message", contextId: "C1", contents: [...]}
```

## Decision Process

1. **Check `kind`**
    - If `"message"`: Extract `contents` and proceed with your work
    - If `"task"`: Continue to step 2

2. **Check `status.state`**
    - `input-required`: Build continuation message with same `taskId` + `contextId`
    - `completed`: Extract `artifacts`, `taskId` can be used for potential future reference
    - `failed`/`cancelled`/`rejected`: Handle error, start new request if needed
    - `submitted`/`working`: Stop processing

## Common Errors to Avoid

❌ Not checking `kind` before processing response
❌ Generating your own `taskId` or `contextId` values
❌ Including `taskId` when starting a new request
❌ Omitting `taskId` when responding to `input-required` status
❌ Reusing `taskId` from terminal states (completed/failed/cancelled/rejected)
❌ Mixing up `taskId`s when managing multiple concurrent tasks to same agent
❌ Including `contextId` in your very first message to an agent
❌ Treating message responses as requiring follow-up

## Quick Reference

- **New request**: Omit `taskId`, include `contextId` (except first message)
- **Continue task**: Include both `taskId` and `contextId`
- **Reference prior work**: Use `referenceTaskIds` with new request
- **IDs flow**: Remote agent creates → You save → You reuse
