# HTTP Webhook Connector

Start a BPMN process instance triggered by external system.

## Overview

The HTTP Webhook Connector allows you to receive HTTP requests from external systems and trigger BPMN processes. It supports various authentication methods, request validation, and customizable responses.

## Verification Expression

The verification expression is a powerful feature that allows you to control webhook behavior before a process instance is created. When a webhook receives an incoming request, the verification expression is evaluated first:

- If the expression returns **a non-null response**, that response is returned to the caller immediately, and **no process instance is created**
- If the expression returns **null**, the webhook proceeds normally to trigger the process instance

This feature enables several important use cases:

### Use Case 1: One-Time Verification Challenges

Many webhook providers (like Slack, Microsoft Teams, etc.) require a one-time verification challenge when you register a webhook URL. The provider sends a challenge value that you must echo back to verify you control the endpoint.

**Example:**
```
=if request.body.challenge != null 
  then {"body": {"challenge": request.body.challenge}} 
  else null
```

### Use Case 2: Payload Validation

Validate incoming payloads and reject invalid requests with custom error messages before creating a process instance.

**Example - Validate required fields:**
```
=if request.body.orderId = null or request.body.customerId = null
  then {
    "statusCode": 400,
    "body": {
      "error": "Missing required fields: orderId and customerId are required"
    }
  }
  else null
```

**Example - Validate field types and ranges:**
```
=if not(is defined(request.body.amount)) or request.body.amount < 0
  then {
    "statusCode": 400,
    "body": {"error": "Invalid amount: must be a positive number"}
  }
  else if not(is defined(request.body.email)) or not(matches(request.body.email, "^[^@]+@[^@]+\.[^@]+$"))
  then {
    "statusCode": 400,
    "body": {"error": "Invalid email format"}
  }
  else null
```

### Use Case 3: Conditional Routing

Handle different event types differently - some may trigger processes, others may just return acknowledgments.

**Example:**
```
=if request.body.event_type = "ping"
  then {"body": {"status": "ok"}, "statusCode": 200}
  else if request.body.event_type = "test"
  then {"body": {"message": "Test received"}, "statusCode": 200}
  else null
```

### Use Case 4: Content-Based Response Customization

Return different responses based on the request content while still creating the process instance in some cases.

**Example:**
```
=if request.body.priority = "low"
  then {
    "statusCode": 202,
    "body": {"message": "Request queued for later processing"}
  }
  else null
```

### Use Case 5: Custom Headers and Status Codes

Control the HTTP response including headers and status codes.

**Example:**
```
=if request.body.source = "legacy-system"
  then {
    "body": {"acknowledged": true},
    "statusCode": 201,
    "headers": {
      "X-Processing-Mode": "legacy",
      "Content-Type": "application/json"
    }
  }
  else null
```

## Verification Expression Structure

The verification expression must return either:
- **`null`** - to allow normal webhook processing and process instance creation
- **A response object** with the following optional properties:
  - `body` - The response body (can be any JSON-serializable value)
  - `statusCode` - HTTP status code (default: 200)
  - `headers` - Map of HTTP headers to include in the response

## Available Context in Verification Expression

The expression has access to a `request` object containing:
- `request.body` - The parsed request body
- `request.headers` - Map of HTTP headers
- `request.params` - Map of query parameters

## Interaction with Other Webhook Features

The verification expression is evaluated **before**:
- Authentication checks (HMAC, API keys, etc.)
- Activation conditions
- Process correlation

If you need to validate authenticated requests, authentication should still be configured separately. The verification expression can access the authenticated request data.

## When to Use Verification Expression vs. Activation Condition

- **Verification Expression**: Use when you need to **prevent process instance creation** and **control the HTTP response** (status code, headers, body)
- **Activation Condition**: Use when you want to **filter which events trigger the process**, but allow the webhook to return standard responses (422 for unmatched events when "Consume unmatched events" is disabled)

## Examples in Tests

For more examples, see the test cases in `HttpWebhookExecutableTest.java`:
- `triggerWebhook_VerificationExpression_ReturnsChallenge`
- `triggerWebhook_VerificationExpressionWithModifiedBody_ReturnsChallenge`
- `triggerWebhook_VerificationExpressionWithStatusCode_ReturnsChallenge`
- `triggerWebhook_VerificationExpressionWithCustomHeaders_ReturnsChallenge`
