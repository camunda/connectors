/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.supplier;

import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getCredentials;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import com.google.common.collect.ImmutableMap;
import io.camunda.connector.gemini.model.GeminiRequest;
import io.camunda.connector.gemini.model.GeminiRequestData;

public final class VertexAISupplier {

  private VertexAISupplier() {}

  public static VertexAI getVertexAI(GeminiRequest geminiRequest) {
    GeminiRequestData requestData = geminiRequest.getInput();
    return new VertexAI.Builder()
        .setProjectId(requestData.projectId())
        .setLocation(requestData.region())
        .setTransport(Transport.REST)
        .setCustomHeaders(ImmutableMap.of())
        .setCredentials(getCredentials(geminiRequest.getAuthentication()))
        .build();
  }
}
