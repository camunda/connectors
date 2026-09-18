# ADR-0008: AI Agent v2 Reusable Credentials

## Status
Proposed

## Context

AI Agent v2 has several provider backends whose connection and authentication data can be reused,
while model selection, API family, request customization, and provider-specific behavior remain
task-level choices. A single provider credential would either hide those choices or duplicate AWS
IAM credentials already shared by the AWS connector family.

The c8run bundle does not provide a low-code credential-creation flow. Requiring reusable
credentials would prevent users from configuring a model connection entirely in the properties
panel. The same templates must support inline setup without provisioning a cluster variable.

## Decision

Use provider-specific whole-object credentials with optional chooser fields in the current AI Agent
v2 templates, including hybrid variants. Show inline authentication and connection fields when the
applicable chooser is empty; hide them when a credential is selected. The selected credential is
authoritative, even if incomplete: do not fall back to stale inline values. Historical templates
and inline jobs remain supported by the runtime.
Keep provider/backend/API discriminators, model and request options local. Reuse
`io.camunda:aws-credential:1` for Bedrock IAM and add a separate Bedrock API-key credential; the
Bedrock authentication-family discriminator exposes exactly one applicable chooser. Gateway
credentials contain an endpoint and an authentication selector: API key or OAuth 2.0 client
credentials. Both authentication methods work with the selected Anthropic or OpenAI protocol.
OAuth token endpoint, client ID/secret, client authentication method, audience, and scopes can be
configured in the credential or inline. Reuse the existing OAuth model and token resolver.

## Consequences

### Positive

Credentials can be shared without making model behavior dependent on inaccessible credential data.
Users of c8run and other environments without credential management can configure connections inline.
Existing inline jobs continue to bind. Optional local Gateway and Bedrock endpoint overrides remain
possible, as do Bedrock region overrides. Inline gateway endpoints and Bedrock regions are required
without a credential; a local Bedrock region is also required when the selected AWS credential does
not specify a default.

### Negative

The native provider models contain explicit effective-value accessors and the generated template
contains several credential schemas and conditional fallback fields. Template-only endpoint and
region variants share existing bindings to express required inline values versus optional overrides.
Validation must ignore hidden inline defaults when a credential is selected. Credentials for MCP,
A2A, custom beans, and memory remain outside this decision.
