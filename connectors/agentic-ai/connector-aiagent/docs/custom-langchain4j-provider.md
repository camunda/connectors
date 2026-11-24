# Custom LangChain4j provider

Earlier versions ran the v1 AI Agent providers (Anthropic, Azure OpenAI, Bedrock, Google Vertex AI, OpenAI and
OpenAI-compatible endpoints) on LangChain4j. Now every provider is native, and the LangChain4j factories for the vendors
are removed. A small bridge stays, so that you can run a model that LangChain4j supports, for a configuration that no
native provider serves or in place of one, without rebuilding the conversion of messages, tools and documents. This page
shows the steps with one example. For the
decision, see [ADR 019](../../docs/adr/019-langchain4j-extension-bridge.md). For what the bridge contains, see
[`langchain4j-chat-model.md`](langchain4j-chat-model.md).

A custom provider needs a connector runtime that you operate yourself, because the Camunda-managed SaaS runtime cannot
load your factory. That is a Self-Managed connector runtime, which can be attached to a SaaS cluster (hybrid mode). The
example runs the OpenAI chat model of LangChain4j against a provider with the type `acme`. It uses the configuration of
the **Custom Implementation** provider of the element templates, which is the usual starting point. The factory is not
tied to it, see [step 4](#4-write-the-factory).

## 1. Select the provider in the element template

In the element template, choose the provider **Custom Implementation** and set:

- **Provider type**: `acme`. The factory matches on it.
- **Model**: the model ID. It becomes `CustomChatModelConfiguration#model()`.
- **Provider parameters**: a FEEL map that only your factory reads, for example
  `={ baseUrl: "https://llm.example.com/v1", apiKey: camunda.secrets.ACME_API_KEY }`.

## 2. Add the dependencies

Add the LangChain4j module of your provider to your runtime. The connector already provides `langchain4j-core` and
`langchain4j-http-client-jdk`. Use the same LangChain4j version for all modules.

```xml
<dependency>
  <groupId>dev.langchain4j</groupId>
  <artifactId>langchain4j-open-ai</artifactId>
  <version>${version.langchain4j}</version>
</dependency>
```

## 3. Enable the bridge

The bridge is off by default. Set the property in the configuration of your runtime:

```properties
camunda.connector.agenticai.aiagent.langchain4j.enabled=true
```

It then provides the beans that the factory needs: `ChatMessageConverter`, `ToolSpecificationConverter`,
`JsonSchemaConverter` and `ChatModelHttpProxySupport`.

## 4. Write the factory

Implement `ChatModelFactory`. The entry point is the chat model `LangChain4JChatModel`: `create` builds your LangChain4j
model and wraps it in a `LangChain4JChatModel`. The factory decides which `ChatModelConfiguration` it serves in
`supports`, and the registry needs exactly one match, so match only your configurations. The example matches the
`CustomChatModelConfiguration`, the domain view of the template fields of the **Custom Implementation** provider
(`providerType()`, `model()`, `parameters()`), on its provider type. Any other configuration works the same way: you can
serve the configuration of a native provider, for example OpenAI, with a LangChain4j model as long as no other factory
matches it, or serve a configuration type of your own that implements `ChatModelConfiguration` directly. The connector creates one model per request and closes it
afterwards, so return a model that owns the resources that it opens.

<!-- docs-lint: allow-java-block: the example of a custom factory reads clearer as code than as prose -->

```java
public class AcmeChatModelFactory implements ChatModelFactory {

  private final ChatModelHttpProxySupport httpProxySupport;
  private final ChatMessageConverter chatMessageConverter;
  private final ToolSpecificationConverter toolSpecificationConverter;
  private final JsonSchemaConverter jsonSchemaConverter;

  public AcmeChatModelFactory(
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ChatModelHttpProxySupport httpProxySupport) {
    this.chatMessageConverter = chatMessageConverter;
    this.toolSpecificationConverter = toolSpecificationConverter;
    this.jsonSchemaConverter = jsonSchemaConverter;
    this.httpProxySupport = httpProxySupport;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof CustomChatModelConfiguration custom
        && "acme".equals(custom.providerType());
  }

  @Override
  public ChatModel create(ChatModelConfiguration configuration) {
    final var custom = (CustomChatModelConfiguration) configuration;
    final var parameters = custom.parameters();

    // The connector proxy settings apply to the HTTP client. The builder closes the client.
    final var httpClientBuilder = httpProxySupport.createJdkHttpClientBuilder();
    httpClientBuilder.connectTimeout(Duration.ofSeconds(30)).readTimeout(Duration.ofMinutes(2));

    final var model =
        OpenAiChatModel.builder()
            .httpClientBuilder(httpClientBuilder)
            .baseUrl((String) parameters.get("baseUrl"))
            .apiKey((String) parameters.get("apiKey"))
            .modelName(custom.model())
            .build();

    return new LangChain4JChatModel(
        new CloseableChatModelDelegate(model, httpClientBuilder),
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
  }
}
```

`CloseableChatModelDelegate` pairs the model with the resource that it closes. If your model owns no resource, pass a
`CloseableChatModel` that implements `close()` as a no-op.

## 5. Register the factory

Declare the factory as a Spring bean in your own configuration. The bean parameters are beans of the bridge.

<!-- docs-lint: allow-java-block: the example of the Spring registration reads clearer as code than as prose -->

```java
@Configuration
public class AcmeLangChain4JConfiguration {

  @Bean
  public AcmeChatModelFactory acmeChatModelFactory(
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ChatModelHttpProxySupport httpProxySupport) {
    return new AcmeChatModelFactory(
        chatMessageConverter, toolSpecificationConverter, jsonSchemaConverter, httpProxySupport);
  }
}
```

`ChatModelRegistryImpl` collects every `ChatModelFactory` bean and sends a request to the one whose `supports` returns
`true`. A request fails when no factory or more than one factory matches.

## 6. Optional: carry provider data through the tool calls

Some providers return data that the next request must repeat verbatim, such as the thought signatures of Gemini 3.
LangChain4j returns this data as attributes of the `AiMessage` and expects it again on the replayed message. Pass a
`ToolCallMetadataCodec` to the constructor of `LangChain4JChatModel` to persist it on the tool call:

- The writer receives the ID of one tool call and the flat `AiMessage` attributes, and returns the data to persist for
  that call.
- The reader receives the data that you persisted for that call and returns the attributes to put back on the replayed
  `AiMessage`. A message with parallel tool calls has one attribute map, and the connector merges the entries of all its
  calls into it. Make each key unique per tool call, for example by including the tool call ID, as the example below
  does.

The connector stores the result under the provider ID of the codec (`acme` here), and passes only your own entry to the
reader. The constructor without a codec carries nothing.

<!-- docs-lint: allow-java-block: the example of the codec reads clearer as code than as prose -->

```java
final var codec =
    new ToolCallMetadataCodec(
        "acme",
        (toolCallId, attributes) ->
            attributes.get("signature_" + toolCallId) instanceof String signature
                ? Map.of("signature", signature)
                : Map.of(),
        (toolCallId, providerMetadata) ->
            providerMetadata.get("signature") instanceof String signature
                ? Map.of("signature_" + toolCallId, signature)
                : Map.of());

return new LangChain4JChatModel(
    closeableModel,
    chatMessageConverter,
    toolSpecificationConverter,
    jsonSchemaConverter,
    codec,
    LangChain4JChatModel::mapTokenUsage);
```

## What the connector does for you

- It converts the conversation, the tool definitions and the response format between the domain model and LangChain4j
  (see the [converter chain](langchain4j-chat-model.md#converter-chain-architecture)).
- It measures the call and maps the token usage. To add the cache or reasoning token detail of your provider, use the
  constructor of `LangChain4JChatModel` with your own mapper, which can start from `baseTokenUsageBuilder`.
- It returns `ChatResult.Completed` for every call, because LangChain4j has no pause semantics.
- It runs the tool calls through the process. LangChain4j never executes a tool.
