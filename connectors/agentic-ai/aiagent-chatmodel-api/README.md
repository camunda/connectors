# aiagent-chatmodel-api

## Purpose

The chat model provider SPI of the AI Agent, in the package `aiagent.api.chatmodel`. To add an LLM provider, implement
a `ChatModelFactory` and register it as a Spring bean. The module depends on `aiagent-model` only and does not depend on
Spring ([ADR 020](../docs/adr/020-chat-model-spi-module.md)).

## Key types

- `ChatModel`, `ChatModelFactory`, `ChatRequest` and `ChatResult`: the chat model provider SPI.
  A `ChatRequest` sends the `ChatConversation` to the LLM: messages typed by role (`ChatRequestMessage`) that hold
  only the content a provider can receive.
- `ChatModelRejectedException`: the sealed type for a rejected model call, with
  `ContentFilteredException`, `ContextWindowExceededException` and `GuardrailInterventionException`.

## Docs

- [Chat model provider SPI](docs/chat-model-spi.md)
- [Extension points](../docs/architecture/extension-points.md#add-an-llm-provider)
- [Native providers](../connector-aiagent/docs/native-providers.md)
