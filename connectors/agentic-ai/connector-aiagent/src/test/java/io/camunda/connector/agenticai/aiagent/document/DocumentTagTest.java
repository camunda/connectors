/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.DocumentId;
import io.camunda.connector.agenticai.aiagent.model.document.DocumentSource;
import io.camunda.connector.agenticai.aiagent.model.message.content.DocumentReferenceContent;
import io.camunda.connector.agenticai.aiagent.model.tool.DocumentDelivery;
import org.junit.jupiter.api.Test;

class DocumentTagTest {

  @Test
  void readsTheIdOfARenderedTag() {
    final var tag =
        new DocumentReferenceContent(
                DocumentId.of("a&b\"c"),
                "re<port>.pdf",
                "application/pdf",
                10L,
                DocumentSource.toolCall("search", "call_abc"),
                DocumentDelivery.REFERENCE,
                null)
            .toTag();

    assertThat(DocumentTag.idOfSingleTag(tag)).isEqualTo(DocumentId.of("a&b\"c"));
    assertThat(DocumentTag.idOfSingleTag("  \n" + tag + "\t ")).isEqualTo(DocumentId.of("a&b\"c"));
  }

  @Test
  void readsTheIdOfATagWithOnlyAnId() {
    assertThat(DocumentTag.idOfSingleTag("<doc id=\"doc-1\" />")).isEqualTo(DocumentId.of("doc-1"));
    assertThat(DocumentTag.idOfSingleTag("<doc id=\"doc-1\"/>")).isEqualTo(DocumentId.of("doc-1"));
  }

  @Test
  void unescapesTheId() {
    assertThat(DocumentTag.idOfSingleTag("<doc id=\"a&amp;b&quot;c&lt;&gt;\" />"))
        .isEqualTo(DocumentId.of("a&b\"c<>"));
  }

  @Test
  void readsTheIdAttributeNotAnAttributeThatEndsInId() {
    assertThat(
            DocumentTag.idOfSingleTag("<doc toolCallId=\"call_1\" id=\"doc-1\" fileName=\"x\" />"))
        .isEqualTo(DocumentId.of("doc-1"));
    assertThat(DocumentTag.idOfSingleTag("<doc toolCallId=\"call_1\" />")).isNull();
  }

  @Test
  void isNullForTagsInsideText() {
    assertThat(DocumentTag.idOfSingleTag("see <doc id=\"a\" />")).isNull();
    assertThat(DocumentTag.idOfSingleTag("<doc id=\"a\" /> see")).isNull();
    assertThat(DocumentTag.idOfSingleTag("some-id")).isNull();
    assertThat(DocumentTag.idOfSingleTag("")).isNull();
  }

  @Test
  void isNullForTwoTags() {
    assertThat(DocumentTag.idOfSingleTag("<doc id=\"a\" /><doc id=\"b\" />")).isNull();
    assertThat(DocumentTag.idOfSingleTag("<doc id=\"a\" />\n<doc id=\"b\" />")).isNull();
  }

  @Test
  void isNullForATagThatIsNotWellFormedOrHasNoUsableId() {
    assertThat(DocumentTag.idOfSingleTag("<doc id=\"a\">")).isNull();
    assertThat(DocumentTag.idOfSingleTag("<doc fileName=\"x\" />")).isNull();
    assertThat(DocumentTag.idOfSingleTag("<doc id=\" \" />")).isNull();
    assertThat(DocumentTag.idOfSingleTag("<doc />")).isNull();
  }

  @Test
  void isNullForAnEscapedTag() {
    assertThat(DocumentTag.idOfSingleTag("&lt;doc id=\"a\" /&gt;")).isNull();
    assertThat(DocumentTag.idOfSingleTag("\\<doc id=\"a\" />")).isNull();
  }
}
