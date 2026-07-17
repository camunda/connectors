/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.framework.multimodal;

import static io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities.Modality;
import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.framework.capabilities.CoreModelCapabilities;
import io.camunda.connector.agenticai.aiagent.framework.capabilities.ModelCapabilities;
import io.camunda.connector.agenticai.aiagent.memory.ConversationSnapshot;
import io.camunda.connector.agenticai.aiagent.model.message.Message;
import io.camunda.connector.agenticai.aiagent.model.message.ToolCallResultMessage;
import io.camunda.connector.agenticai.aiagent.model.message.UserMessage;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentContent;
import io.camunda.connector.agenticai.aiagent.model.message.content.ObjectContent;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResult;
import io.camunda.connector.agenticai.aiagent.model.tool.ToolCallResultContent;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentCreationRequest;
import io.camunda.connector.api.error.ConnectorException;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.InMemoryDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CapabilityAwareToolCallResultStrategyTest {

  private static ModelCapabilities caps(List<Modality> toolResult, List<Modality> userMessage) {
    return new CoreModelCapabilities(userMessage, toolResult, List.of(Modality.TEXT), null, null);
  }

  private static final ModelCapabilities BRIDGE_CAPS =
      caps(List.of(), List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT));
  private static final ModelCapabilities NATIVE_DOC_CAPS =
      caps(
          List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT),
          List.of(Modality.TEXT, Modality.IMAGE, Modality.DOCUMENT));

  private final DocumentFactoryImpl documentFactory =
      new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE);
  private final ToolCallResultStrategy strategy = new CapabilityAwareToolCallResultStrategy();

  private Document doc(String content, String contentType, String fileName) {
    return documentFactory.create(
        DocumentCreationRequest.from(content.getBytes(StandardCharsets.UTF_8))
            .contentType(contentType)
            .fileName(fileName)
            .build());
  }

  private ConversationSnapshot snapshot(Message... messages) {
    return new ConversationSnapshot(List.of(messages), List.of());
  }

  /** Mirrors the composer's self-describing lift: from(raw) content + appended DocumentContent. */
  private ToolCallResultMessage toolResult(
      String id, String name, Object rawContent, Document... docs) {
    final var base =
        ToolCallResultContent.from(
            ToolCallResult.builder().id(id).name(name).content(rawContent).build());
    final var content = new ArrayList<>(base.content());
    for (var d : docs) {
      content.add(DocumentContent.documentContent(d));
    }
    return ToolCallResultMessage.builder().results(List.of(base.withContent(content))).build();
  }

  @Test
  void unsupportedToolResultModality_stripsDocAndInsertsSyntheticFallback() {
    var pdf = doc("pdf-bytes", "application/pdf", "report.pdf");
    var trm = toolResult("call_1", "getReport", Map.of("k", "v"), pdf);

    var out = strategy.routeToolResults(snapshot(trm), BRIDGE_CAPS).messages();

    assertThat(out).hasSize(2);
    var strippedTrm = (ToolCallResultMessage) out.get(0);
    assertThat(strippedTrm.results().getFirst().content())
        .noneMatch(DocumentContent.class::isInstance)
        .anyMatch(ObjectContent.class::isInstance); // object content preserved
    var synthetic = (UserMessage) out.get(1);
    assertThat(synthetic.metadata()).containsEntry(UserMessage.METADATA_TOOL_CALL_DOCUMENTS, true);
    assertThat(synthetic.content().getFirst())
        .isEqualTo(textContent(CapabilityAwareToolCallResultStrategy.TOOL_CALL_DOCUMENTS_PREAMBLE));
    assertThat(synthetic.content()).contains(DocumentContent.documentContent(pdf));
  }

  @Test
  void supportedToolResultModality_keepsDocInlineAndSynthesizesNothing() {
    var pdf = doc("pdf-bytes", "application/pdf", "report.pdf");
    var trm = toolResult("call_1", "getReport", Map.of("k", "v"), pdf);

    var out = strategy.routeToolResults(snapshot(trm), NATIVE_DOC_CAPS).messages();

    assertThat(out).hasSize(1);
    var keptTrm = (ToolCallResultMessage) out.getFirst();
    assertThat(keptTrm.results().getFirst().content())
        .contains(DocumentContent.documentContent(pdf));
  }

  @Test
  void mixedModalities_stripsOnlyUnsupportedDocs() {
    var pdf = doc("pdf", "application/pdf", "report.pdf");
    var png = doc("png", "image/png", "chart.png");
    var trm = toolResult("c1", "t", Map.of("k", "v"), pdf, png);

    // toolResult supports IMAGE but not DOCUMENT
    var out =
        strategy
            .routeToolResults(
                snapshot(trm), caps(List.of(Modality.TEXT, Modality.IMAGE), List.of(Modality.TEXT)))
            .messages();

    assertThat(out).hasSize(2);
    var strippedTrm = (ToolCallResultMessage) out.get(0);
    assertThat(strippedTrm.results().getFirst().content())
        .contains(DocumentContent.documentContent(png)) // image kept inline
        .doesNotContain(DocumentContent.documentContent(pdf)); // pdf stripped
    assertThat(((UserMessage) out.get(1)).content())
        .contains(DocumentContent.documentContent(pdf))
        .doesNotContain(DocumentContent.documentContent(png));
  }

  @Test
  void textModalityDocument_fallsBackWhenToolResultEmbeddingUnsupported() {
    // BRIDGE_CAPS declares toolResultModalities = [] (a model that cannot embed any document
    // inline in a tool result), so even a TEXT-bucket document (text/csv) must be extracted into
    // the synthetic fallback message.
    var csv = doc("a,b,c", "text/csv", "data.csv");
    var trm = toolResult("call_1", "getReport", Map.of("k", "v"), csv);

    var out = strategy.routeToolResults(snapshot(trm), BRIDGE_CAPS).messages();

    assertThat(out).hasSize(2);
    var strippedTrm = (ToolCallResultMessage) out.get(0);
    assertThat(strippedTrm.results().getFirst().content())
        .noneMatch(DocumentContent.class::isInstance);
    var synthetic = (UserMessage) out.get(1);
    assertThat(synthetic.content()).contains(DocumentContent.documentContent(csv));
  }

  @Test
  void textModalityDocument_keptInlineWhenToolResultSupportsText() {
    // Under the capability-model reframe, toolResultModalities purely describes which document
    // modalities can be embedded at the tool-result location: a model that declares TEXT support
    // there keeps a text-family document (text/csv) inline, no synthetic fallback.
    var csv = doc("a,b,c", "text/csv", "data.csv");
    var trm = toolResult("call_1", "getReport", Map.of("k", "v"), csv);

    var out =
        strategy
            .routeToolResults(
                snapshot(trm), caps(List.of(Modality.TEXT), List.of(Modality.TEXT, Modality.IMAGE)))
            .messages();

    assertThat(out).hasSize(1);
    var keptTrm = (ToolCallResultMessage) out.getFirst();
    assertThat(keptTrm.results().getFirst().content())
        .contains(DocumentContent.documentContent(csv));
  }

  @Test
  void userMessageDoc_unsupportedModality_failsLoud() {
    var pdf = doc("pdf", "application/pdf", "report.pdf");
    var userMessage =
        UserMessage.builder().content(List.of(DocumentContent.documentContent(pdf))).build();

    assertThatThrownBy(
            () ->
                strategy.routeToolResults(
                    snapshot(userMessage), caps(List.of(Modality.TEXT), List.of(Modality.TEXT))))
        .isInstanceOf(ConnectorException.class)
        .hasMessageContaining("DOCUMENT")
        .hasMessageContaining("report.pdf");
  }
}
