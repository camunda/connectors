/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.api.tool;

import static io.camunda.connector.agenticai.aiagent.model.message.content.TextContent.textContent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.ResponseDocumentSelection;
import io.camunda.connector.agenticai.aiagent.model.message.content.Content;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RuntimeToolResultTest {

  private static final DocumentId DOC_1 = DocumentId.of("doc-1");
  private static final DocumentId DOC_2 = DocumentId.of("doc-2");

  @Test
  void aTextBecomesTheContent() {
    final var result = RuntimeToolResult.success("ok");

    assertThat(result).isInstanceOf(RuntimeToolResult.Success.class);
    assertThat(result.content()).containsExactly(textContent("ok"));
  }

  @Test
  void aBlankTextBecomesNoContent() {
    assertThat(RuntimeToolResult.success(" ").content()).isEmpty();
  }

  @Test
  void theGivenBlocksBecomeTheContentInOrder() {
    final var result = RuntimeToolResult.success(List.of(textContent("a"), textContent("b")));

    assertThat(result).isInstanceOf(RuntimeToolResult.Success.class);
    assertThat(result.content()).containsExactly(textContent("a"), textContent("b"));
  }

  @Test
  void anErrorResultCarriesItsMessageAsContent() {
    final var result = RuntimeToolResult.error("no");

    assertThat(result).isEqualTo(new RuntimeToolResult.Error("no"));
    assertThat(result.content()).containsExactly(textContent("no"));
  }

  @Test
  void aBlankErrorMessageGivesNoContent() {
    assertThat(RuntimeToolResult.error(" ").content()).isEmpty();
  }

  @Test
  void nullsAreRejected() {
    assertThatThrownBy(() -> RuntimeToolResult.success((String) null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> RuntimeToolResult.success((List<Content>) null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> RuntimeToolResult.error(null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void theContentIsACopyThatCannotBeChanged() {
    final var blocks = new ArrayList<Content>(List.of(textContent("a")));
    final var result = RuntimeToolResult.success(blocks);

    blocks.add(textContent("b"));

    assertThat(result.content()).containsExactly(textContent("a"));
    assertThatThrownBy(() -> result.content().add(textContent("c")))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void aSuccessfulResultKeepsTheDocumentContentEffectsInTheOrderTheyWereNamed() {
    final var result =
        RuntimeToolResult.success("ok").withDocumentContent(DOC_2).withDocumentContent(DOC_1);

    assertThat(result.documentContent()).containsExactly(DOC_2, DOC_1);
    assertThat(result.content()).containsExactly(textContent("ok"));
  }

  @Test
  void aSuccessfulResultKeepsTheResponseDocumentEffectsInTheOrderTheyWereNamed() {
    final var result =
        RuntimeToolResult.success("ok")
            .withResponseDocument("second", DOC_2, null)
            .withResponseDocument("first", DOC_1, "A document")
            .withDocumentContent(DOC_1);

    assertThat(result.responseDocuments())
        .containsExactly(
            new ResponseDocumentSelection("second", DOC_2, null),
            new ResponseDocumentSelection("first", DOC_1, "A document"));
    assertThat(result.documentContent()).containsExactly(DOC_1);
    assertThat(result.content()).containsExactly(textContent("ok"));
  }

  @Test
  void aSuccessfulResultWithoutEffectsNamesNoDocument() {
    final var result = RuntimeToolResult.success("ok");

    assertThat(result.documentContent()).isEmpty();
    assertThat(result.responseDocuments()).isEmpty();
  }

  @Test
  void anErrorResultHasNoEffects() {
    final var result = RuntimeToolResult.error("no");

    assertThat(result.documentContent()).isEmpty();
    assertThat(result.responseDocuments()).isEmpty();
  }

  @Test
  void aResponseDocumentKeyMustNotBeBlank() {
    final var result = RuntimeToolResult.success("ok");

    assertThatThrownBy(() -> result.withResponseDocument(" ", DOC_1, null))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void aWitherLeavesTheOriginalResultUnchanged() {
    final var original = RuntimeToolResult.success("ok");

    original.withDocumentContent(DOC_1);
    original.withResponseDocument("key", DOC_1, null);

    assertThat(original.documentContent()).isEmpty();
    assertThat(original.responseDocuments()).isEmpty();
  }

  @Test
  void theEffectsAreACopyThatCannotBeChanged() {
    final var result =
        RuntimeToolResult.success("ok")
            .withDocumentContent(DOC_1)
            .withResponseDocument("key", DOC_1, null);

    assertThatThrownBy(() -> result.documentContent().add(DOC_2))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(
            () -> result.responseDocuments().add(new ResponseDocumentSelection("k", DOC_2, null)))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
