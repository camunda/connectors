# Custom Model Provider Implementation Guide

This document describes how to implement and register a custom model provider for the Agentic AI connector.

## Overview

The custom model provider support allows users to integrate their own LLM implementations in self-managed and hybrid setups. This follows the same pattern as custom memory storage.

## Architecture

The implementation uses a registry pattern with the following components:

- **`ChatModelProvider`**: Interface for creating ChatModel instances from provider configurations
- **`ChatModelProviderRegistry`**: Registry for managing provider implementations
- **`ChatModelFactoryImpl`**: Factory that delegates to the registry
- **`CustomProviderConfiguration`**: Configuration model for custom providers

## Using a Custom Provider

### 1. In the Connector Configuration

Select "Custom Implementation (Self-Managed/Hybrid only)" as the provider type and configure:

- **Implementation type**: The type identifier for your custom provider (e.g., "my-custom-llm")
- **Parameters**: A map of custom parameters that your implementation will use

Example JSON configuration:
```json
{
  "provider": {
    "type": "custom",
    "providerType": "my-custom-llm",
    "parameters": {
      "endpoint": "https://my-llm-api.example.com",
      "apiKey": "secrets.MY_LLM_API_KEY",
      "model": "custom-model-v1"
    }
  }
}
```

### 2. Implementing a Custom Provider

Create a class that implements `ChatModelProvider`:

```java
package com.example;

import dev.langchain4j.model.chat.ChatModel;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.provider.ChatModelProvider;
import io.camunda.connector.agenticai.aiagent.model.request.provider.CustomProviderConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.provider.ProviderConfiguration;
import org.springframework.stereotype.Component;

@Component
public class MyCustomChatModelProvider implements ChatModelProvider {

  @Override
  public String getProviderType() {
    return "my-custom-llm";
  }

  @Override
  public boolean supports(ProviderConfiguration providerConfiguration) {
    if (!(providerConfiguration instanceof CustomProviderConfiguration custom)) {
      return false;
    }
    return "my-custom-llm".equals(custom.providerType());
  }

  @Override
  public ChatModel createChatModel(ProviderConfiguration providerConfiguration) {
    if (!(providerConfiguration instanceof CustomProviderConfiguration custom)) {
      throw new IllegalArgumentException("Expected CustomProviderConfiguration");
    }

    // Extract parameters from the configuration
    var parameters = custom.parameters();
    var endpoint = (String) parameters.get("endpoint");
    var apiKey = (String) parameters.get("apiKey");
    var model = (String) parameters.get("model");

    // Create and return your custom ChatModel implementation
    return MyCustomChatModel.builder()
        .endpoint(endpoint)
        .apiKey(apiKey)
        .modelName(model)
        .build();
  }
}
```

### 3. Registering the Provider

The provider will be automatically registered when:

1. It's a Spring bean (annotated with `@Component`, `@Service`, etc.)
2. It implements `ChatModelProvider`
3. It's on the classpath of the connector runtime

The Spring configuration will automatically pick up all `ChatModelProvider` beans and register them with the `ChatModelProviderRegistry`.

## Overriding Built-in Providers

You can override the behavior of built-in providers by creating a bean with the same type:

```java
@Configuration
public class CustomProviderConfiguration {

  @Bean
  @Primary
  public OpenAiChatModelProvider customOpenAiProvider(
      AgenticAiConnectorsConfigurationProperties properties) {
    return new MyCustomOpenAiChatModelProvider(properties);
  }
}
```

## Built-in Providers

The following providers are available out of the box:

- **Anthropic** (`anthropic`) - `AnthropicChatModelProvider`
- **Azure OpenAI** (`azureOpenAi`) - `AzureOpenAiChatModelProvider`
- **AWS Bedrock** (`bedrock`) - `BedrockChatModelProvider`
- **Google Vertex AI** (`google-vertex-ai`) - `GoogleVertexAiChatModelProvider`
- **OpenAI** (`openai`) - `OpenAiChatModelProvider`
- **OpenAI Compatible** (`openaiCompatible`) - `OpenAiCompatibleChatModelProvider`

Each of these can be customized using the `@ConditionalOnMissingBean` annotation on their bean definitions.

## Implementation Notes

- Custom providers must implement the LangChain4J `ChatModel` interface
- The `providerType` in `CustomProviderConfiguration` must match the value returned by `getProviderType()`
- Parameters can be any JSON-serializable values (strings, numbers, maps, lists)
- Secret references (e.g., "secrets.MY_API_KEY") will be resolved by the connector runtime before being passed to the provider

## Testing

Example test for a custom provider:

```java
@ExtendWith(MockitoExtension.class)
class MyCustomChatModelProviderTest {

  @Test
  void shouldCreateChatModel() {
    // given
    var provider = new MyCustomChatModelProvider();
    var config = new CustomProviderConfiguration(
        "my-custom-llm",
        Map.of(
            "endpoint", "https://api.example.com",
            "apiKey", "test-key",
            "model", "test-model"));

    // when
    var chatModel = provider.createChatModel(config);

    // then
    assertThat(chatModel).isNotNull();
    // Add assertions specific to your implementation
  }
}
```
