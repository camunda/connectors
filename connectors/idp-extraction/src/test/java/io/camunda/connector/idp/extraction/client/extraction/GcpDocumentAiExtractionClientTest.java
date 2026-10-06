/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.client.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import io.camunda.connector.api.error.ConnectorInputException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class GcpDocumentAiExtractionClientTest {

  private static final GoogleCredentials CREDENTIALS =
      GoogleCredentials.create(new AccessToken("test-token", null));

  @ParameterizedTest
  @CsvSource({"eu, eu-documentai.googleapis.com:443", "us, us-documentai.googleapis.com:443"})
  void buildSettings_shouldUseRegionalEndpoint(String region, String expectedEndpoint)
      throws Exception {
    var settings = GcpDocumentAiExtractionClient.buildSettings(CREDENTIALS, region);

    assertThat(settings.getEndpoint()).isEqualTo(expectedEndpoint);
  }

  @ParameterizedTest
  @ValueSource(strings = {"eu", "EU", " EU ", "Eu"})
  void normalizeRegion_shouldTrimAndLowercase(String region) {
    assertThat(GcpDocumentAiExtractionClient.normalizeRegion(region)).isEqualTo("eu");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void normalizeRegion_shouldRejectBlankRegion(String region) {
    assertThatThrownBy(() -> GcpDocumentAiExtractionClient.normalizeRegion(region))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Document AI region must not be empty");
  }

  @Test
  void constructor_shouldRejectBlankRegion() {
    assertThatThrownBy(
            () -> new GcpDocumentAiExtractionClient(CREDENTIALS, "project", " ", "processor"))
        .isInstanceOf(ConnectorInputException.class);
  }
}
