package io.camunda.connector.runtime.instances.service;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
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
    Map<String, String> headers = Map.of("Authorization", "Bearer token");
    DefaultInstanceForwardingService service =
        new DefaultInstanceForwardingService(mockHttpClient, "localhost");
    var mockHttpServletRequest = new MockHttpServletRequest(method, path);
    mockHttpServletRequest.setContent(
        ConnectorsObjectMapperSupplier.getCopy().writeValueAsBytes(body));
    mockHttpServletRequest.addHeader("Authorization", "Bearer token");

    // When
    service.forwardAndReduce(mockHttpServletRequest, new TypeReference<ConnectorInstances>() {});

    // Then
    verify(mockHttpClient, times(1))
        .execute(
            "POST",
            "/api/forward?param=value&param2=value2",
            ConnectorsObjectMapperSupplier.getCopy().writeValueAsString(body),
            headers,
            new TypeReference<ConnectorInstances>() {});
  }
}
