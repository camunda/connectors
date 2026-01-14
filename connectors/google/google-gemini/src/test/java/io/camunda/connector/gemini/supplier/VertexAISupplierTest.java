/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.supplier;

import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getCredentials;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import io.camunda.connector.gemini.model.GeminiRequest;
import io.camunda.connector.gemini.model.GeminiRequestData;
import io.camunda.connector.gemini.model.ModelVersion;
import io.camunda.google.model.Authentication;
import io.camunda.google.model.AuthenticationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class VertexAISupplierTest {

  private GeminiRequest geminiRequest;

  @BeforeEach
  public void setUp() {
    geminiRequest = new GeminiRequest();
    geminiRequest.setAuthentication(
        new Authentication(AuthenticationType.BEARER, "barer", "", "", ""));
    geminiRequest.setInput(getGeminiRequestData());
  }

  @Test
  void getVertexAI() throws Exception {
    GeminiRequestData requestData = geminiRequest.getInput();
    var vertexAIResult = VertexAISupplier.getVertexAI(geminiRequest);

    assertThat(vertexAIResult.getProjectId()).isEqualTo(requestData.projectId());
    assertThat(vertexAIResult.getLocation()).isEqualTo(requestData.region());
    assertThat(vertexAIResult.getCredentials())
        .isEqualTo(getCredentials(geminiRequest.getAuthentication()));
  }

  private GeminiRequestData getGeminiRequestData() {
    return new GeminiRequestData(
        "project",
        "region",
        ModelVersion.GEMINI_2_5_FLASH,
        null, // customModelName
        List.of("text"),
        "systemInstr",
        false,
        "path",
        false,
        null,
        null,
        null,
        null,
        List.of("stop"),
        1,
        1,
        2,
        3,
        1,
        List.of("function call"));
  }
}
