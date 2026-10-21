# Custom LangChain4j provider

Earlier versions ran the v1 AI Agent providers (Anthropic, Azure OpenAI, Bedrock, Google Vertex AI, OpenAI and
OpenAI-compatible endpoints) on LangChain4j. Now every provider is native, and the LangChain4j factories for the vendors
are removed. A small bridge stays, so that you can run a model that LangChain4j supports and no native provider covers,
without rebuilding the conversion of messages, tools and documents. This page shows the steps with one example. For the
decision, see [ADR 019](../../docs/adr/019-langchain4j-extension-bridge.md). For what the bridge contains, see
[`langchain4j-chat-model.md`](langchain4j-chat-model.md).

A custom provider needs a connector runtime that you operate yourself, because the Camunda-managed SaaS runtime cannot
load your factory. That is a Self-Managed connector runtime, which can be attached to a SaaS cluster (hybrid mode). The
example runs the OpenAI chat model of LangChain4j against a provider with the type `acme`.

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

## 3. Write the factory

Extend `LangChain4JChatModelFactory` with the type of your configuration. A custom provider uses `CustomChatModelConfiguration`, the domain view of the template fields (`providerType()`, `model()`, `parameters()`). `supports` must match only your
configurations, because the registry needs exactly one match. `createChatModel` builds the LangChain4j model. The
connector creates one model per request and closes it afterwards, so return a model that owns the resources that it
opens.

<!-- docs-lint: allow-java-block: the example of a custom factory reads clearer as code than as prose -->

```java
public class AcmeChatModelFactory extends LangChain4JChatModelFactory<CustomChatModelConfiguration> {

  private final ChatModelHttpProxySupport httpProxySupport;

  public AcmeChatModelFactory(
      ChatMessageConverter chatMessageConverter,
      ToolSpecificationConverter toolSpecificationConverter,
      JsonSchemaConverter jsonSchemaConverter,
      ChatModelHttpProxySupport httpProxySupport) {
    super(
        CustomChatModelConfiguration.class,
        chatMessageConverter,
        toolSpecificationConverter,
        jsonSchemaConverter);
    this.httpProxySupport = httpProxySupport;
  }

  @Override
  public boolean supports(ChatModelConfiguration configuration) {
    return configuration instanceof CustomChatModelConfiguration custom
        && "acme".equals(custom.providerType());
  }

  @Override
  protected CloseableChatModel createChatModel(CustomChatModelConfiguration configuration) {
    final var parameters = configuration.parameters();

    // The connector proxy settings apply to the HTTP client. The builder closes the client.
    final var httpClientBuilder = httpProxySupport.createJdkHttpClientBuilder();
    httpClientBuilder.connectTimeout(Duration.ofSeconds(30)).readTimeout(Duration.ofMinutes(2));

    final var model =
        OpenAiChatModel.builder()
            .httpClientBuilder(httpClientBuilder)
            .baseUrl((String) parameters.get("baseUrl"))
            .apiKey((String) parameters.get("apiKey"))
            .modelName(configuration.model())
            .build();

    return new CloseableChatModelDelegate(model, httpClientBuilder);
  }
}
```

`CloseableChatModelDelegate` pairs the model with the resource that it closes. If your model owns no resource, return a
`CloseableChatModel` that implements `close()` as a no-op.

## 4. Register the factory

Declare the factory as a Spring bean in your own configuration. The LangChain4j converters of the connector load only
when a `LangChain4JChatModelFactory` bean exists, so a factory from a library auto-configuration must be ordered before
`AgenticAiConnectorsAutoConfiguration`. The bean parameters are beans of the connector.

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

## 5. Optional: carry provider data through the tool calls

Some providers return data that the next request must repeat verbatim, such as the thought signatures of Gemini 3.
LangChain4j returns this data as attributes of the `AiMessage` and expects it again on the replayed message. Override
the two hooks of the factory to persist it on the tool call:

- `writeToolCallMetadata` receives the flat `AiMessage` attributes and the ID of one tool call, and returns the data to
  persist for that call.
- `readToolCallMetadata` receives the data that you persisted for that call and returns the attributes to put back on
  the replayed `AiMessage`. A message with parallel tool calls has one attribute map, and the connector merges the
  entries of all its calls into it. Make each key unique per tool call, for example by including the tool call ID, as
  the example below does.

The connector stores the result under the provider ID of the configuration (`acme` here), and passes only your own
entry to `readToolCallMetadata`. The default of both hooks carries nothing.

<!-- docs-lint: allow-java-block: the example of the metadata hooks reads clearer as code than as prose -->

```java
@Override
protected Map<String, Object> writeToolCallMetadata(
    CustomChatModelConfiguration configuration,
    String toolCallId,
    Map<String, Object> aiMessageAttributes) {
  return aiMessageAttributes.get("signature_" + toolCallId) instanceof String signature
      ? Map.of("signature", signature)
      : Map.of();
}

@Override
protected Map<String, Object> readToolCallMetadata(
    CustomChatModelConfiguration configuration,
    String toolCallId,
    Map<?, ?> providerMetadata) {
  return providerMetadata.get("signature") instanceof String signature
      ? Map.of("signature_" + toolCallId, signature)
      : Map.of();
}
```

## What the connector does for you

- It converts the conversation, the tool definitions and the response format between the domain model and LangChain4j
  (see the [converter chain](langchain4j-chat-model.md#converter-chain-architecture)).
- It measures the call and maps the token usage. Override `mapTokenUsage` to add the cache or reasoning token detail of
  your provider.
- It returns `ChatResult.Completed` for every call, because LangChain4j has no pause semantics.
- It runs the tool calls through the process. LangChain4j never executes a tool.
