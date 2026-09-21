# Reusable saved credentials for v2 provider templates

* Deciders: Agentic AI Team
* Date: September 21, 2026

## Status

**Accepted**

## Context and Problem Statement

Camunda's credential-templates feature lets a value be entered once into a separately saved,
reusable credential element template and bound from any diagram that needs it, instead of being
retyped inline on every task. Several connectors already support it. The v2 native LLM provider
templates (Anthropic, OpenAI, Gemini, Bedrock Converse) do not: every diagram using, say, the
Anthropic API backend must retype the same API key inline, and rotating it means editing every
diagram that carries it.

Each v2 provider backend already models its authentication as one or more scalar fields (an API
key, a client secret pair, ...) alongside always-needed configuration that is not secret (an
endpoint override, HTTP headers, query parameters, body properties). Adding credential support
without care risks either leaking those non-secret escape-hatch fields into the saved-credential
dialog, or restructuring the existing discriminated backend/authentication unions and their
property paths more than necessary.

## Decision Drivers

* Let a secret be entered once and reused across diagrams; ease rotation.
* Don't leak escape-hatch fields (endpoint, headers, query parameters, body properties) into the
  saved-credential dialog — they are diagram-level configuration, not part of the secret.
* Don't restructure existing discriminated unions (backend choice, authentication-type choice) or
  churn their property paths beyond what's unavoidable.
* Reuse an already-established shared credential type where one already matches an authentication
  shape, instead of building parallel machinery.
* v2 templates are shipping as GA; this is a feature addition to a released surface, not a
  proof-of-concept.

## Considered Options

1. **Generic cross-provider credential** wrapping an arbitrary secret, shared by every provider.
   Rejected: loses per-provider shape and validation, produces an awkward "pick a credential that
   happens to fit" selection UX, and provides no real reuse since every provider's secret shape
   differs (a single key vs. a key/org/project trio vs. a client-credentials triple).
2. **Wrap the entire connection or backend object** as the credential. Rejected: the credential
   dialog would then also expose the escape-hatch fields, which are never meant to be part of a
   saved secret, and binding a credential would force re-entering the non-secret fields per
   diagram, or hide them from the diagram they're meant to configure.
3. **A new top-level "family" choice** (bound credential vs. inline authentication) sitting above
   the existing authentication-type discriminator. Rejected: duplicates the discriminator concept
   the template already has, and renames/renests existing property paths across the board for no
   benefit the next option doesn't already give.
4. **Gate only the replaceable scalar(s).** For each backend or authentication variant that carries
   secret scalars, add a sibling field of a small `@Configuration`-typed credential wrapping just
   those scalars, and hide the scalars behind a `@NestedProperties` visibility condition on that
   field. Escape hatches and the existing discriminators are untouched and always visible. Where a
   shared credential type already exists and already matches a variant's authentication shape
   (AWS IAM, via the existing `io.camunda:aws-credential:1`), gate the whole variant instead of
   introducing a redundant dedicated type.

## Decision Outcome

Chosen option 4, applied uniformly per backend and per authentication variant across Anthropic,
OpenAI, Gemini and Bedrock Converse (including the AWS-Bedrock-Mantle backend Anthropic shares with
it). Concretely:

* Every credential sibling field is named distinctly and prefixed by provider/backend/variant
  (e.g. `anthropicApiCredential`, `bedrockApiKeyCredential`), matching the one precedent already in
  the codebase (`awsCredential`). This keeps every credential field's generated property id unique
  even where a discriminated union flattens several variants' fields onto one property level, and
  needs no per-field id override to avoid collisions.
* A variant with no secret scalars (a default-credentials-chain, a managed-identity flow) gets no
  credential — there is nothing to save.
* AWS-authenticated backends (Bedrock Converse, Anthropic's AWS Bedrock Mantle) reuse the existing
  shared AWS credential and its established region-override behavior (a template-only override
  field bound to the same region property, shown only once a credential is chosen) rather than
  inventing a parallel resolution mechanism — the same approach every other AWS-backed connector in
  this repository already uses.

## Follow-up

* Anthropic's and OpenAI's `custom` backends are intentionally not covered yet: whether an
  `endpoint` belongs inside a credential there, or stays a per-diagram concern even when the
  authentication is saved, is still open.
