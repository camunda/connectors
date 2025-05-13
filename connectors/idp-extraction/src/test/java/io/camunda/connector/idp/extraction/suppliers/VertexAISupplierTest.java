/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.suppliers;

import static io.camunda.connector.idp.extraction.utils.GcsUtil.getCredentials;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.vertexai.VertexAI;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthentication;
import io.camunda.connector.idp.extraction.model.providers.gcp.GcpAuthenticationType;
import io.camunda.connector.idp.extraction.model.providers.gcp.VertexRequestConfiguration;
import io.camunda.connector.idp.extraction.supplier.VertexAISupplier;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class VertexAISupplierTest {

  private static final String PROJECT_ID = "test-project";
  private static final String REGION = "test-region";
  private static final String BUCKET_NAME = "test-bucket";

  @Test
  void getVertexAI_withBearerToken() throws IOException {
    // Given
    GcpProvider baseRequest =
        createBaseRequest(
            new GcpAuthentication(
                GcpAuthenticationType.BEARER, "test-token", null, null, null, null));

    // When
    VertexAI vertexAI = VertexAISupplier.getVertexAI(baseRequest);

    // Then
    assertThat(vertexAI).isNotNull();
    assertThat(vertexAI).isInstanceOf(VertexAI.class);
    assertThat(vertexAI.getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(vertexAI.getLocation()).isEqualTo(REGION);
    assertThat(vertexAI.getCredentials())
        .isEqualTo(getCredentials(baseRequest.getAuthentication()));
  }

  @Test
  void getVertexAI_withRefreshToken() throws IOException {
    // Given
    GcpProvider baseRequest =
        createBaseRequest(
            new GcpAuthentication(
                GcpAuthenticationType.REFRESH,
                null,
                "refresh-token",
                "client-id",
                "client-secret",
                null));

    // When
    VertexAI vertexAI = VertexAISupplier.getVertexAI(baseRequest);

    // Then
    assertThat(vertexAI).isNotNull();
    assertThat(vertexAI).isInstanceOf(VertexAI.class);
    assertThat(vertexAI.getProjectId()).isEqualTo(PROJECT_ID);
    assertThat(vertexAI.getLocation()).isEqualTo(REGION);
    assertThat(vertexAI.getCredentials())
        .isEqualTo(getCredentials(baseRequest.getAuthentication()));
  }

  private GcpProvider createBaseRequest(GcpAuthentication authentication) {
    VertexRequestConfiguration configuration =
        new VertexRequestConfiguration(REGION, PROJECT_ID, BUCKET_NAME);

    GcpProvider baseRequest = new GcpProvider();
    baseRequest.setAuthentication(authentication);
    baseRequest.setConfiguration(configuration);
    return baseRequest;
  }
}
