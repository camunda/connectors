/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.e2e.agenticai;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static io.camunda.connector.agenticai.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.Content;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.ImageContent.DetailLevel;
import dev.langchain4j.data.message.PdfFileContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.data.pdf.PdfFile;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.ChatResponseMetadata;
import dev.langchain4j.model.output.FinishReason;
import dev.langchain4j.model.output.TokenUsage;
import io.camunda.client.api.search.response.Incident;
import io.camunda.client.api.worker.JobWorker;
import io.camunda.connector.agenticai.adhoctoolsschema.resolver.AdHocToolsSchemaResolver;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.ChatModelFactory;
import io.camunda.connector.agenticai.aiagent.framework.langchain4j.document.DocumentToContentResponseModel;
import io.camunda.connector.agenticai.aiagent.memory.InProcessConversationContext;
import io.camunda.connector.agenticai.aiagent.model.AgentMetrics;
import io.camunda.connector.agenticai.aiagent.model.AgentResponse;
import io.camunda.connector.agenticai.aiagent.model.AgentState;
import io.camunda.connector.agenticai.model.message.AssistantMessage;
import io.camunda.connector.e2e.ZeebeTest;
import io.camunda.connector.e2e.agenticai.assertj.ToolExecutionRequestEqualsPredicate;
import io.camunda.connector.test.SlowTest;
import io.camunda.process.test.impl.assertions.CamundaDataSource;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import org.assertj.core.api.ThrowingConsumer;
import org.assertj.core.api.recursive.comparison.RecursiveComparisonConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SlowTest
public class Langchain4JAiAgentTests extends BaseAgenticAiTest {

  @RegisterExtension
  static WireMockExtension wm =
      WireMockExtension.newInstance().options(wireMockConfig().dynamicPort()).build();

  @Autowired private ResourceLoader resourceLoader;

  @MockitoBean private ChatModelFactory chatModelFactory;
  @Mock private ChatModel chatModel;
  @Captor private ArgumentCaptor<ChatRequest> chatRequestCaptor;

  @MockitoSpyBean private AdHocToolsSchemaResolver schemaResolver;

  private JobWorker jobWorker;
  private final AtomicInteger jobWorkerCounter = new AtomicInteger(0);
  private final AtomicReference<Map<String, Object>> userFeedbackVariables =
      new AtomicReference<>(Collections.emptyMap());

  @BeforeEach
  void setUp() {
    when(chatModelFactory.createChatModel(any())).thenReturn(chatModel);
    openUserFeedbackJobWorker();

    // WireMock returns the content type for the YAML file as application/json, so
    // we need to override the stub manually
    wm.stubFor(
        get(urlPathEqualTo("/test.yaml"))
            .willReturn(
                aResponse()
                    .withBodyFile("test.yaml")
                    .withHeader("Content-Type", "application/yaml")));
  }

  private void openUserFeedbackJobWorker() {
    userFeedbackVariables.set(Collections.emptyMap());
    jobWorkerCounter.set(0);
    jobWorker =
        camundaClient
            .newWorker()
            .jobType("user_feedback")
            .handler(
                (client, job) -> {
                  jobWorkerCounter.incrementAndGet();
                  client
                      .newCompleteCommand(job.getKey())
                      .variables(userFeedbackVariables.get())
                      .send()
                      .join();
                })
            .open();
  }

  @AfterEach
  void tearDown() {
    if (jobWorker != null) {
      jobWorker.close();
    }
  }

  @Nested
  class BasicFeedbackLoop {

    @Test
    void executesAgentWithoutUserFeedback() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(m -> m, true);
    }

    @Test
    void basicExecutionWorksWithoutOptionalConfiguration() throws Exception {
      testBasicExecutionWithoutFeedbackLoop(
          withoutInputsMatching(
              i ->
                  i.getTarget().startsWith("data.tools.")
                      || i.getTarget().startsWith("data.memory.")
                      || i.getTarget().startsWith("data.limits.")),
          false);

      verify(schemaResolver, never()).resolveSchema(any(), any());
    }

    @Nested
    class Response {
      @Test
      void fallsBackToResponseTextWhenConfigurationIsMissing() throws Exception {
        testBasicExecutionWithoutFeedbackLoop(
            withoutInputsMatching(i -> i.getTarget().startsWith("data.response.")),
            false,
            true,
            false);
      }

      @Test
      void returnsOnlyResponseTextIfConfigured() throws Exception {
        testBasicExecutionWithoutFeedbackLoop(
            withModifiedInputs(
                Map.ofEntries(
                    Map.entry("data.response.includeText", i -> i.setSource("=true")),
                    Map.entry(
                        "data.response.includeAssistantMessage", i -> i.setSource("=false")))),
            false,
            true,
            false);
      }

      @Test
      void returnsOnlyResponseMessageIfConfigured() throws Exception {
        testBasicExecutionWithoutFeedbackLoop(
            withModifiedInputs(
                Map.ofEntries(
                    Map.entry("data.response.includeText", i -> i.setSource("=false")),
                    Map.entry("data.response.includeAssistantMessage", i -> i.setSource("=true")))),
            false,
            false,
            true);
      }
    }

    private void testBasicExecutionWithoutFeedbackLoop(
        Function<BpmnModelInstance, BpmnModelInstance> modelModifier,
        boolean assertToolSpecifications)
        throws Exception {
      testBasicExecutionWithoutFeedbackLoop(modelModifier, assertToolSpecifications, true, true);
    }

    private void testBasicExecutionWithoutFeedbackLoop(
        Function<BpmnModelInstance, BpmnModelInstance> modelModifier,
        boolean assertToolSpecifications,
        boolean expectResponseText,
        boolean expectResponseMessage)
        throws Exception {
      final var initialUserPrompt = "Write a haiku about the sea";
      final var expectedConversation =
          List.of(
              new SystemMessage(
                  "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
              new UserMessage(initialUserPrompt),
              new AiMessage(
                  "Endless waves whisper | moonlight dances on the tide | secrets drift below."));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage(
                      new AiMessage(
                          "Endless waves whisper | moonlight dances on the tide | secrets drift below."))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(
                  modelModifier, Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
              .waitForProcessCompletion();

      assertLastChatRequest(1, expectedConversation, assertToolSpecifications);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(agentResponse, new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20)));

      final var expectedResponseText = ((AiMessage) expectedConversation.getLast()).text();
      if (expectResponseText) {
        assertAgentResponseText(agentResponse, expectedResponseText);
      } else {
        assertThat(agentResponse.responseText()).isNull();
      }

      if (expectResponseMessage) {
        assertAgentResponseMessage(agentResponse, expectedResponseText);
      } else {
        assertThat(agentResponse.responseMessage()).isNull();
      }

      assertThat(jobWorkerCounter.get()).isEqualTo(1);
    }

    @Test
    void executesAgentWithUserFeedback() throws Exception {
      final var initialUserPrompt = "Write a haiku about the sea";
      final var expectedConversation =
          List.of(
              new SystemMessage(
                  "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
              new UserMessage(initialUserPrompt),
              new AiMessage(
                  "Endless waves whisper | moonlight dances on the tide | secrets drift below."),
              new UserMessage("Add emojis!"),
              new AiMessage(
                  "Endless waves whisper \uD83C\uDF0A | moonlight dances on the tide \uD83C\uDF15 | secrets drift below \uD83C\uDF0C"));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage(
                      new AiMessage(
                          "Endless waves whisper | moonlight dances on the tide | secrets drift below."))
                  .build(),
              userFollowUpFeedback("Add emojis!")),
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(11, 22))
                          .build())
                  .aiMessage(
                      new AiMessage(
                          "Endless waves whisper \uD83C\uDF0A | moonlight dances on the tide \uD83C\uDF15 | secrets drift below \uD83C\uDF0C"))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
              .waitForProcessCompletion();

      assertLastChatRequest(2, expectedConversation);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(agentResponse, new AgentMetrics(2, new AgentMetrics.TokenUsage(21, 42)));
      assertAgentResponseTextAndMessage(
          agentResponse, ((AiMessage) expectedConversation.getLast()).text());

      assertThat(jobWorkerCounter.get()).isEqualTo(2);
    }
  }

  @Nested
  class ToolCalling {

    @Test
    void executesAgentWithToolCallingAndUserFeedback() throws Exception {
      final var initialUserPrompt = "Explore some of your tools!";
      final var expectedConversation =
          List.of(
              new SystemMessage(
                  "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
              new UserMessage(initialUserPrompt),
              new AiMessage(
                  "The user asked me to call some of my tools. I will call the superflux calculation and the task with a text input schema as they look interesting to me.",
                  List.of(
                      ToolExecutionRequest.builder()
                          .id("aaa111")
                          .name("SuperfluxProduct")
                          .arguments("{\"a\": 5, \"b\": 3}")
                          .build(),
                      ToolExecutionRequest.builder()
                          .id("bbb222")
                          .name("Search_The_Web")
                          .arguments("{\"searchQuery\": \"Where does this data come from?\"}")
                          .build())),
              new ToolExecutionResultMessage("aaa111", "SuperfluxProduct", "24"),
              new ToolExecutionResultMessage(
                  "bbb222", "Search_The_Web", "No results for 'Where does this data come from?'"),
              new AiMessage(
                  "I played with the tools and learned that the data comes from the follow-up task and that a superflux calculation of 5 and 3 results in 24."),
              new UserMessage("So what is a superflux calculation anyway?"),
              new AiMessage(
                  "A very complex calculation only the superflux calculation tool can do."));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage((AiMessage) expectedConversation.get(2))
                  .build()),
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(100, 200))
                          .build())
                  .aiMessage((AiMessage) expectedConversation.get(5))
                  .build(),
              userFollowUpFeedback("So what is a superflux calculation anyway?")),
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(11, 22))
                          .build())
                  .aiMessage((AiMessage) expectedConversation.get(7))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
              .waitForProcessCompletion();

      assertLastChatRequest(3, expectedConversation);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(
          agentResponse, new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)));
      assertAgentResponseTextAndMessage(
          agentResponse, ((AiMessage) expectedConversation.getLast()).text());

      assertThat(jobWorkerCounter.get()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
      "test.csv,text,text/csv",
      "test.gif,base64,image/gif",
      "test.jpg,base64,image/jpeg",
      "test.json,text,application/json",
      "test.pdf,base64,application/pdf",
      "test.png,base64,image/png",
      "test.txt,text,text/plain",
      "test.webp,base64,image/webp",
      "test.xml,text,application/xml",
      "test.yaml,text,application/yaml"
    })
    void supportsDocumentResponsesFromToolCalls(String filename, String type, String mimeType)
        throws Exception {
      DownloadFileToolResult expectedDownloadFileResult;
      if (type.equals("text")) {
        expectedDownloadFileResult =
            new DownloadFileToolResult(
                200,
                new DocumentToContentResponseModel(
                    type, mimeType, testFileContent(filename).get()));
      } else {
        expectedDownloadFileResult =
            new DownloadFileToolResult(
                200,
                new DocumentToContentResponseModel(
                    type, mimeType, testFileContentBase64(filename).get()));
      }

      final var initialUserPrompt = "Go and download a document!";
      final var expectedConversation =
          List.of(
              new SystemMessage(
                  "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
              new UserMessage(initialUserPrompt),
              new AiMessage(
                  "The user asked me to download a document. I will call the Download_A_File tool to do so.",
                  List.of(
                      ToolExecutionRequest.builder()
                          .id("aaa111")
                          .name("Download_A_File")
                          .arguments("{\"url\": \"%s\"}".formatted(wm.baseUrl() + "/" + filename))
                          .build())),
              new ToolExecutionResultMessage(
                  "aaa111",
                  "Download_A_File",
                  objectMapper.writeValueAsString(expectedDownloadFileResult)),
              new AiMessage(
                  "I loaded a document and learned that it contains interesting data. Anything specific you want to know?"),
              new UserMessage("What is the content type?"),
              new AiMessage("The content type is '%s'".formatted(mimeType)));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage((AiMessage) expectedConversation.get(2))
                  .build()),
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(100, 200))
                          .build())
                  .aiMessage((AiMessage) expectedConversation.get(4))
                  .build(),
              userFollowUpFeedback("What is the content type?")),
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(11, 22))
                          .build())
                  .aiMessage((AiMessage) expectedConversation.get(6))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(Map.of("action", "executeAgent", "userPrompt", initialUserPrompt))
              .waitForProcessCompletion();

      assertLastChatRequest(3, expectedConversation);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(
          agentResponse, new AgentMetrics(3, new AgentMetrics.TokenUsage(121, 242)));
      assertAgentResponseTextAndMessage(
          agentResponse, ((AiMessage) expectedConversation.getLast()).text());

      assertThat(jobWorkerCounter.get()).isEqualTo(2);
    }
  }

  @Nested
  class UserPromptDocuments {

    @ParameterizedTest
    @ValueSource(
        strings = {
          "test.csv",
          "test.gif",
          "test.jpg",
          "test.json",
          "test.pdf",
          "test.png",
          "test.txt",
          "test.webp",
          "test.xml",
          "test.yaml"
        })
    void handlesDocumentType(String filename) throws Exception {
      final var initialUserPrompt = "Summarize the following document";
      final var expectedConversation =
          List.of(
              new SystemMessage(
                  "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
              UserMessage.builder()
                  .addContent(new TextContent(initialUserPrompt))
                  .addContent(asContentBlock(filename))
                  .build(),
              new AiMessage("TL;DR: it is pretty interesting"));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage(new AiMessage("TL;DR: it is pretty interesting"))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(
                  Map.of(
                      "action",
                      "executeAgent",
                      "userPrompt",
                      initialUserPrompt,
                      "downloadUrls",
                      List.of(wm.baseUrl() + "/" + filename)))
              .waitForProcessCompletion();

      assertLastChatRequest(1, expectedConversation);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(agentResponse, new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20)));
      assertAgentResponseTextAndMessage(
          agentResponse, ((AiMessage) expectedConversation.getLast()).text());

      assertThat(jobWorkerCounter.get()).isEqualTo(1);
    }

    @Test
    void handlesMultipleDocuments() throws Exception {
      final var initialUserPrompt = "Summarize the following documents";
      final var expectedConversation =
          List.of(
              new SystemMessage(
                  "You are a helpful AI assistant. Answer all the questions, but always be nice. Explain your thinking."),
              UserMessage.builder()
                  .addContent(new TextContent(initialUserPrompt))
                  .addContent(asContentBlock("test.txt"))
                  .addContent(asContentBlock("test.jpg"))
                  .build(),
              new AiMessage("TL;DR: they contain a lot of interesting information."));

      mockChatInteractions(
          ChatInteraction.of(
              ChatResponse.builder()
                  .metadata(
                      ChatResponseMetadata.builder()
                          .finishReason(FinishReason.STOP)
                          .tokenUsage(new TokenUsage(10, 20))
                          .build())
                  .aiMessage(new AiMessage("TL;DR: they contain a lot of interesting information."))
                  .build(),
              userSatisfiedFeedback()));

      final var zeebeTest =
          createProcessInstance(
                  Map.of(
                      "action",
                      "executeAgent",
                      "userPrompt",
                      initialUserPrompt,
                      "downloadUrls",
                      List.of(wm.baseUrl() + "/test.txt", wm.baseUrl() + "/test.jpg")))
              .waitForProcessCompletion();

      assertLastChatRequest(1, expectedConversation);

      final var agentResponse = getAgentResponse(zeebeTest);
      assertAgentResponse(agentResponse, new AgentMetrics(1, new AgentMetrics.TokenUsage(10, 20)));
      assertAgentResponseTextAndMessage(
          agentResponse, ((AiMessage) expectedConversation.getLast()).text());

      assertThat(jobWorkerCounter.get()).isEqualTo(1);
    }

    @Test
    void raisesIncidentWhenDocumentTypeIsNotSupported() throws Exception {
      final var zeebeTest =
          createProcessInstance(
                  Map.of(
                      "action",
                      "executeAgent",
                      "userPrompt",
                      "Summarize the following document",
                      "downloadUrls",
                      List.of(wm.baseUrl() + "/unsupported.zip")))
              .waitForActiveIncidents();

      assertIncident(
          zeebeTest,
          incident -> {
            assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
            assertThat(incident.getErrorMessage())
                .startsWith(
                    "Unsupported content type 'application/zip' for document with reference");
          });
    }

    private Content asContentBlock(String filename) throws Exception {
      final Supplier<String> text = testFileContent(filename);
      final Supplier<String> b64 = testFileContentBase64(filename);

      return switch (filename) {
        case "test.txt", "test.yaml", "test.csv", "test.json", "test.xml" ->
            TextContent.from(text.get());
        case "test.pdf" -> PdfFileContent.from(PdfFile.builder().base64Data(b64.get()).build());
        case "test.gif" -> ImageContent.from(b64.get(), "image/gif", DetailLevel.AUTO);
        case "test.jpg" -> ImageContent.from(b64.get(), "image/jpeg", DetailLevel.AUTO);
        case "test.png" -> ImageContent.from(b64.get(), "image/png", DetailLevel.AUTO);
        case "test.webp" -> ImageContent.from(b64.get(), "image/webp", DetailLevel.AUTO);
        default -> throw new IllegalStateException("Unsupported file: " + filename);
      };
    }
  }

  @Nested
  class Limits {

    @Test
    void raisesIncidentWhenMaximumModelCallsAreReached() throws Throwable {
      // 9 = custom value set in the process definition
      testMaxModelCallsLoop(m -> m, 9);
    }

    @Test
    void fallsBackToADefaultMaxModelCallsLimitWhenNoLimitIsConfigured() throws Throwable {
      // 10 = default value
      testMaxModelCallsLoop(
          withoutInputsMatching(input -> input.getTarget().startsWith("data.limits.")), 10);
    }

    private void testMaxModelCallsLoop(
        Function<BpmnModelInstance, BpmnModelInstance> modelModifier, int expectedMaxModelCalls)
        throws Throwable {
      // infinite loop - always returning the same answer and handling the same user feedback
      doAnswer(
              invocationOnMock -> {
                userFeedbackVariables.set(userFollowUpFeedback("I don't like it"));
                return ChatResponse.builder()
                    .metadata(
                        ChatResponseMetadata.builder()
                            .finishReason(FinishReason.STOP)
                            .tokenUsage(new TokenUsage(10, 20))
                            .build())
                    .aiMessage(
                        new AiMessage(
                            "Endless waves whisper | moonlight dances on the tide | secrets drift below."))
                    .build();
              })
          .when(chatModel)
          .chat(chatRequestCaptor.capture());

      final var zeebeTest =
          createProcessInstance(
                  modelModifier,
                  Map.of("action", "executeAgent", "userPrompt", "Write a haiku about the sea"))
              .waitForActiveIncidents();

      assertIncident(
          zeebeTest,
          incident -> {
            assertThat(incident.getElementId()).isEqualTo(AI_AGENT_TASK_ID);
            assertThat(incident.getErrorMessage())
                .isEqualTo(
                    "Maximum number of model calls reached (modelCalls: %1$d, limit: %1$d)"
                        .formatted(expectedMaxModelCalls));
          });

      final var agentResponse = getAgentResponse(zeebeTest);
      assertThat(agentResponse.context().metrics().modelCalls()).isEqualTo(expectedMaxModelCalls);

      final var conversationMessages =
          ((InProcessConversationContext) agentResponse.context().conversation()).messages();
      assertThat(conversationMessages)
          .filteredOn(
              msg -> msg instanceof io.camunda.connector.agenticai.model.message.SystemMessage)
          .hasSize(1);
      assertThat(conversationMessages)
          .filteredOn(msg -> msg instanceof AssistantMessage)
          .hasSize(expectedMaxModelCalls);
      assertThat(conversationMessages)
          .filteredOn(
              msg -> msg instanceof io.camunda.connector.agenticai.model.message.UserMessage)
          .hasSize(expectedMaxModelCalls);
    }
  }

  private void mockChatInteractions(ChatInteraction... chatInteractions) {
    final var queue = new ArrayList<>(Arrays.asList(chatInteractions));
    doAnswer(
            invocationOnMock -> {
              final var interaction = queue.removeFirst();
              userFeedbackVariables.set(interaction.userFeedback());
              return interaction.chatResponse();
            })
        .when(chatModel)
        .chat(chatRequestCaptor.capture());
  }

  private void assertLastChatRequest(
      int expectedChatRequestCount, List<ChatMessage> expectedConversation) {
    assertLastChatRequest(expectedChatRequestCount, expectedConversation, true);
  }

  private void assertLastChatRequest(
      int expectedChatRequestCount,
      List<ChatMessage> expectedConversation,
      boolean assertToolSpecifications) {
    assertThat(chatRequestCaptor.getAllValues()).hasSize(expectedChatRequestCount);

    final var lastChatRequest = chatRequestCaptor.getValue();

    if (assertToolSpecifications) {
      assertToolSpecifications(lastChatRequest);
    }

    assertThat(lastChatRequest.messages())
        .as("The last chat request should contain all messages except the last response")
        .usingRecursiveFieldByFieldElementComparator(
            RecursiveComparisonConfiguration.builder()
                .withEqualsForType(
                    new ToolExecutionRequestEqualsPredicate(), ToolExecutionRequest.class)
                .build())
        .containsExactlyElementsOf(
            expectedConversation.subList(0, expectedConversation.size() - 1));
  }

  private void assertAgentResponse(AgentResponse agentResponse, AgentMetrics expectedMetrics) {
    assertThat(agentResponse).isNotNull();
    assertThat(agentResponse.toolCalls()).isEmpty();
    assertThat(agentResponse.context().state()).isEqualTo(AgentState.READY);
    assertThat(agentResponse.context().metrics()).isEqualTo(expectedMetrics);
  }

  private void assertAgentResponseTextAndMessage(
      AgentResponse agentResponse, String expectedResponseText) {
    assertAgentResponseText(agentResponse, expectedResponseText);
    assertAgentResponseMessage(agentResponse, expectedResponseText);
  }

  private void assertAgentResponseText(AgentResponse agentResponse, String expectedResponseText) {
    assertThat(agentResponse.responseText()).isEqualTo(expectedResponseText);
  }

  private void assertAgentResponseMessage(
      AgentResponse agentResponse, String expectedResponseText) {
    assertThat(agentResponse.responseMessage().content())
        .hasSize(1)
        .containsExactly(textContent(expectedResponseText));
  }

  private void assertToolSpecifications(ChatRequest chatRequest) {
    assertThat(chatRequest.toolSpecifications())
        .extracting(ToolSpecification::name)
        .containsExactly(
            "GetDateAndTime",
            "SuperfluxProduct",
            "Search_The_Web",
            "A_Complex_Tool",
            "Download_A_File",
            "An_Event");
  }

  private void assertIncident(ZeebeTest zeebeTest, ThrowingConsumer<Incident> assertion) {
    final var incidents =
        camundaClient
            .newIncidentSearchRequest()
            .filter(
                filter ->
                    filter.processInstanceKey(
                        zeebeTest.getProcessInstanceEvent().getProcessInstanceKey()))
            .send()
            .join();

    assertThat(incidents.items()).hasSize(1).first().satisfies(assertion);
  }

  private AgentResponse getAgentResponse(ZeebeTest zeebeTest) throws JsonProcessingException {
    final var agentVariableSearchResult =
        new CamundaDataSource(camundaClient)
                .findVariablesByProcessInstanceKey(
                    zeebeTest.getProcessInstanceEvent().getProcessInstanceKey())
                .stream()
                .filter(variable -> variable.getName().equals("agent"))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Agent variable 'agent' not found"));

    if (agentVariableSearchResult.isTruncated()) {
      final var agentVariable =
          camundaClient
              .newVariableGetRequest(agentVariableSearchResult.getVariableKey())
              .send()
              .join();

      return objectMapper.readValue(agentVariable.getValue(), AgentResponse.class);
    }

    return objectMapper.readValue(agentVariableSearchResult.getValue(), AgentResponse.class);
  }

  private Resource testFileResource(String filename) {
    return resourceLoader.getResource("classpath:__files/" + filename);
  }

  private Supplier<String> testFileContent(String filename) {
    return () -> {
      try {
        return testFileResource(filename).getContentAsString(StandardCharsets.UTF_8);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    };
  }

  private Supplier<String> testFileContentBase64(String filename) {
    return () -> {
      try {
        return Base64.getEncoder()
            .encodeToString(testFileResource(filename).getContentAsByteArray());
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    };
  }

  private Map<String, Object> userSatisfiedFeedback() {
    return Map.of("userSatisfied", true);
  }

  private Map<String, Object> userFollowUpFeedback(String followUp) {
    return Map.of("userSatisfied", false, "followUpUserPrompt", followUp);
  }

  private record ChatInteraction(ChatResponse chatResponse, Map<String, Object> userFeedback) {
    public static ChatInteraction of(ChatResponse chatResponse) {
      return new ChatInteraction(chatResponse, null);
    }

    public static ChatInteraction of(ChatResponse chatResponse, Map<String, Object> userFeedback) {
      return new ChatInteraction(chatResponse, userFeedback);
    }
  }

  private record DownloadFileToolResult(int status, DocumentToContentResponseModel document) {}
}
