/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.document;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.aiagent.model.document.AgentDocumentId;
import org.junit.jupiter.api.Test;

class DocumentTagsTest {

  @Test
  void readsTheIdOfATagSurroundedByWhitespace() {
    final var tag = "<doc id=\"a&amp;b&quot;c\" />";

    assertThat(DocumentTags.idOfSingleTag(tag)).isEqualTo(AgentDocumentId.of("a&b\"c"));
    assertThat(DocumentTags.idOfSingleTag("  \n" + tag + "\t "))
        .isEqualTo(AgentDocumentId.of("a&b\"c"));
  }

  @Test
  void readsTheIdOfATagWithOnlyAnId() {
    assertThat(DocumentTags.idOfSingleTag("<doc id=\"doc-1\" />"))
        .isEqualTo(AgentDocumentId.of("doc-1"));
    assertThat(DocumentTags.idOfSingleTag("<doc id=\"doc-1\"/>"))
        .isEqualTo(AgentDocumentId.of("doc-1"));
  }

  @Test
  void unescapesTheId() {
    assertThat(DocumentTags.idOfSingleTag("<doc id=\"a&amp;b&quot;c&lt;&gt;\" />"))
        .isEqualTo(AgentDocumentId.of("a&b\"c<>"));
  }

  @Test
  void readsTheIdAttributeNotAnAttributeThatEndsInId() {
    assertThat(
            DocumentTags.idOfSingleTag("<doc toolCallId=\"call_1\" id=\"doc-1\" fileName=\"x\" />"))
        .isEqualTo(AgentDocumentId.of("doc-1"));
    assertThat(DocumentTags.idOfSingleTag("<doc toolCallId=\"call_1\" />")).isNull();
  }

  @Test
  void isNullForTagsInsideText() {
    assertThat(DocumentTags.idOfSingleTag("see <doc id=\"a\" />")).isNull();
    assertThat(DocumentTags.idOfSingleTag("<doc id=\"a\" /> see")).isNull();
    assertThat(DocumentTags.idOfSingleTag("some-id")).isNull();
    assertThat(DocumentTags.idOfSingleTag("")).isNull();
  }

  @Test
  void isNullForTwoTags() {
    assertThat(DocumentTags.idOfSingleTag("<doc id=\"a\" /><doc id=\"b\" />")).isNull();
    assertThat(DocumentTags.idOfSingleTag("<doc id=\"a\" />\n<doc id=\"b\" />")).isNull();
  }

  @Test
  void isNullForATagThatIsNotWellFormedOrHasNoUsableId() {
    assertThat(DocumentTags.idOfSingleTag("<doc id=\"a\">")).isNull();
    assertThat(DocumentTags.idOfSingleTag("<doc fileName=\"x\" />")).isNull();
    assertThat(DocumentTags.idOfSingleTag("<doc id=\" \" />")).isNull();
    assertThat(DocumentTags.idOfSingleTag("<doc />")).isNull();
  }

  @Test
  void isNullForAnEscapedTag() {
    assertThat(DocumentTags.idOfSingleTag("&lt;doc id=\"a\" /&gt;")).isNull();
    assertThat(DocumentTags.idOfSingleTag("\\<doc id=\"a\" />")).isNull();
  }
}
