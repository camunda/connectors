# ADR-0008: AI Agent v2 Reusable Credentials

## Status
Proposed

## Context

AI Agent v2 has several provider backends whose connection and authentication data can be reused,
while model selection, API family, request customization, and provider-specific behavior remain
task-level choices. A single provider credential would either hide those choices or duplicate AWS
IAM credentials already shared by the AWS connector family.

## Decision

Use provider-specific whole-object credentials with required chooser fields in the current AI Agent
v2 templates. New templates are credential-only for model connections: inline authentication and
connection fallback fields are not exposed. Historical templates and inline jobs remain supported
by the runtime.
Keep provider/backend/API discriminators, model and request options local. Reuse
`io.camunda:aws-credential:1` for Bedrock IAM and add a separate Bedrock API-key credential; the
Bedrock authentication-family discriminator exposes exactly one applicable chooser. Gateway
credentials contain an endpoint and API key only and are interpreted by the selected Anthropic or
OpenAI protocol.

## Consequences

### Positive

Credentials can be shared without making model behavior dependent on inaccessible credential data.
Existing inline jobs continue to bind. Optional local Gateway and Bedrock endpoint overrides remain
possible, as do Bedrock region overrides. A local region is only required when the shared AWS
credential does not specify a default.

### Negative

The native provider models contain explicit effective-value accessors and the generated template
contains several credential schemas. Gateway OAuth and credentials for MCP, A2A, custom beans, and
memory remain outside this decision.
