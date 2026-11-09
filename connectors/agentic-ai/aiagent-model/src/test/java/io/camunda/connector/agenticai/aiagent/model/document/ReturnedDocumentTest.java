/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import org.junit.jupiter.api.Test;

class ReturnedDocumentTest {

  @Test
  void keepsTheReferenceItWasCreatedWith() {
    final var reference = new HashMap<String, Object>();
    reference.put("camunda.document.type", "camunda");
    reference.put("documentId", "a");
    reference.put("contentHash", null);

    final var returned = new ReturnedDocument(reference, null);
    reference.put("documentId", "b");

    final var expected = new HashMap<String, Object>();
    expected.put("camunda.document.type", "camunda");
    expected.put("documentId", "a");
    expected.put("contentHash", null);
    assertThat(returned.document()).isEqualTo(expected);
    assertThatThrownBy(() -> returned.document().put("documentId", "c"))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> returned.document().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
