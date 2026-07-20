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
import com.openai.core.JsonValue;
import com.openai.helpers.ChatCompletionAccumulator;
import com.openai.helpers.ResponseAccumulator;
import com.openai.models.FunctionDefinition;
import com.openai.models.Reasoning;
import com.openai.models.ReasoningEffort;
import com.openai.models.ResponseFormatJsonSchema;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.responses.FunctionTool;
import com.openai.models.responses.ResponseCreateParams;
import com.openai.models.responses.ResponseFormatTextJsonSchemaConfig;
import com.openai.models.responses.ResponseIncludable;
import com.openai.models.responses.ResponseTextConfig;
import com.openai.models.responses.Tool;
import com.openai.models.responses.WebSearchTool;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pins the OpenAI SDK ({@code com.openai:openai-java}) surface every later task of the native
 * OpenAI provider relies on. Makes no network calls — it only builds params/clients/accumulators so
 * the compiler validates the vendor method names against the resolved SDK version.
 *
 * <p>SDK-SURFACE (CONFIRMED against openai-java 4.43.0 via javap + this compiling test):
 *
 * <pre>
 *   client build:            OpenAIOkHttpClient.builder()
 *                              .apiKey(String) .baseUrl(String) .organization(String) .project(String)
 *                              .credential(com.openai.credential.Credential)   // e.g. Azure
 *                              .putHeader(String,String) .proxy(java.net.Proxy)
 *                              .proxyAuthenticator(com.openai.core.http.ProxyAuthenticator)
 *                              .fromEnv() .build()  -> com.openai.client.OpenAIClient
 *   services:                client.responses()  -> com.openai.services.blocking.ResponseService
 *                            client.chat().completions() -> ...chat.ChatCompletionService
 *   responses stream:        responseService.createStreaming(ResponseCreateParams)
 *                              -> StreamResponse<com.openai.models.responses.ResponseStreamEvent>
 *   responses accumulate:    ResponseAccumulator.create();  acc.accumulate(ResponseStreamEvent);  acc.response() -> Response
 *   completions stream:      chatCompletionService.createStreaming(ChatCompletionCreateParams)
 *                              -> StreamResponse<com.openai.models.chat.completions.ChatCompletionChunk>
 *   completions accumulate:  ChatCompletionAccumulator.create();  acc.accumulate(ChatCompletionChunk);  acc.chatCompletion() -> ChatCompletion
 *   reasoning:               Reasoning.builder().effort(ReasoningEffort.HIGH).build();  responseParams.reasoning(Reasoning)
 *   effort enum values:      ReasoningEffort.{NONE, MINIMAL, LOW, MEDIUM, HIGH, XHIGH, MAX}  (our config maps MINIMAL/LOW/MEDIUM/HIGH)
 *   include encrypted:       responseParams.addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT);  responseParams.store(false)
 *                            (other includables: WEB_SEARCH_CALL_RESULTS, CODE_INTERPRETER_CALL_OUTPUTS, ...)
 *   function tool:           FunctionTool.builder().name(String).parameters(FunctionTool.Parameters).strict(bool).description(String).build()
 *                            FunctionTool.Parameters.builder().putAdditionalProperty(String, JsonValue).build()   // raw JSON-schema map
 *                            Tool.ofFunction(FunctionTool)   // wrap into responses Tool union
 *   web_search tool:         Tool.ofWebSearch( WebSearchTool.builder().type(WebSearchTool.Type.WEB_SEARCH).build() )
 *   code_interpreter tool:   Tool.ofCodeInterpreter( Tool.CodeInterpreter.builder()
 *                              .container(Tool.CodeInterpreter.Container.ofCodeInterpreterToolAuto(
 *                                  Tool.CodeInterpreter.Container.CodeInterpreterToolAuto.builder().build()))
 *                              .build() )
 *   tools on request:        responseParams.tools(List<Tool>) / .addTool(Tool) ;  completionsParams.addTool(ChatCompletionTool)
 *   structured output (resp):responseParams.text( ResponseTextConfig.builder().format(ResponseFormatTextJsonSchemaConfig).build() )
 *                            ResponseFormatTextJsonSchemaConfig.builder().name(String)
 *                              .schema(ResponseFormatTextJsonSchemaConfig.Schema.builder().putAdditionalProperty(..).build()).strict(bool).build()
 *   structured output (chat):completionsParams.responseFormat( ResponseFormatJsonSchema.builder().jsonSchema(
 *                              ResponseFormatJsonSchema.JsonSchema.builder().name(String).schema(..).build()).build() )
 *   completions tool:        ChatCompletionTool.ofFunction( ChatCompletionFunctionTool.builder().function(FunctionDefinition).build() )
 *                            FunctionDefinition.builder().name(String).parameters(FunctionParameters).build()
 *   completions messages:    completionsParams.addSystemMessage(String) / .addDeveloperMessage(String) / .addUserMessage(String)
 *   completions reasoning:   completionsParams.reasoningEffort(ReasoningEffort)  // SDK-supported, but the pilot gates reasoning to Responses only
 *   response read-side:      Response.id() : String ;  Response.output() : List<ResponseOutputItem> ;  Response.usage() : Optional<ResponseUsage>
 *   additional properties:   *.Builder.putAdditionalProperty(String, com.openai.core.JsonValue) on every model builder (escape hatch)
 * </pre>
 */
class OpenAiSdkSurfaceTest {

  @Test
  void buildsClient() {
    OpenAIClient client = OpenAIOkHttpClient.builder().apiKey("test-key").build();
    assertThat(client).isNotNull();
    assertThat(client.responses()).isNotNull();
    assertThat(client.chat().completions()).isNotNull();
  }

  @Test
  void buildsResponsesParamsWithReasoningAndTools() {
    final FunctionTool functionTool =
        FunctionTool.builder()
            .name("get_weather")
            .description("Get the weather for a city")
            .parameters(
                FunctionTool.Parameters.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .putAdditionalProperty("properties", JsonValue.from(java.util.Map.of()))
                    .build())
            .strict(true)
            .build();

    final WebSearchTool webSearchTool =
        WebSearchTool.builder().type(WebSearchTool.Type.WEB_SEARCH).build();

    final Tool.CodeInterpreter codeInterpreterTool =
        Tool.CodeInterpreter.builder()
            .container(
                Tool.CodeInterpreter.Container.ofCodeInterpreterToolAuto(
                    Tool.CodeInterpreter.Container.CodeInterpreterToolAuto.builder().build()))
            .build();

    final ResponseFormatTextJsonSchemaConfig jsonSchema =
        ResponseFormatTextJsonSchemaConfig.builder()
            .name("result")
            .schema(
                ResponseFormatTextJsonSchemaConfig.Schema.builder()
                    .putAdditionalProperty("type", JsonValue.from("object"))
                    .build())
            .strict(true)
            .build();

    final ResponseCreateParams params =
        ResponseCreateParams.builder()
            .model("gpt-5")
            .input("What is the weather in Berlin?")
            .instructions("You are a helpful assistant.")
            .reasoning(Reasoning.builder().effort(ReasoningEffort.HIGH).build())
            .addInclude(ResponseIncludable.REASONING_ENCRYPTED_CONTENT)
            .store(false)
            .tools(
                List.of(
                    Tool.ofFunction(functionTool),
                    Tool.ofWebSearch(webSearchTool),
                    Tool.ofCodeInterpreter(codeInterpreterTool)))
            .text(ResponseTextConfig.builder().format(jsonSchema).build())
            .maxOutputTokens(1024)
            .build();

    assertThat(params).isNotNull();
    assertThat(params.tools()).hasValueSatisfying(tools -> assertThat(tools).hasSize(3));
  }

  @Test
  void buildsCompletionsParamsWithToolAndResponseFormat() {
    final ChatCompletionTool functionTool =
        ChatCompletionTool.ofFunction(
            ChatCompletionFunctionTool.builder()
                .function(
                    FunctionDefinition.builder()
                        .name("get_weather")
                        .description("Get the weather for a city")
                        .build())
                .build());

    final ResponseFormatJsonSchema responseFormat =
        ResponseFormatJsonSchema.builder()
            .jsonSchema(
                ResponseFormatJsonSchema.JsonSchema.builder()
                    .name("result")
                    .schema(
                        ResponseFormatJsonSchema.JsonSchema.Schema.builder()
                            .putAdditionalProperty("type", JsonValue.from("object"))
                            .build())
                    .build())
            .build();

    final ChatCompletionCreateParams params =
        ChatCompletionCreateParams.builder()
            .model("gpt-4o")
            .addSystemMessage("You are a helpful assistant.")
            .addUserMessage("What is the weather in Berlin?")
            .addTool(functionTool)
            .responseFormat(responseFormat)
            .maxCompletionTokens(1024)
            .build();

    assertThat(params).isNotNull();
    assertThat(params.tools()).hasValueSatisfying(tools -> assertThat(tools).hasSize(1));
  }

  @Test
  void accumulatorsExist() {
    assertThat(ResponseAccumulator.create()).isNotNull();
    assertThat(ChatCompletionAccumulator.create()).isNotNull();
  }
}
