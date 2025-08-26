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

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.http.InstanceForwardingHttpClient;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class DefaultInstanceForwardingServiceTest {

  @Test
  public void shouldForwardRequest_whenHeadlessService() throws IOException, InterruptedException {
    // Given
    InstanceForwardingHttpClient mockHttpClient = mock(InstanceForwardingHttpClient.class);
    String method = "POST";
    String path = "/api/forward?param=value&param2=value2";
    ConnectorInstances body =
        new ConnectorInstances(
            "connectorId",
            "connectorName",
            List.of(
                new ActiveInboundConnectorResponse(
                    ExecutableId.fromDeduplicationId("deduplicationId"),
                    "type",
                    "tenantId",
                    List.of(),
                    Map.of("dataKey", "dataValue"),
                    Health.down(),
                    System.currentTimeMillis())));
    DefaultInstanceForwardingService service =
        new DefaultInstanceForwardingService(mockHttpClient, "localhost");
    var mockHttpServletRequest = new MockHttpServletRequest(method, path);
    mockHttpServletRequest.setContent(
        ConnectorsObjectMapperSupplier.getCopy().writeValueAsBytes(body));
    mockHttpServletRequest.addHeader("Authorization", "Bearer token");

    // When
    TypeReference<ConnectorInstances> responseType = new TypeReference<>() {};
    service.forwardAndReduce(mockHttpServletRequest, responseType);

    // Then
    verify(mockHttpClient, times(1))
        .execute(
            "POST",
            "/api/forward?param=value&param2=value2",
            ConnectorsObjectMapperSupplier.getCopy().writeValueAsString(body),
            Map.of("Authorization", "Bearer token"),
            responseType,
            "localhost");
  }
}
