/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.supplier;

import static io.camunda.google.supplier.util.GoogleServiceSupplierUtil.getCredentials;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import com.google.common.collect.ImmutableMap;
import io.camunda.connector.idp.extraction.model.GeminiBaseRequest;

public final class VertexAISupplier {

  private VertexAISupplier() {}

  public static VertexAI getVertexAI(GeminiBaseRequest baseRequest) {
    return new VertexAI.Builder()
        .setProjectId(baseRequest.getConfiguration().projectId())
        .setLocation(baseRequest.getConfiguration().region())
        .setTransport(Transport.REST)
        .setCustomHeaders(ImmutableMap.of())
        .setCredentials(getCredentials(baseRequest.getAuthentication()))
        .build();
  }
}
