/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.suppliers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

import com.google.cloud.documentai.v1.DocumentProcessorServiceClient;
import io.camunda.connector.idp.extraction.model.providers.DocumentAIProvider;
import io.camunda.connector.idp.extraction.model.providers.DocumentAiRequestConfiguration;
import io.camunda.connector.idp.extraction.model.providers.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.GcpAuthenticationType;
import io.camunda.connector.idp.extraction.supplier.DocumentAiClientSupplier;
import io.camunda.connector.idp.extraction.utils.GcsUtil;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class DocumentAiClientSupplierTest {

  private static final String PROJECT_ID = "test-project";
  private static final String REGION = "test-region";
  private static final String PROCESSOR_ID = "test-processor";

  private final DocumentAiClientSupplier supplier = new DocumentAiClientSupplier();

  @Test
  void getDocumentAiClient_withBearerToken() throws IOException {
    // Given
    DocumentAIProvider baseRequest =
        createBaseRequest(
            new GcpAuthentication(
                GcpAuthenticationType.BEARER, "test-token", null, null, null, null));

    // When/Then
    try (MockedStatic<GcsUtil> gcsUtilMock = mockStatic(GcsUtil.class)) {
      gcsUtilMock
          .when(() -> GcsUtil.getCredentials(baseRequest.getAuthentication()))
          .thenCallRealMethod();

      DocumentProcessorServiceClient client = supplier.getDocumentAiClient(baseRequest);

      assertThat(client).isNotNull();
      assertThat(client).isInstanceOf(DocumentProcessorServiceClient.class);
    }
  }

  @Test
  void getDocumentAiClient_withRefreshToken() throws IOException {
    // Given
    DocumentAIProvider baseRequest =
        createBaseRequest(
            new GcpAuthentication(
                GcpAuthenticationType.REFRESH,
                null,
                "refresh-token",
                "client-id",
                "client-secret",
                null));

    // When/Then
    try (MockedStatic<GcsUtil> gcsUtilMock = mockStatic(GcsUtil.class)) {
      gcsUtilMock
          .when(() -> GcsUtil.getCredentials(baseRequest.getAuthentication()))
          .thenCallRealMethod();

      DocumentProcessorServiceClient client = supplier.getDocumentAiClient(baseRequest);

      assertThat(client).isNotNull();
      assertThat(client).isInstanceOf(DocumentProcessorServiceClient.class);
    }
  }

  private DocumentAIProvider createBaseRequest(GcpAuthentication authentication) {
    DocumentAiRequestConfiguration configuration =
        new DocumentAiRequestConfiguration(REGION, PROJECT_ID, PROCESSOR_ID);

    DocumentAIProvider baseRequest = new DocumentAIProvider();
    baseRequest.setAuthentication(authentication);
    baseRequest.setConfiguration(configuration);
    return baseRequest;
  }
}
