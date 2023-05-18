/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gdrive.supliers;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.gdrive.model.MimeTypeUrl;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MimeTypeUrlTest {

  @DisplayName("Should return resource url")
  @Test
  void getResourceUrl_shouldReturnResourceUrl() {
    // Given
    Map<String, String> values = MimeTypeUrl.getValues();
    String ID = "123456";
    values.forEach(
        (type, url) -> {
          // When
          String resourceUrl = MimeTypeUrl.getResourceUrl(type, ID);
          // Then
          assertThat(resourceUrl).isEqualTo(String.format(url, ID));
        });
  }
}
