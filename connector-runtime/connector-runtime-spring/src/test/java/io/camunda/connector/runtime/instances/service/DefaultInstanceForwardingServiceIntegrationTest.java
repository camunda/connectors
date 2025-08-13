/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.camunda.connector.runtime.instances.service;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static io.camunda.connector.runtime.instances.helpers.ActiveInboundConnectorResponseHelper.assertExpectedResponse;
import static io.camunda.connector.runtime.instances.helpers.ActiveInboundConnectorResponseHelper.createResponse;
import static io.camunda.connector.runtime.instances.helpers.ConnectorInstancesListResponseHelper.assertResponse;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.github.tomakehurst.wiremock.WireMockServer;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import io.camunda.connector.test.SlowTest;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;

@SlowTest
public class DefaultInstanceForwardingServiceIntegrationTest {
  private static final WireMockServer runtime1 = new WireMockServer(options().dynamicPort());
  private static final WireMockServer runtime2 = new WireMockServer(options().dynamicPort());

  private static final InstanceForwardingHttpClient instanceForwardingHttpClient =
      new InstanceForwardingHttpClient(
          HttpClient.newHttpClient(),
          (path) -> List.of(runtime1.baseUrl() + path, runtime2.baseUrl() + path),
          ConnectorsObjectMapperSupplier.getCopy());

  @BeforeAll
  static void setup() {
    runtime1.start();
    runtime2.start();
  }

  @AfterEach
  public void tearDown() {
    runtime1.resetAll();
    runtime2.resetAll();
  }

  private static void stubRuntimeWith(WireMockServer runtime, Object body)
      throws JsonProcessingException {
    runtime.stubFor(
        get(urlPathMatching("/api/forward"))
            .withQueryParam("param", equalTo("value"))
            .withHeader("Authorization", equalTo("Bearer xyz"))
            .willReturn(
                ok().withBody(ConnectorsObjectMapperSupplier.getCopy().writeValueAsString(body))));
  }

  @Nested
  class GetSingleActiveInboundConnectorResponseTests {
    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.instances.helpers.ActiveInboundConnectorResponseHelper#getConnectorInstancesWithExpectedResult")
    public void shouldReduceAndKeepDownStatus_whenMultipleResponsesWithUpAndDown(
        ActiveInboundConnectorResponse responseRuntime1,
        ActiveInboundConnectorResponse responseRuntime2,
        ActiveInboundConnectorResponse expectedResult)
        throws IOException, InterruptedException {
      // given
      stubRuntimeWith(runtime1, responseRuntime1);
      stubRuntimeWith(runtime2, responseRuntime2);
      DefaultInstanceForwardingService service =
          new DefaultInstanceForwardingService(instanceForwardingHttpClient, "localhost");
      var mockHttpServletRequest = new MockHttpServletRequest("GET", "/api/forward");
      mockHttpServletRequest.setQueryString("param=value");
      mockHttpServletRequest.addHeader("Authorization", "Bearer xyz");

      // when
      var reducedResponse =
          service.forwardAndReduce(
              mockHttpServletRequest, new TypeReference<ActiveInboundConnectorResponse>() {});

      // then
      assertExpectedResponse(expectedResult, reducedResponse);
    }
  }

  @Nested
  class GetAllConnectorInstancesTests {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.instances.helpers.ConnectorInstancesListResponseHelper#getConnectorInstancesListsWithExpectedResult")
    public void shouldReduceAndKeepDownStatus_whenMultipleResponsesWithUpAndDown(
        List<ConnectorInstances> connectorInstances1,
        List<ConnectorInstances> connectorInstances2,
        List<ConnectorInstances> expectedResult)
        throws IOException, InterruptedException {
      // given
      stubRuntimeWith(runtime1, connectorInstances1);
      stubRuntimeWith(runtime2, connectorInstances2);
      DefaultInstanceForwardingService service =
          new DefaultInstanceForwardingService(instanceForwardingHttpClient, "localhost");
      var mockHttpServletRequest = new MockHttpServletRequest("GET", "/api/forward");
      mockHttpServletRequest.setQueryString("param=value");
      mockHttpServletRequest.addHeader("Authorization", "Bearer xyz");

      // when
      var reducedResponse =
          service.forwardAndReduce(
              mockHttpServletRequest, new TypeReference<List<ConnectorInstances>>() {});

      // then
      assertResponse(reducedResponse, expectedResult);
    }
  }

  @Nested
  class GetSingleConnectorInstanceTests {

    @ParameterizedTest
    @MethodSource(
        "io.camunda.connector.runtime.instances.helpers.ActiveInboundConnectorResponseHelper#getConnectorInstancesWithExpectedResult")
    public void shouldReduceAndKeepDownStatus_whenMultipleResponsesWithUpAndDown(
        ActiveInboundConnectorResponse responseRuntime1,
        ActiveInboundConnectorResponse responseRuntime2,
        ActiveInboundConnectorResponse expectedResult)
        throws IOException, InterruptedException {
      // given
      var connectorInstances1 =
          new ConnectorInstances(
              "webhook",
              "Webhook connector",
              List.of(
                  responseRuntime1,
                  createResponse(
                      ExecutableId.fromDeduplicationId("onlyInRuntime1"),
                      Health.down(),
                      System.currentTimeMillis())));
      var connectorInstances2 =
          new ConnectorInstances(
              "webhook",
              "Webhook connector",
              List.of(
                  responseRuntime2,
                  createResponse(
                      ExecutableId.fromDeduplicationId("onlyInRuntime2"),
                      Health.up(),
                      System.currentTimeMillis())));
      stubRuntimeWith(runtime1, connectorInstances1);
      stubRuntimeWith(runtime2, connectorInstances2);
      DefaultInstanceForwardingService service =
          new DefaultInstanceForwardingService(instanceForwardingHttpClient, "localhost");
      var mockHttpServletRequest = new MockHttpServletRequest("GET", "/api/forward");
      mockHttpServletRequest.setQueryString("param=value");
      mockHttpServletRequest.addHeader("Authorization", "Bearer xyz");

      // when
      var reducedResponse =
          service.forwardAndReduce(
              mockHttpServletRequest, new TypeReference<ConnectorInstances>() {});

      // then
      assertExpectedResponse(expectedResult, reducedResponse, connectorInstances1);
    }
  }

  @Nested
  class GetAllActivityTests {

    @Test
    public void shouldMergeAllActivityLogs() throws IOException {
      // given
      OffsetDateTime fixedTimestamp = OffsetDateTime.parse("2025-04-22T09:37:44.740894Z");

      List<InstanceAwareModel.InstanceAwareActivity> activities1 =
          List.of(
              new InstanceAwareModel.InstanceAwareActivity(
                  Severity.INFO, "TAG1", fixedTimestamp, "Message1", "runtime1"),
              new InstanceAwareModel.InstanceAwareActivity(
                  Severity.DEBUG, "TAG2", fixedTimestamp, "Message2", "runtime1"));

      List<InstanceAwareModel.InstanceAwareActivity> activities2 =
          List.of(
              new InstanceAwareModel.InstanceAwareActivity(
                  Severity.WARNING, "TAG3", fixedTimestamp, "Message3", "runtime2"),
              new InstanceAwareModel.InstanceAwareActivity(
                  Severity.ERROR, "TAG4", fixedTimestamp, "Message4", "runtime2"));

      stubRuntimeWith(runtime1, activities1);
      stubRuntimeWith(runtime2, activities2);
      DefaultInstanceForwardingService service =
          new DefaultInstanceForwardingService(instanceForwardingHttpClient, "localhost");
      var mockHttpServletRequest = new MockHttpServletRequest("GET", "/api/forward");
      mockHttpServletRequest.setQueryString("param=value");
      mockHttpServletRequest.addHeader("Authorization", "Bearer xyz");

      // when
      var reducedResponse =
          service.forwardAndReduce(
              mockHttpServletRequest,
              new TypeReference<List<InstanceAwareModel.InstanceAwareActivity>>() {});

      // then
      assertThat(reducedResponse).isNotNull();
      assertThat(reducedResponse).containsAll(activities1);
      assertThat(reducedResponse).containsAll(activities2);
    }
  }
}
