# Microsoft O365 Email Inbound Connector - Improvement Plan

## Overview

This document outlines architectural improvements needed to bring the MS O365 Email Inbound Connector to parity with other production-ready inbound connectors (Email, Kafka, RabbitMQ).

---

## Critical Issues

### 1. ✅ Missing Health Reporting (FIXED)

**Current State:** ~~`Health.up()` is never called after activation. The connector has no way to indicate it's working.~~ FIXED

**Changes Made:**
- Added `context.reportHealth(Health.up())` after successful activation in `MsEmailInboundExecutable.activate()`
- Added `context.reportHealth(Health.down(e))` when activation fails
- Added `context.reportHealth(Health.up())` after successful polling in `EmailPollingWorker.run()`
- Added `context.reportHealth(Health.down(e))` when polling errors occur

**Reference:** See `JakartaEmailListener.java` lines 56-70

### 2. ❌ No Retry/Recovery Mechanism

**Current State:** If the Microsoft Graph API connection fails, the connector just logs and continues without recovery.

**Required Changes:**
- Add Failsafe `RetryPolicy` with exponential backoff for connection issues
- Handle transient API failures gracefully
- Implement reconnection logic similar to generic Email connector's `prepareForPolling()`

**Reference:** See `JakartaEmailListener.java` lines 35-52 for Failsafe usage

### 3. ✅ Exception Handling Without Health Updates (FIXED)

**Current State:** ~~No health reporting or activity logging on errors~~ FIXED

**Changes Made:**
```java
// EmailPollingWorker.run()
try {
    fetcher.poll();
    context.reportHealth(Health.up());
} catch (RuntimeException e) {
    LOGGER.error("Uncaught exception...", e);
    context.log(
        activity -> activity
            .withSeverity(Severity.ERROR)
            .withTag("polling-error")
            .withMessage("Error polling emails: " + e.getMessage()));
    context.reportHealth(Health.down(e));
}
```

---

## Important Improvements

### 4. ⚠️ Missing Element Type Templates

**Current State:** Only one base template defined. Other connectors have 3-4 templates.

**Required Changes:**
Add `elementTypes` to `@ElementTemplate`:
- Message Start Event
- Intermediate Catch Event  
- Boundary Event

**Reference:** See `EmailConnectorExecutable.java` lines 39-50

### 5. ⚠️ Missing Documentation Reference

**Current State:** No `documentationRef` in `@ElementTemplate`

**Required Changes:**
```java
documentationRef = "https://docs.camunda.io/docs/components/connectors/out-of-the-box-connectors/microsoft-o365-mail/"
```

### 6. ⚠️ Activity Logging Gaps

**Current State:** Minimal logging, doesn't use `context.log()` pattern

**Required Changes:**
- Log activation/deactivation events
- Log polling errors with severity
- Log message processing events

**Reference:** See `PollingManager.java` for comprehensive logging examples

---

## Testing Gaps

### 7. 📋 Missing Input Validation Tests

**Current State:** No `*InputValidationTest.java` files

**Required Changes:**
- Add `MsInboundEmailPropertiesValidationTest.java`
- Test required field validation
- Test constraint violations

### 8. 📋 Missing Secrets Tests

**Current State:** No `*SecretsTest.java` files

**Required Changes:**
- Add `MsInboundEmailSecretsTest.java`
- Test secret resolution for `clientId`, `clientSecret`, `tenantId`

### 9. 📋 Missing Integration Tests

**Current State:** Only unit tests exist

**Required Changes:**
- Consider adding integration tests with mocked Graph API
- Or E2E tests if appropriate

---

## Code Quality

### 10. 🔧 Tight Coupling in Worker

**Current State:**
```java
MicrosoftMailClient client = new MicrosoftMailClient(properties);
```

**Required Changes:**
- Accept `MailClient` as constructor parameter
- Use factory or supplier pattern for production vs test scenarios

### 11. 🔧 Package Naming Inconsistency

**Current State:** `io.camunda.connector.azure.email`

**Consideration:** 
- "Azure" is just the identity provider, the connector is for Microsoft 365/Graph
- Consider `io.camunda.connector.microsoft.email` for consistency

### 12. 🔧 TODO Comments Need Resolution

**File: MicrosoftMailClient.java**
- Line 56: Filter injection concern
- Line 63: Error message improvement
- Line 89: Exponential backoff for refetch

**File: Folder.java**
- Line 20: Add documentation link

**File: FilterCriteria.java**
- Line 47: `earliestReceived` filter not implemented

---

## Implementation Priority

### Phase 1: Critical (Must Fix)
1. ✅ Health reporting on activation - DONE
2. ✅ Health reporting on polling errors - DONE  
3. ❌ Basic retry mechanism - TODO

### Phase 2: Important
4. Element type templates
5. Documentation reference
6. Activity logging

### Phase 3: Quality
7-9. Test coverage improvements
10-12. Code quality improvements

---

## Files to Modify

| File | Changes |
|------|---------|
| `MsEmailInboundExecutable.java` | Add health reporting |
| `EmailPollingWorker.java` | Add retry policy, health reporting on errors |
| `MessageProcessor.java` | Add activity logging |
| `MicrosoftMailClient.java` | Resolve TODOs, improve error handling |
| `MsInboundEmailProperties.java` | (none, already clean) |

## New Files to Create

| File | Purpose |
|------|---------|
| `MsInboundEmailPropertiesValidationTest.java` | Input validation tests |
| `MsInboundEmailSecretsTest.java` | Secret handling tests |

---

## References

- Generic Email Connector: `/connectors/email/`
- Kafka Inbound: `/connectors/kafka/src/main/java/io/camunda/connector/kafka/inbound/`
- RabbitMQ Inbound: `/connectors/rabbitmq/src/main/java/io/camunda/connector/rabbitmq/inbound/`
- Project conventions: `/.github/copilot-instructions.md`

