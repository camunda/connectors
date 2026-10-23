/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;
import io.camunda.connector.idp.extraction.supplier.DocumentAiClientSupplier;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

class DocumentAiClientSupplierTest {

  private final DocumentAiClientSupplier supplier = new DocumentAiClientSupplier();

  @Test
  void getDocumentAiClient_withBearerToken() throws IOException {
    // Given
    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);

    // When/Then
    try (MockedStatic<GcsUtil> gcsUtilMock = mockStatic(GcsUtil.class)) {
      gcsUtilMock.when(() -> GcsUtil.getCredentials(authentication)).thenCallRealMethod();

      DocumentProcessorServiceClient client = supplier.getDocumentAiClient(authentication, "us");

      assertThat(client).isNotNull();
      assertThat(client).isInstanceOf(DocumentProcessorServiceClient.class);
    }
  }

  @Test
  void getDocumentAiClient_withRefreshToken() throws IOException {
    // Given
    GcpAuthentication authentication =
        new GcpAuthentication(
            GcpAuthenticationType.REFRESH,
            null,
            "refresh-token",
            "client-id",
            "client-secret",
            null);

    // When/Then
    try (MockedStatic<GcsUtil> gcsUtilMock = mockStatic(GcsUtil.class)) {
      gcsUtilMock.when(() -> GcsUtil.getCredentials(authentication)).thenCallRealMethod();

      DocumentProcessorServiceClient client = supplier.getDocumentAiClient(authentication, "us");

      assertThat(client).isNotNull();
      assertThat(client).isInstanceOf(DocumentProcessorServiceClient.class);
    }
  }

  @ParameterizedTest
  @CsvSource({
    "eu, eu-documentai.googleapis.com:443",
    "us, us-documentai.googleapis.com:443",
    "' EU ', eu-documentai.googleapis.com:443"
  })
  void buildSettings_shouldUseRegionalEndpoint(String region, String expectedEndpoint)
      throws IOException {
    GcpAuthentication authentication =
        new GcpAuthentication(GcpAuthenticationType.BEARER, "test-token", null, null, null, null);

    var settings = DocumentAiClientSupplier.buildSettings(authentication, region);

    assertThat(settings.getEndpoint()).isEqualTo(expectedEndpoint);
  }

  @ParameterizedTest
  @ValueSource(strings = {"eu", "EU", " EU ", "Eu"})
  void normalizeRegion_shouldTrimAndLowercase(String region) {
    assertThat(DocumentAiClientSupplier.normalizeRegion(region)).isEqualTo("eu");
  }

  @ParameterizedTest
  @NullAndEmptySource
  @ValueSource(strings = {"   "})
  void normalizeRegion_shouldRejectBlankRegion(String region) {
    assertThatThrownBy(() -> DocumentAiClientSupplier.normalizeRegion(region))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining("Document AI region must not be empty");
  }
}
