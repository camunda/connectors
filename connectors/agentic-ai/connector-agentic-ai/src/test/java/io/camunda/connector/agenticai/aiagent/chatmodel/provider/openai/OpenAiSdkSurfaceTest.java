/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.chatmodel.provider.openai;

import static org.assertj.core.api.Assertions.assertThat;

import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.ObjectMappers;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.helpers.ResponseAccumulator;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionChunk;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionStreamOptions;
import com.openai.models.responses.Response;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFunctionCallOutputItem;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseInputItem;
import com.openai.models.responses.ResponseInputTextContent;
import com.openai.models.responses.ResponseStreamEvent;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/**
 * Pins the OpenAI SDK ({@code com.openai:openai-java}, version 4.50.0) surface every later task of
 * the native OpenAI provider relies on. This test makes no network calls — it only builds
 * params/accumulators so the compiler validates the vendor method names against the resolved SDK
 * version. If a future SDK bump renames or removes one of these methods, this test fails to compile
 * rather than a converter deep in the provider implementation.
 */
class OpenAiSdkSurfaceTest {

  @Test
  void okHttpClientExposesResponsesAndChatCompletionServices() {
    OpenAIClient client = OpenAIOkHttpClient.builder().apiKey("test-key").build();

    assertThat(client).isNotNull();
    assertThat(client.responses()).isNotNull();
    assertThat(client.chat().completions()).isNotNull();
  }

  @Test
  void responsesCreateParamsExposeTheBuildersWeDependOn() {
    ResponseCreateParams params =
        ResponseCreateParams.builder()
            .model("gpt-5.5")
            .input(ResponseCreateParams.Input.ofResponse(List.of()))
            .store(false)
            .addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT)
            .reasoning(Reasoning.builder().effort(ReasoningEffort.HIGH).build())
            .maxOutputTokens(1024L)
            .temperature(0.5)
            .topP(0.9)
            .build();

    assertThat(params.store()).contains(false);
    assertThat(params._body()).isNotNull();
  }

  @Test
  void functionCallOutputCanCarryNativeFileAndImageItems() {
    ResponseInputItem.FunctionCallOutput output =
        ResponseInputItem.FunctionCallOutput.builder()
            .callId("call_1")
            .output(
                ResponseInputItem.FunctionCallOutput.Output.ofResponseFunctionCallOutputItemList(
                    List.of(
                        ResponseFunctionCallOutputItem.ofInputText(
                            ResponseInputTextContent.builder().text("hello").build()))))
            .build();

    assertThat(output.callId()).isEqualTo("call_1");
  }

  @Test
  void completionsCreateParamsExposeStreamOptionsAndReasoningEffort() {
    ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder()
            .model("gpt-5.5")
            .messages(List.of())
            .streamOptions(ChatCompletionStreamOptions.builder().includeUsage(true).build())
            .reasoningEffort(ReasoningEffort.MEDIUM)
            .maxCompletionTokens(1024L)
            .build();

    assertThat(params.streamOptions()).isPresent();
  }

  @Test
  void responseAccumulatorExposesAccumulateAndResponse() {
    ResponseAccumulator accumulator = ResponseAccumulator.create();
    assertThat(accumulator).isNotNull();

    // accumulate(ResponseStreamEvent) returns the event itself (pass-through, meant to be used as
    // a Consumer via `stream.forEach(accumulator::accumulate)`), and response() returns a plain
    // Response (not an Optional) which is only safe to call once a completed event was
    // accumulated. Both are checked here purely at the type level, so this test documents the
    // signature without requiring a full, valid event stream.
    Function<ResponseStreamEvent, ResponseStreamEvent> accumulate = accumulator::accumulate;
    Supplier<Response> response = accumulator::response;

    assertThat(accumulate).isNotNull();
    assertThat(response).isNotNull();
  }

  @Test
  void chatCompletionAccumulatorExposesAccumulateAndChatCompletion() {
    ChatCompletionAccumulator accumulator = ChatCompletionAccumulator.create();
    assertThat(accumulator).isNotNull();

    // Same pass-through/non-Optional shape as ResponseAccumulator, checked at the type level only.
    Function<ChatCompletionChunk, ChatCompletionChunk> accumulate = accumulator::accumulate;
    Supplier<ChatCompletion> chatCompletion = accumulator::chatCompletion;

    assertThat(accumulate).isNotNull();
    assertThat(chatCompletion).isNotNull();
  }

  @Test
  void objectMappersExposesTheSdkJsonMapper() {
    assertThat(ObjectMappers.jsonMapper()).isNotNull();
  }
}
