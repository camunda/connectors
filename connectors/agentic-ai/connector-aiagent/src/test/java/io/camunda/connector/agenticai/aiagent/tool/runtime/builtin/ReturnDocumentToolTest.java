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
import io.camunda.connector.agenticai.aiagent.api.tool.DocumentSchemas;
import io.camunda.connector.agenticai.aiagent.document.DocumentParameterResolver;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments;
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocuments.NewDocument;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentOrigin;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.AssistantMessage;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
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

  private static final AgentConfiguration CONFIGURATION =
      new AgentConfiguration(TEST_CHAT_MODEL, TEST_SYSTEM_PROMPT, null, null, null, null, null);

  private static final AgentContext WITH_DOCUMENTS =
      AgentContext.empty().withFeatures(AgentFeatures.ofNewInstance());

  private final ReturnDocumentTool tool = new ReturnDocumentTool();
  private final DocumentParameterResolver resolver = new DocumentParameterResolver();
  private final RuntimeTools runtimeTools =
      new RuntimeTools(List.of(tool), TestObjectMapperSupplier.INSTANCE, resolver);
  private final LocalToolCallExecutor executor = new LocalToolCallExecutor(runtimeTools);
  private final ToolCallResultsInputBuilder inputBuilder =
      new ToolCallResultsInputBuilder(TestDocuments.documentContentDelivery());

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final AgentDocumentId reportId = AgentDocumentId.of(report);
  private final AgentDocumentId summaryId = AgentDocumentId.of(summary);
  private final String reportTag = "<doc id=\"" + reportId + "\" />";
  private final String summaryTag = "<doc id=\"" + summaryId + "\" />";

  private final AgentDocuments registered =
      AgentDocuments.empty()
          .register(
              List.of(
                  new NewDocument(report, DocumentOrigin.toolCall("search", "call-1")),
                  new NewDocument(summary, DocumentOrigin.userMessage())));

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
            "Attaches one document to your final response. Use this only for a document the user"
                + " should receive in your answer; do not use it for documents you only pass to"
                + " other tools. Call it once per document. The key is a stable identifier the"
                + " process uses to find the document; it starts with a letter or an underscore and"
                + " continues with letters, digits, underscores or hyphens. The description is"
                + " optional. Calling it again with the same key replaces the document returned"
                + " under that key. Documents you returned earlier stay attached to your later final"
                + " responses too, so you do not need to return them again.");
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
                        "A stable identifier the process uses to find the document, matching"
                            + " [A-Za-z_][A-Za-z0-9_-]*."),
                    "document",
                    DocumentSchemas.document("The document to return."),
                    "description",
                    Map.of(
                        "type",
                        "string",
                        "description",
                        "An optional short description of the document.")),
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
        .containsExactly("type", "format", "description");
    assertThat(schema.toString()).doesNotContain("Pass the tag of a known document");
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
        run(call("1", Map.of("key", "summary", "document", summaryTag, "description", "Short")));

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
    final var run = run(call("1", Map.of("key", "report", "document", reportTag)));

    assertThat(run.responseDocuments())
        .containsExactly(new ResponseDocumentSelection("report", reportId, null));
  }

  @Test
  void aBlankDescriptionIsLeftOut() {
    final var run =
        run(call("1", Map.of("key", "report", "document", reportTag, "description", " ")));

    assertThat(run.responseDocuments())
        .containsExactly(new ResponseDocumentSelection("report", reportId, null));
  }

  @Test
  void acceptsKeysWithUnderscoresDigitsAndHyphens() {
    final var run = run(call("1", Map.of("key", "_report-2024_final", "document", reportTag)));

    assertThat(run.responseDocuments())
        .extracting(ResponseDocumentSelection::key)
        .containsExactly("_report-2024_final");
  }

  @Test
  void answersAnInvalidKeyWithAnErrorAndSelectsNothing() {
    for (final var key : List.of("", "1abc", "a b", "a.b", "-a")) {
      final var run = run(call("1", Map.of("key", key, "document", reportTag)));

      assertThat(run.results())
          .singleElement()
          .satisfies(result ->
              assertThat(text(result))
                  .startsWith("Parameter 'key' must match [A-Za-z_][A-Za-z0-9_-]*"));
      assertThat(run.entries().getFirst().effects()).isEmpty();
      assertThat(run.responseDocuments()).isEmpty();
    }
  }

  @Test
  void answersAMissingKeyOrDocumentWithAnErrorAndSelectsNothing() {
    final var nullKey = new HashMap<String, Object>();
    nullKey.put("key", null);
    nullKey.put("document", reportTag);

    for (final var arguments :
        List.<Map<String, Object>>of(Map.of("document", reportTag), nullKey, Map.of("key", "k"))) {
      final var run = run(call("1", arguments));

      assertThat(run.results()).singleElement();
      assertThat(text(run.results().getFirst())).contains("Parameter");
      assertThat(run.responseDocuments()).isEmpty();
    }
  }

  @Test
  void answersAnUnknownDocumentWithTheRejectionOfTheAdmissionAndSelectsNothing() {
    final var agentContext = WITH_DOCUMENTS.withDocuments(registered);
    final var message =
        AssistantMessage.builder()
            .toolCalls(List.of(call("1", Map.of("key", "k", "document", "<doc id=\"unknown\" />"))))
            .build();

    final var admitted =
        new ToolCallAdmission(resolver)
            .admit(message, List.of(tool.definition()), agentContext)
            .toolCalls();
    final var entries =
        executor.execute(withConfiguration(CONFIGURATION), agentContext, Set.of(), admitted);
    final var input =
        inputBuilder.build(agentContext, ToolCallBatch.of(admitted).withEntries(entries), Set.of());

    assertThat(admitted.getFirst().rejection()).startsWith("Parameter 'document' must be");
    assertThat(entries)
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
            call("1", Map.of("key", "a", "document", reportTag, "description", "one")),
            call("2", Map.of("key", "b", "document", summaryTag)),
            call("3", Map.of("key", "a", "document", summaryTag, "description", "three")));

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
            call("1", Map.of("key", "second", "document", summaryTag)));

    assertThat(run.responseDocuments())
        .containsExactly(earlier, new ResponseDocumentSelection("second", summaryId, null));
  }

  private static ToolCall call(String id, Map<String, Object> arguments) {
    return new ToolCall(id, ReturnDocumentTool.NAME, arguments).withKind(ToolKind.RUNTIME);
  }

  private Run run(ToolCall... calls) {
    return run(WITH_DOCUMENTS.withDocuments(registered), calls);
  }

  private Run run(AgentContext agentContext, ToolCall... calls) {
    final var entries =
        executor.execute(withConfiguration(CONFIGURATION), agentContext, Set.of(), List.of(calls));
    final var batch = ToolCallBatch.of(List.of(calls)).withEntries(entries);
    final var input =
        inputBuilder.build(agentContext, batch, Set.of(), TestDocuments.documentFactory());
    return new Run(entries, input, input.applyTo(agentContext));
  }
}
