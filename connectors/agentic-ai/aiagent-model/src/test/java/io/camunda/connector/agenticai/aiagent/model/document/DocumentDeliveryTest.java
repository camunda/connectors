/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.model.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class DocumentDeliveryTest {

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {" ", "\t"})
  void aBlankOrAbsentExtensionValueIsReference(String value) {
    assertThat(DocumentDelivery.fromExtension(value)).isEqualTo(DocumentDelivery.REFERENCE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"reference", "Reference", " REFERENCE "})
  void readsReference(String value) {
    assertThat(DocumentDelivery.fromExtension(value)).isEqualTo(DocumentDelivery.REFERENCE);
  }

  @ParameterizedTest
  @ValueSource(strings = {"content", "Content", " CONTENT "})
  void readsContent(String value) {
    assertThat(DocumentDelivery.fromExtension(value)).isEqualTo(DocumentDelivery.CONTENT);
  }

  @Test
  void anUnknownValueFailsAndNamesTheAllowedValues() {
    assertThatThrownBy(() -> DocumentDelivery.fromExtension("contnet"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage(
            "The document delivery 'contnet' is not allowed, use one of: reference, content");
  }
}
