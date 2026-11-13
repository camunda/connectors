/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime.builtin;

import static io.camunda.connector.agenticai.aiagent.TestAgentExecutionContext.withConfiguration;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_CHAT_MODEL;
import static io.camunda.connector.agenticai.aiagent.TestMessagesFixture.TEST_SYSTEM_PROMPT;
import static io.camunda.connector.agenticai.testutil.TestDocuments.createDocument;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.agent.LocalToolCallExecutor;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallAdmission;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallBatch;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultsInputBuilder;
import io.camunda.connector.agenticai.aiagent.agent.TurnInput;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocument;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentHandle;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentParameter;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallEffect;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeTools;
import io.camunda.connector.agenticai.testutil.TestDocuments;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * The {@code c8__return_document} tool run by the framework, the way the agent runs it: the
 * executor runs the call and the input builder applies the effect of the result.
 */
class ReturnDocumentToolTest {

  private static final String INVALID_KEY_ERROR =
      "Parameter 'key' must match [A-Za-z_][A-Za-z0-9_-]*.";
  private static final String MISSING_DOCUMENT_ERROR =
      "Parameter 'document' is required: pass the handle of a document from the conversation.";

  private static final AgentConfiguration CONFIGURATION =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);

  private static final AgentContext WITH_DOCUMENTS =
      AgentContext.empty().withFeatures(AgentFeatures.ofNewInstance());

  private final ReturnDocumentTool tool = new ReturnDocumentTool();
  private final DocumentParameterResolver resolver =
      new DocumentParameterResolver(TestObjectMapperSupplier.INSTANCE);
  private final RuntimeTools runtimeTools =
      new RuntimeTools(List.of(tool), TestObjectMapperSupplier.INSTANCE, resolver);
  private final LocalToolCallExecutor executor =
      new LocalToolCallExecutor(runtimeTools, TestDocuments.objectHandleRenderer());
  private final ToolCallResultsInputBuilder inputBuilder =
      new ToolCallResultsInputBuilder(TestDocuments.documentContentDelivery());

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final AgentDocumentId reportId = AgentDocumentId.of(report);
  private final AgentDocumentId summaryId = AgentDocumentId.of(summary);
  private final Map<String, Object> reportHandle = handle(reportId);
  private final Map<String, Object> summaryHandle = handle(summaryId);

  private final AgentDocuments registered =
      AgentDocuments.empty()
          .register(
              List.of(
                  AgentDocument.of(report, AgentDocumentOrigin.toolCall("search", "call-1")),
                  AgentDocument.of(
                      summary, AgentDocumentOrigin.userMessage(TestDocuments.MESSAGE_ID))));

  /**
   * What the agent does with return_document calls: the entries of the results, the input of the
   * next turn and the agent context the turn continues with.
   */
  private record Run(
      List<ToolCallBatch.Entry> entries, TurnInput input, AgentContext agentContext) {

    List<ToolCallResultContent> results() {
      return ((ToolCallResultMessage) input.messages().getFirst()).results();
    }

    /** The response documents of the agent context after the input is applied. */
    List<ResponseDocumentSelection> responseDocuments() {
      return agentContext.responseDocuments();
    }
  }

  private static String text(ToolCallResultContent result) {
    assertThat(result.content()).singleElement().isInstanceOf(TextContent.class);
    return ((TextContent) result.content().getFirst()).text();
  }

  @Test
  void definitionDescribesOneDocumentPerCallUnderAKey() {
    final var definition = tool.definition();

    assertThat(ReturnDocumentTool.NAME).isEqualTo("c8__return_document");
    assertThat(definition.description())
        .isEqualTo(
            "Adds a document from the conversation to your final response. Call it once per"
                + " document. A returned document stays in all later final responses.");
    assertThat(definition.inputSchema())
        .isEqualTo(
            Map.of(
                "type",
                "object",
                "properties",
                Map.of(
                    "key",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Key of the document in the response. Pattern: [A-Za-z_][A-Za-z0-9_-]*."),
                    "document",
                    DocumentParameter.documentSchema("Handle of the document to return."),
                    "description",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "Optional short description of the document.")),
                "required",
                List.of("key", "document")));
  }

  @Test
  void definitionKeepsADeterministicKeyOrder() {
    final var schema = tool.definition().inputSchema();

    assertThat(schema.keySet()).containsExactly("type", "properties", "required");
    @SuppressWarnings("unchecked")
    final var properties = (Map<String, Object>) schema.get("properties");
    assertThat(properties.keySet()).containsExactly("key", "document", "description");
    assertThat(((Map<String, Object>) properties.get("key")).keySet())
        .containsExactly("type", "description");
    assertThat(((Map<String, Object>) properties.get("document")).keySet())
        .containsExactly("type", "description", "properties", "required");
  }

  @Test
  void isOfferedToEveryAgentWithTheDocumentsFeatureOnly() {
    final var executionContext = withConfiguration(CONFIGURATION);

    assertThat(runtimeTools.enabledDefinitions(executionContext, WITH_DOCUMENTS))
        .containsExactly(tool.definition());
    assertThat(
            runtimeTools.enabledDefinitions(
                executionContext,
                AgentContext.empty().withFeatures(List.of(AgentFeatures.RUNTIME_TOOLS))))
        .isEmpty();
  }

  @Test
  void answersAValidCallAndSelectsTheDocumentUnderItsKey() {
    final var run =
        run(call("1", Map.of("key", "summary", "document", summaryHandle, "description", "Short")));

    assertThat(run.results())
        .extracting(ReturnDocumentToolTest::text)
        .containsExactly("Returned the document under the key 'summary'.");
    assertThat(run.entries().getFirst().effects())
        .containsExactly(
            new ToolCallEffect.SelectResponseDocument(
                new ResponseDocumentSelection("summary", summaryId, "Short")));
    assertThat(run.input().responseDocuments())
        .containsExactly(new ResponseDocumentSelection("summary", summaryId, "Short"));
    assertThat(run.responseDocuments())
        .containsExactly(new ResponseDocumentSelection("summary", summaryId, "Short"));
  }

  @Test
  void theDescriptionIsOptional() {
    final var run = run(call("1", Map.of("key", "report", "document", reportHandle)));

    assertThat(run.responseDocuments())
        .containsExactly(new ResponseDocumentSelection("report", reportId, null));
  }

  @Test
  void aBlankDescriptionIsLeftOut() {
    final var run =
        run(call("1", Map.of("key", "report", "document", reportHandle, "description", " ")));

    assertThat(run.responseDocuments())
        .containsExactly(new ResponseDocumentSelection("report", reportId, null));
  }

  @Test
  void acceptsKeysWithUnderscoresDigitsAndHyphens() {
    final var run = run(call("1", Map.of("key", "_report-2024_final", "document", reportHandle)));

    assertThat(run.responseDocuments())
        .containsExactly(new ResponseDocumentSelection("_report-2024_final", reportId, null));
  }

  @Test
  void answersAnInvalidKeyWithAnErrorAndSelectsNothing() {
    for (final var key : List.of("", "1abc", "a b", "a.b", "-a")) {
      final var run = run(call("1", Map.of("key", key, "document", reportHandle)));

      assertThat(run.results())
          .singleElement()
          .satisfies(result -> assertThat(text(result)).isEqualTo(INVALID_KEY_ERROR));
      assertThat(run.entries().getFirst().effects()).isEmpty();
      assertThat(run.responseDocuments()).isEmpty();
    }
  }

  @Test
  void answersAMissingKeyOrDocumentWithAnErrorAndSelectsNothing() {
    final var nullKey = new HashMap<String, Object>();
    nullKey.put("key", null);
    nullKey.put("document", reportHandle);

    final var cases =
        List.of(
            Map.entry(Map.<String, Object>of("document", reportHandle), INVALID_KEY_ERROR),
            Map.entry((Map<String, Object>) nullKey, INVALID_KEY_ERROR),
            Map.entry(Map.<String, Object>of("key", "k"), MISSING_DOCUMENT_ERROR));
    for (final var testCase : cases) {
      final var run = run(call("1", testCase.getKey()));

      assertThat(run.results())
          .singleElement()
          .satisfies(result -> assertThat(text(result)).isEqualTo(testCase.getValue()));
      assertThat(run.responseDocuments()).isEmpty();
    }
  }

  @Test
  void answersAnUnknownDocumentWithTheRejectionOfTheAdmissionAndSelectsNothing() {
    final var agentContext = WITH_DOCUMENTS.withDocuments(registered);
    final var message =
        AssistantMessage.builder()
            .toolCalls(
                List.of(
                    call(
                        "1",
                        Map.of("key", "k", "document", handle(AgentDocumentId.of("unknown"))))))
            .build();

    final var admitted =
        new ToolCallAdmission(resolver)
            .admit(agentContext, List.of(tool.definition()), message)
            .toolCalls();
    // the batch answers a rejected call with its rejection, so nothing is left to execute
    final var batch = ToolCallBatch.of(admitted);
    final var entries =
        executor.execute(withConfiguration(CONFIGURATION), agentContext, batch.unanswered());
    final var input = inputBuilder.build(agentContext, batch.withEntries(entries), Set.of());

    assertThat(admitted.getFirst().rejection())
        .isEqualTo(
            "Parameter 'document' has the id 'unknown', but the conversation has no document"
                + " with this id. Pass the handle of a document from the conversation: a JSON"
                + " object such as {\"type\":\"camunda.agenticai.document\",\"id\":\"doc_…\"}.");
    assertThat(entries).isEmpty();
    assertThat(batch.entriesInCallOrder())
        .singleElement()
        .satisfies(entry -> {
          assertThat(text(entry.result())).isEqualTo(admitted.getFirst().rejection());
          assertThat(entry.effects()).isEmpty();
        });
    assertThat(input.responseDocuments()).isEmpty();
    assertThat(input.applyTo(agentContext).responseDocuments()).isEmpty();
  }

  @Test
  void selectsTheDocumentsOfSeveralCallsInTheOrderOfTheCallsAndKeepsTheLastOfAKey() {
    final var run =
        run(
            call("1", Map.of("key", "a", "document", reportHandle, "description", "one")),
            call("2", Map.of("key", "b", "document", summaryHandle)),
            call("3", Map.of("key", "a", "document", summaryHandle, "description", "three")));

    assertThat(run.input().responseDocuments())
        .containsExactly(
            new ResponseDocumentSelection("a", reportId, "one"),
            new ResponseDocumentSelection("b", summaryId, null),
            new ResponseDocumentSelection("a", summaryId, "three"));
    assertThat(run.responseDocuments())
        .containsExactly(
            new ResponseDocumentSelection("a", summaryId, "three"),
            new ResponseDocumentSelection("b", summaryId, null));
  }

  @Test
  void keepsTheSelectionsOfEarlierTurnsInTheContext() {
    final var earlier = new ResponseDocumentSelection("first", reportId, null);
    final var run =
        run(
            WITH_DOCUMENTS.withDocuments(registered).withResponseDocument(earlier),
            call("1", Map.of("key", "second", "document", summaryHandle)));

    assertThat(run.responseDocuments())
        .containsExactly(earlier, new ResponseDocumentSelection("second", summaryId, null));
  }

  private static Map<String, Object> handle(AgentDocumentId id) {
    return Map.of("type", AgentDocumentHandle.TYPE, "id", id.value());
  }

  private static ToolCall call(String id, Map<String, Object> arguments) {
    return new ToolCall(id, ReturnDocumentTool.NAME, arguments).withKind(ToolKind.RUNTIME);
  }

  private Run run(ToolCall... calls) {
    return run(WITH_DOCUMENTS.withDocuments(registered), calls);
  }

  private Run run(AgentContext agentContext, ToolCall... calls) {
    final var entries =
        executor.execute(withConfiguration(CONFIGURATION), agentContext, List.of(calls));
    final var batch = ToolCallBatch.of(List.of(calls)).withEntries(entries);
    final var input = inputBuilder.build(agentContext, batch, Set.of());
    return new Run(entries, input, input.applyTo(agentContext));
  }
}
