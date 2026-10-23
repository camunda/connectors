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

import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolExecution;
import io.camunda.connector.agenticai.aiagent.agent.RuntimeToolExecutorImpl;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultsInputBuilder;
import io.camunda.connector.agenticai.aiagent.agent.ToolCallResultsInputBuilderImpl;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolResult;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentGuard;
import io.camunda.connector.agenticai.aiagent.document.DocumentContentLimits;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentRegistries;
import io.camunda.connector.agenticai.aiagent.document.IngestedDocument;
import io.camunda.connector.agenticai.aiagent.model.AgentConfiguration;
import io.camunda.connector.agenticai.aiagent.model.AgentContext;
import io.camunda.connector.agenticai.aiagent.model.AgentFeatures;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentRegistry;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.TextContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCall;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolKind;
import io.camunda.connector.agenticai.aiagent.tool.runtime.RuntimeToolRegistryImpl;
import io.camunda.connector.agenticai.testutil.TestObjectMapperSupplier;
import io.camunda.connector.api.document.Document;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.assertj.core.api.InstanceOfAssertFactories;
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
  private final RuntimeToolRegistryImpl registry =
      new RuntimeToolRegistryImpl(List.of(tool), TestObjectMapperSupplier.INSTANCE);
  private final RuntimeToolExecutorImpl executor =
      new RuntimeToolExecutorImpl(registry, TestObjectMapperSupplier.INSTANCE);
  private final ToolCallResultsInputBuilder inputBuilder =
      new ToolCallResultsInputBuilderImpl(
          new DocumentContentGuard(DocumentContentLimits.defaults()),
          TestObjectMapperSupplier.INSTANCE);

  private final Document report = createDocument("report", "report.txt");
  private final Document summary = createDocument("summary", "summary.txt");
  private final DocumentId reportId = DocumentHandle.idFor(report);
  private final DocumentId summaryId = DocumentHandle.idFor(summary);
  private final String reportTag = "<doc id=\"" + reportId + "\" />";
  private final String summaryTag = "<doc id=\"" + summaryId + "\" />";

  private final DocumentRegistry registered =
      DocumentRegistries.withDocuments(
          DocumentRegistry.empty(),
          List.of(
              new IngestedDocument(report, DocumentSource.toolCall("search", "call-1")),
              new IngestedDocument(summary, DocumentSource.userMessage())));

  /**
   * What the agent does with return_document calls: the executions and the input of the next turn.
   */
  private record Run(
      List<RuntimeToolExecution> executions, ToolCallResultsInputBuilder.Input input) {

    List<ToolCallResultContent> results() {
      return ((ToolCallResultMessage) input.messages().getFirst()).results();
    }

    List<ResponseDocumentSelection> responseDocuments() {
      return input.agentContext().responseDocuments();
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
    assertThat(definition.inputSchema().get("required")).isEqualTo(List.of("key", "document"));
    assertThat(definition.inputSchema().get("properties"))
        .asInstanceOf(InstanceOfAssertFactories.MAP)
        .containsOnlyKeys("key", "document", "description");
    assertThat(definition.inputSchema().toString()).contains("format=camunda-document");
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

    assertThat(registry.enabledToolDefinitions(executionContext, WITH_DOCUMENTS))
        .containsExactly(tool.definition());
    assertThat(
            registry.enabledToolDefinitions(
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
    assertThat(run.executions().getFirst().result().responseDocuments())
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
          .satisfies(
              result ->
                  assertThat(text(result))
                      .startsWith("Parameter 'key' must match [A-Za-z_][A-Za-z0-9_-]*"));
      assertThat(run.executions().getFirst().result()).isInstanceOf(RuntimeToolResult.Error.class);
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
  void answersAnUnknownDocumentWithAnErrorAndSelectsNothing() {
    final var run = run(call("1", Map.of("key", "k", "document", "<doc id=\"unknown\" />")));

    assertThat(run.executions().getFirst().result()).isInstanceOf(RuntimeToolResult.Error.class);
    assertThat(run.responseDocuments()).isEmpty();
  }

  @Test
  void selectsTheDocumentsOfSeveralCallsInTheOrderOfTheCallsAndKeepsTheLastOfAKey() {
    final var run =
        run(
            call("1", Map.of("key", "a", "document", reportTag, "description", "one")),
            call("2", Map.of("key", "b", "document", summaryTag)),
            call("3", Map.of("key", "a", "document", summaryTag, "description", "three")));

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
            WITH_DOCUMENTS.withDocumentRegistry(registered).withResponseDocument(earlier),
            call("1", Map.of("key", "second", "document", summaryTag)));

    assertThat(run.responseDocuments())
        .containsExactly(earlier, new ResponseDocumentSelection("second", summaryId, null));
  }

  private static ToolCall call(String id, Map<String, Object> arguments) {
    return new ToolCall(id, ReturnDocumentTool.NAME, arguments).withKind(ToolKind.RUNTIME);
  }

  private Run run(ToolCall... calls) {
    return run(WITH_DOCUMENTS.withDocumentRegistry(registered), calls);
  }

  private Run run(AgentContext agentContext, ToolCall... calls) {
    final var executions =
        executor.execute(withConfiguration(CONFIGURATION), agentContext, List.of(calls), Set.of());
    final var input =
        inputBuilder.build(
            agentContext,
            executions.stream().map(ToolCallResultsInputBuilder.Entry::of).toList(),
            Set.of());
    return new Run(executions, input);
  }
}
