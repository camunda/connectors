/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.message.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.document.DocumentHandle;
import io.camunda.connector.agenticai.aiagent.document.DocumentReferences;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import io.camunda.connector.runtime.core.document.InlineDocument;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DocumentReferenceContentTest {

  @Test
  void theBlockOfADocumentHoldsItsIdAndTheDeliveryReference() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");

    assertThat(DocumentReferences.from(doc))
        .isEqualTo(
            new DocumentReferenceContent(
                DocumentHandle.idFor(doc), DocumentDelivery.REFERENCE, null));
  }

  @Test
  void withDeliveryReturnsACopyWithTheDeliveryAndEverythingElseUnchanged() {
    final var reference =
        new DocumentReferenceContent(
            DocumentId.of("doc-1"), DocumentDelivery.REFERENCE, Map.of("k", "v"));

    final var withContent = reference.withDelivery(DocumentDelivery.CONTENT);

    assertThat(withContent)
        .isEqualTo(
            new DocumentReferenceContent(
                DocumentId.of("doc-1"), DocumentDelivery.CONTENT, Map.of("k", "v")));
    assertThat(reference.delivery()).isEqualTo(DocumentDelivery.REFERENCE);
    assertThat(withContent.withDelivery(DocumentDelivery.REFERENCE)).isEqualTo(reference);
  }

  @Test
  void rejectsAMissingId() {
    assertThatThrownBy(() -> new DocumentReferenceContent(null, DocumentDelivery.REFERENCE, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void roundTripsThroughContentInterfaceTypeWithTypeDiscriminator() throws Exception {
    final var objectMapper = new ObjectMapper();
    final Content reference =
        new DocumentReferenceContent(
            DocumentId.of("doc-1"), DocumentDelivery.CONTENT, Map.of("k", "v"));

    final var serialized = objectMapper.writeValueAsString(reference);

    assertThat(serialized).contains("\"type\":\"documentReference\"");
    assertThat(serialized).contains("\"delivery\":\"content\"");
    assertThat(objectMapper.readValue(serialized, Content.class)).isEqualTo(reference);
  }

  @Test
  void serializesWithoutAbsentAttributesAndReadsAMissingDeliveryAsReference() throws Exception {
    final var objectMapper = new ObjectMapper();
    final var reference =
        new DocumentReferenceContent(DocumentId.of("doc-1"), DocumentDelivery.REFERENCE, null);

    final var serialized = objectMapper.writeValueAsString(reference);

    assertThat(serialized)
        .isEqualTo("{\"type\":\"documentReference\",\"id\":\"doc-1\",\"delivery\":\"reference\"}");
    assertThat(
            objectMapper.readValue(
                "{\"type\":\"documentReference\",\"id\":\"doc-1\"}", Content.class))
        .isEqualTo(reference);
  }

  @Test
  void notExpandedFailureNamesTheTypeAndTheExpansion() {
    final var failure =
        new DocumentReferenceContent(DocumentId.of("doc-1"), DocumentDelivery.REFERENCE, null)
            .notExpandedFailure();

    assertThat(failure)
        .hasMessageContaining("DocumentReferenceContent")
        .hasMessageContaining("doc-1")
        .hasMessageContaining("expanded");
  }
}
