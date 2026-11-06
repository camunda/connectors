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
import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentDelivery;
import io.camunda.connector.runtime.core.document.InlineDocument;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AgentDocumentContentTest {

  @Test
  void theBlockOfADocumentHoldsItsIdAndTheDeliveryReference() {
    final var doc = new InlineDocument("{\"a\":1}", "data.json", "application/json");

    assertThat(AgentDocumentContent.of(AgentDocumentId.of(doc)))
        .isEqualTo(
            new AgentDocumentContent(
                AgentDocumentId.of(doc), DocumentDelivery.REFERENCE, null, null));
  }

  @Test
  void withDeliveryReturnsACopyWithTheDeliveryAndEverythingElseUnchanged() {
    final var reference =
        new AgentDocumentContent(
            AgentDocumentId.of("doc-1"), DocumentDelivery.REFERENCE, null, Map.of("k", "v"));

    final var withContent = reference.withDelivery(DocumentDelivery.CONTENT);

    assertThat(withContent)
        .isEqualTo(
            new AgentDocumentContent(
                AgentDocumentId.of("doc-1"), DocumentDelivery.CONTENT, null, Map.of("k", "v")));
    assertThat(reference.delivery()).isEqualTo(DocumentDelivery.REFERENCE);
    assertThat(withContent.withDelivery(DocumentDelivery.REFERENCE)).isEqualTo(reference);
  }

  @Test
  void rejectsAMissingId() {
    assertThatThrownBy(() -> new AgentDocumentContent(null, DocumentDelivery.REFERENCE, null, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void roundTripsThroughContentInterfaceTypeWithTypeDiscriminator() throws Exception {
    final var objectMapper = new ObjectMapper();
    final Content reference =
        new AgentDocumentContent(
            AgentDocumentId.of("doc-1"), DocumentDelivery.CONTENT, null, Map.of("k", "v"));

    final var serialized = objectMapper.writeValueAsString(reference);

    assertThat(serialized).contains("\"type\":\"agentDocument\"");
    assertThat(serialized).contains("\"delivery\":\"content\"");
    assertThat(objectMapper.readValue(serialized, Content.class)).isEqualTo(reference);
  }

  @Test
  void serializesWithoutAbsentAttributesAndReadsAMissingDeliveryAsReference() throws Exception {
    final var objectMapper = new ObjectMapper();
    final var reference =
        new AgentDocumentContent(
            AgentDocumentId.of("doc-1"), DocumentDelivery.REFERENCE, null, null);

    final var serialized = objectMapper.writeValueAsString(reference);

    assertThat(serialized)
        .isEqualTo("{\"type\":\"agentDocument\",\"id\":\"doc-1\",\"delivery\":\"reference\"}");
    assertThat(
            objectMapper.readValue("{\"type\":\"agentDocument\",\"id\":\"doc-1\"}", Content.class))
        .isEqualTo(reference);
  }

  @Test
  void ofTheIdIsDeliveredAsReference() {
    assertThat(AgentDocumentContent.of(AgentDocumentId.of("doc-1")))
        .isEqualTo(
            new AgentDocumentContent(
                AgentDocumentId.of("doc-1"), DocumentDelivery.REFERENCE, null, null));
  }

  @Test
  void withNoteReturnsACopyWithTheNoteAndEverythingElseUnchanged() {
    final var block =
        new AgentDocumentContent(
            AgentDocumentId.of("doc-1"), DocumentDelivery.REFERENCE, null, Map.of("k", "v"));

    final var withNote = block.withNote("It cannot be loaded.");

    assertThat(withNote)
        .isEqualTo(
            new AgentDocumentContent(
                AgentDocumentId.of("doc-1"),
                DocumentDelivery.REFERENCE,
                "It cannot be loaded.",
                Map.of("k", "v")));
    assertThat(block.note()).isNull();
    assertThat(withNote.withDelivery(DocumentDelivery.CONTENT).note())
        .isEqualTo("It cannot be loaded.");
  }

  @Test
  void aBlankNoteIsNoNote() {
    assertThat(AgentDocumentContent.of(AgentDocumentId.of("doc-1")).withNote("  ").note()).isNull();
  }

  @Test
  void theNoteIsPersistedOnlyWhenSetAndRoundTrips() throws Exception {
    final var objectMapper = new ObjectMapper();
    final Content withNote =
        AgentDocumentContent.of(AgentDocumentId.of("doc-1")).withNote("It cannot be loaded.");

    final var serialized = objectMapper.writeValueAsString(withNote);

    assertThat(serialized)
        .isEqualTo(
            "{\"type\":\"agentDocument\",\"id\":\"doc-1\",\"delivery\":\"reference\",\"note\":\"It cannot be loaded.\"}");
    assertThat(objectMapper.readValue(serialized, Content.class)).isEqualTo(withNote);
  }
}
