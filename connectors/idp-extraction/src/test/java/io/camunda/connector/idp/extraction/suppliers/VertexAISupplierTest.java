/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.suppliers;

import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getCredentials;
import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.vertexai.VertexAI;
import io.camunda.connector.idp.extraction.model.GeminiBaseRequest;
import io.camunda.connector.idp.extraction.model.GeminiRequestConfiguration;
import io.camunda.connector.idp.extraction.supplier.VertexAISupplier;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.AuthenticationType;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class VertexAISupplierTest {

  private static final String PROJECT_ID = "test-project";
  private static final String REGION = "test-region";
  private static final String BUCKET_NAME = "test-bucket";

  @Test
  void getVertexAI_withBearerToken() throws IOException {
    // Given
    GeminiBaseRequest baseRequest =
        createBaseRequest(
            new Authentication(AuthenticationType.BEARER, "test-token", null, null, null));

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
    GeminiBaseRequest baseRequest =
        createBaseRequest(
            new Authentication(
                AuthenticationType.REFRESH, null, "refresh-token", "client-id", "client-secret"));

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

  private GeminiBaseRequest createBaseRequest(Authentication authentication) {
    GeminiRequestConfiguration configuration =
        new GeminiRequestConfiguration(REGION, PROJECT_ID, BUCKET_NAME, null, null);

    GeminiBaseRequest baseRequest = new GeminiBaseRequest();
    baseRequest.setAuthentication(authentication);
    baseRequest.setConfiguration(configuration);
    return baseRequest;
  }
}
