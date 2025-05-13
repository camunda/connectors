/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.supplier;

import static io.camunda.connector.idp.extraction.utils.GcsUtil.getCredentials;

import com.google.cloud.vertexai.Transport;
import com.google.cloud.vertexai.VertexAI;
import com.google.common.collect.ImmutableMap;
import io.camunda.connector.idp.extraction.model.providers.GcpProvider;
import io.camunda.connector.idp.extraction.model.providers.gcp.VertexRequestConfiguration;

public final class VertexAISupplier {

  private VertexAISupplier() {}

  public static VertexAI getVertexAI(GcpProvider baseRequest) {
    var configuration = (VertexRequestConfiguration) baseRequest.getConfiguration();
    return new VertexAI.Builder()
        .setProjectId(configuration.getProjectId())
        .setLocation(configuration.getRegion())
        .setTransport(Transport.REST)
        .setCustomHeaders(ImmutableMap.of())
        .setCredentials(getCredentials(baseRequest.getAuthentication()))
        .build();
  }
}
