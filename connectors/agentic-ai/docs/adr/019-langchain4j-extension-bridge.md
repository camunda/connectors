# Keep LangChain4j as an extension bridge, not as a provider stack

* Deciders: Agentic AI Team
* Date: Nov 23, 2026

## Status

**Accepted**. Partly supersedes [ADR 014](014-route-v1-requests-through-native-providers.md) D3. ADR 014 removes the
whole LangChain4j integration. This ADR removes the LangChain4j provider stack and keeps a generic bridge for custom
providers. All other decisions of ADR 014 stand.

## Context and Problem Statement

[ADR 014](014-route-v1-requests-through-native-providers.md) routes every v1 request through the native providers and
removes the LangChain4j integration with the factories, the framework binding and the `dev.langchain4j` dependencies.
The reason is one provider stack instead of two.

The factories for the vendors are the part of the integration that duplicates the native providers. The rest is
vendor neutral: the conversion of the conversation, the tool definitions, the response format and the documents to the
LangChain4j types, the timing of the call, the token usage and the round trip of provider data through the tool calls. A
vendor or an operator who runs a model that LangChain4j supports and no native provider covers can register their own
`ChatModelFactory` on top of it today. Without the bridge, each of them has to rebuild the converter chain and keep it
in step with the domain model, including every new content type.

The framework is built for pro-code users and vendors. This ADR decides whether that extension surface stays after the
vendor factories are gone, and what contract it has.

## Decision Drivers

* **Pro-code extensibility without a rebuild of the converter chain**: a user adds a provider that LangChain4j supports
  with a small factory.
* **One shipped provider stack**: the goal of ADR 014. No vendor factory on LangChain4j ships.
* **A small surface that is hard to misuse**: nothing public that only tests use, and a contract that makes each
  factory declare its match.
* **No coupling between native providers and the bridge**: they share no code, as the implementation rules require.

## Considered Options

1. Remove LangChain4j completely, as ADR 014 D3 says.
2. Keep the LangChain4j provider stack: the vendor factories and their wiring.
3. Keep only the bridge as a supported extension point. No vendor factory ships.

## Decision Outcome

Chosen option: **Option 3, the bridge only**, because it keeps the part that users can build on, and drops the part that
duplicates the native providers. Option 1 forces every user to rebuild the converter chain. Option 2 keeps two provider
stacks, which ADR 014 rejects.

### D1: The bridge is a supported extension point

The bridge consists of the factory base class for LangChain4j models, the converter chain, the support for the
connector proxy settings of a JDK HTTP client, and the two `langchain4j` dependencies that these need. It is a
supported SPI: a breaking change to it goes into `breaking-changes.md`. The bridge loads only when the application
registers a factory bean, so a runtime without a custom LangChain4j provider carries none of it. The unit tests cover it through a custom factory.
There is no shipped provider, so there is no real-provider end-to-end test. The implementation rules name the bridge
converters as a location of a change only when the change alters the domain message or content model.

### D2: A custom factory matches explicitly

A factory declares the configuration type it serves and implements the match itself. The base class has no default
match, because the registry needs exactly one match and a default that matches every configuration of the type would
match every custom provider. The base class checks the type before it builds a model, so a match that is wider than the
declared type fails with a clear message and not with a cast error. A factory for a custom provider uses the domain
configuration `CustomChatModelConfiguration` and matches on its `providerType`.

### D3: Provider data on tool calls belongs to the factory, and the connector owns the namespace

Some providers, such as Gemini 3 with its thought signatures, require data from a response to come back verbatim on the
next request. A factory carries this data through the tool calls with two typed hooks. The connector persists what the
write hook returns under the provider ID of the configuration, and gives the read hook only that entry. The factory owns
the hooks because it knows its provider and its configuration type, and a bean for all providers would have to switch
on the configuration. The connector owns the namespace so that a provider cannot read the data of another provider,
for example after a process instance migration switches the provider.

### D4: Placement is not part of this decision

The bridge stays in `connector-aiagent` with its dependencies. Whether it moves into an opt-in module belongs to the
module split ([ADR 015](015-split-agentic-ai-into-modules.md)).

### Non-goals

* A LangChain4j based provider for a vendor that has a native provider.

### Positive Consequences

* A user extends the connector with a LangChain4j model by writing the factory and the model construction only.
* The one shipped provider stack stays the native stack.
* The registry detects a missing or ambiguous match only when a request for that configuration arrives, and fails with
  a clear message. The abstract `supports` makes a factory declare its match, but nothing detects an overlap between two
  factories before that request.

### Negative Consequences

* The connector keeps `langchain4j-core` and `langchain4j-http-client-jdk` and a public surface that nothing in the
  repository uses.
* The bridge has no end-to-end coverage against a real provider. A change to the domain message model must still check
  the converters.
