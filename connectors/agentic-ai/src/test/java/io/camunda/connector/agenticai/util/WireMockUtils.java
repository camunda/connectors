/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.util;

import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.verify;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.stubbing.ServeEvent;
import io.camunda.client.protocol.rest.JobCompletionRequest;
import io.camunda.client.protocol.rest.JobFailRequest;
import org.assertj.core.api.ThrowingConsumer;

public class WireMockUtils {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  public static void assertJobCompletionRequest(ThrowingConsumer<JobCompletionRequest> assertions) {
    awaitJobCompletionRequest();
    assertions.accept(getLastRequest(JobCompletionRequest.class));
  }

  public static void awaitJobCompletionRequest() {
    await()
        .untilAsserted(
            () -> verify(1, postRequestedFor(urlPathMatching("^/v2/jobs/([0-9]+)/completion$"))));
  }

  public static void assertJobFailRequest(ThrowingConsumer<JobFailRequest> assertions) {
    awaitJobFailRequest();
    assertions.accept(getLastRequest(JobFailRequest.class));
  }

  public static void awaitJobFailRequest() {
    await()
        .untilAsserted(
            () -> verify(1, postRequestedFor(urlPathMatching("^/v2/jobs/([0-9]+)/failure$"))));
  }

  public static <T> T getLastRequest(final Class<T> requestType) {
    assertThat(WireMock.getAllServeEvents()).describedAs("WireMock has serve events").isNotEmpty();

    ServeEvent lastServeEvent = WireMock.getAllServeEvents().getLast();
    try {
      return OBJECT_MAPPER.readValue(lastServeEvent.getRequest().getBodyAsString(), requestType);
    } catch (JsonProcessingException e) {
      throw new RuntimeException(e);
    }
  }
}
