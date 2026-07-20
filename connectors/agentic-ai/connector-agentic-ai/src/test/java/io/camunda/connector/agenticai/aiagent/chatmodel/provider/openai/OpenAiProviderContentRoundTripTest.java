/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.ObjectMappers;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseOutputItem;
import io.camunda.connector.agenticai.aiagent.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.capabilities.ModelCapabilities.Modality;
import io.camunda.connector.agenticai.aiagent.chatmodel.ChatModelResult;
import io.camunda.connector.agenticai.aiagent.chatmodel.V2ChatModelApiConfiguration;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesRequestConverter;
import io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai.family.responses.OpenAiResponsesResponseConverter;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentExecutionContext;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import io.camunda.connector.agenticai.aiagent.model.message.content.ProviderContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ReasoningContent;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.SystemPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.PromptConfiguration.UserPromptConfiguration;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiApiFamily;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiBackend.OpenAiDirectBackend;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiConnection;
import io.camunda.connector.agenticai.aiagent.model.request.v2.OpenAiChatModel.OpenAiModel;
import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Captured-fixture round-trip witnesses: the data-model validation goal of the OpenAI Responses
 * provider is that real provider-specific payloads (encrypted reasoning content, server-tool
 * blocks) survive a full round-trip through the neutral {@link Content} model unchanged.
 *
 * <p>Each test here takes a minimal-but-realistic {@code Response} fixture (mirroring the ones in
 * {@code OpenAiResponsesResponseConverterTest}), runs it through {@link
 * OpenAiResponsesResponseConverter#toResult(Response, Duration)} to obtain the neutral {@link
 * ReasoningContent} / {@link ProviderContent}, then replays that neutral content back through
 * {@link OpenAiResponsesRequestConverter#toResponseCreateParams} -- the same production path used
 * on the next turn of a real conversation -- and asserts that the resulting request-side {@link
 * ResponseInputItem} is semantically identical (as a {@link JsonNode} tree, order-insensitive) to
 * the original response-side {@link ResponseOutputItem}.
 */
class OpenAiProviderContentRoundTripTest {

  private static final String FIXTURE_BASE_PATH = "/openai/fixtures/";

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final OpenAiContentConverter contentConverter = new OpenAiContentConverter(objectMapper);
  private final OpenAiResponsesResponseConverter responseConverter =
      new OpenAiResponsesResponseConverter(objectMapper);
  private final OpenAiResponsesRequestConverter requestConverter =
      new OpenAiResponsesRequestConverter(contentConverter, objectMapper);

  @Test
  void reasoningContentRoundTripsByteIdentically() {
    final Response response = responseFromFixture("responses-reasoning.json");
    final ResponseOutputItem originalItem = response.output().get(0);

    final ChatModelResult result = responseConverter.toResult(response, Duration.ofMillis(1));
    final List<Content> content = result.assistantMessage().content();
    assertThat(content).hasSize(1);

    final ReasoningContent reasoningContent = (ReasoningContent) content.get(0);

    @SuppressWarnings("unchecked")
    final Map<String, Object> providerPayload =
        (Map<String, Object>) reasoningContent.providerPayload();
    assertThat(providerPayload)
        .as("providerPayload equals the source reasoning output item")
        .isEqualTo(originalItemAsMap(originalItem));

    final ResponseInputItem replayedInputItem = replayAssistantContent(reasoningContent);

    assertThat(asJsonNode(replayedInputItem))
        .as("replayed reasoning input item is byte-identical to the source output item")
        .isEqualTo(asJsonNode(originalItem));
  }

  @Test
  void codeInterpreterCallRoundTripsByteIdentically() {
    final Response response = responseFromFixture("responses-code-interpreter.json");
    final ResponseOutputItem originalItem = response.output().get(0);

    final ChatModelResult result = responseConverter.toResult(response, Duration.ofMillis(1));
    final List<Content> content = result.assistantMessage().content();
    assertThat(content).hasSize(1);

    final ProviderContent providerContent = (ProviderContent) content.get(0);
    assertThat(providerContent.provider()).isEqualTo("openai");
    assertThat(providerContent.blockType()).isEqualTo("code_interpreter_call");

    @SuppressWarnings("unchecked")
    final Map<String, Object> payload = (Map<String, Object>) providerContent.payload();
    assertThat(payload)
        .as("payload equals the source code_interpreter_call output item")
        .isEqualTo(originalItemAsMap(originalItem));

    final ResponseInputItem replayedInputItem = replayAssistantContent(providerContent);

    assertThat(asJsonNode(replayedInputItem))
        .as("replayed code_interpreter_call input item is byte-identical to the source output item")
        .isEqualTo(asJsonNode(originalItem));
  }

  @Test
  void webSearchCallRoundTripsByteIdentically() {
    final Response response = responseFromFixture("responses-web-search.json");
    final ResponseOutputItem originalItem = response.output().get(0);

    final ChatModelResult result = responseConverter.toResult(response, Duration.ofMillis(1));
    final List<Content> content = result.assistantMessage().content();
    assertThat(content).hasSize(1);

    final ProviderContent providerContent = (ProviderContent) content.get(0);
    assertThat(providerContent.provider()).isEqualTo("openai");
    assertThat(providerContent.blockType()).isEqualTo("web_search_call");

    @SuppressWarnings("unchecked")
    final Map<String, Object> payload = (Map<String, Object>) providerContent.payload();
    assertThat(payload)
        .as("payload equals the source web_search_call output item")
        .isEqualTo(originalItemAsMap(originalItem));

    final ResponseInputItem replayedInputItem = replayAssistantContent(providerContent);

    assertThat(asJsonNode(replayedInputItem))
        .as("replayed web_search_call input item is byte-identical to the source output item")
        .isEqualTo(asJsonNode(originalItem));
  }

  /**
   * Feeds a single assistant content item back through the real {@link
   * OpenAiResponsesRequestConverter#toResponseCreateParams} replay path, mirroring what happens on
   * the next conversation turn, and returns the single resulting input item.
   */
  private ResponseInputItem replayAssistantContent(Content content) {
    final var snapshot =
        new ConversationSnapshot(
            List.of(AssistantMessage.builder().content(List.of(content)).build()), List.of());

    final var params =
        requestConverter.toResponseCreateParams(executionContext(), snapshot, capabilities());

    final var items = params.input().orElseThrow().asResponse();
    assertThat(items).hasSize(1);
    return items.get(0);
  }

  private static AgentExecutionContext executionContext() {
    final OpenAiChatModel model =
        new OpenAiChatModel(
            new OpenAiConnection(
                OpenAiApiFamily.RESPONSES,
                new OpenAiDirectBackend("sk-test", null, null),
                new OpenAiModel("gpt-5", null),
                null,
                null,
                null,
                null));
    final var configuration =
        new AgentConfiguration(
            new V2ChatModelApiConfiguration(model),
            model.model(),
            model.providerType(),
            new SystemPromptConfiguration("system prompt"),
            new UserPromptConfiguration("user prompt", null),
            null,
            null,
            null,
            null);

    final AgentExecutionContext executionContext = Mockito.mock(AgentExecutionContext.class);
    Mockito.when(executionContext.configuration()).thenReturn(configuration);
    return executionContext;
  }

  private static OpenAiModelCapabilities capabilities() {
    return new OpenAiModelCapabilities(
        new CoreModelCapabilities(
            List.of(Modality.TEXT), List.of(Modality.TEXT), List.of(Modality.TEXT), null, null),
        null);
  }

  private static Response responseFromFixture(String fileName) {
    try (InputStream stream =
        OpenAiProviderContentRoundTripTest.class.getResourceAsStream(
            FIXTURE_BASE_PATH + fileName)) {
      assertThat(stream).as("fixture resource %s", fileName).isNotNull();
      return ObjectMappers.jsonMapper().readValue(stream, Response.class);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to read/parse test fixture " + fileName, e);
    }
  }

  /**
   * Mirrors {@code OpenAiResponsesResponseConverter#toRawMap}: uses the SDK's own {@link
   * ObjectMappers#jsonMapper()} rather than the plain injected {@code objectMapper}, since only it
   * knows how to serialize the raw item's {@code JsonValue}/{@code JsonField} internals faithfully
   * (omitting genuinely-absent optional fields instead of materializing them as explicit {@code
   * null}, and not leaking the Kotlin-generated {@code isValid()} property as a spurious {@code
   * valid} key).
   */
  private Map<String, Object> originalItemAsMap(ResponseOutputItem originalItem) {
    return ObjectMappers.jsonMapper()
        .convertValue(originalItem, new TypeReference<Map<String, Object>>() {});
  }

  /**
   * Serializes an SDK response/input item with the SDK's own {@link ObjectMappers#jsonMapper()}
   * rather than the plain injected {@code objectMapper}: these Stainless-generated models rely on
   * SDK-specific handling (e.g. distinguishing an absent field from an explicit {@code null}, and
   * omitting synthetic Kotlin properties like {@code valid}) to serialize the same way the real
   * client does when sending a request. Using a plain mapper here would introduce serialization
   * differences that have nothing to do with whether the round-trip through the neutral model
   * actually preserved the payload.
   */
  private JsonNode asJsonNode(Object value) {
    return ObjectMappers.jsonMapper().valueToTree(value);
  }
}
