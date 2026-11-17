/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class ResponseDocumentSelectionTest {

  private static final AgentDocumentId DOCUMENT = AgentDocumentId.of("doc-1");

  @Test
  void acceptsKeysWithLettersDigitsUnderscoresAndHyphens() {
    for (final var key : List.of("a", "_", "report", "_report-2024_final", "A-b_9")) {
      assertThat(new ResponseDocumentSelection(key, DOCUMENT, null).key()).isEqualTo(key);
    }
  }

  @Test
  void rejectsAKeyThatDoesNotMatchThePattern() {
    for (final var key : List.of("", " ", "1abc", "a b", "a.b", "-a", "ä")) {
      assertThatThrownBy(() -> new ResponseDocumentSelection(key, DOCUMENT, null))
          .isInstanceOf(IllegalArgumentException.class)
          .hasMessage(
              "The key '%s' of a response document must match [A-Za-z_][A-Za-z0-9_-]*"
                  .formatted(key));
    }
  }

  @Test
  void tellsWhetherAKeyMatchesThePattern() {
    for (final var key : List.of("a", "_", "report", "_report-2024_final", "A-b_9")) {
      assertThat(ResponseDocumentSelection.isValidKey(key)).as(key).isTrue();
    }
    for (final var key : List.of("", " ", "1abc", "a b", "a.b", "-a", "ä")) {
      assertThat(ResponseDocumentSelection.isValidKey(key)).as(key).isFalse();
    }
  }

  @Test
  void rejectsAMissingKeyOrDocument() {
    assertThatThrownBy(() -> new ResponseDocumentSelection(null, DOCUMENT, null))
        .isInstanceOf(NullPointerException.class);
    assertThatThrownBy(() -> new ResponseDocumentSelection("a", null, null))
        .isInstanceOf(NullPointerException.class);
  }

  @Test
  void readsAnInvalidPersistedKeyAsAFailure() {
    assertThatThrownBy(() ->
            new ObjectMapper()
                .readValue(
                    "{\"key\":\"a b\",\"document\":\"doc-1\"}", ResponseDocumentSelection.class))
        .hasRootCauseInstanceOf(IllegalArgumentException.class);
  }
}
