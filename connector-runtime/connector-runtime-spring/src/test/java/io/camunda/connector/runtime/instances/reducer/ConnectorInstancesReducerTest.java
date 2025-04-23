package io.camunda.connector.runtime.instances.reducer;

import static io.camunda.connector.runtime.instances.helpers.ActiveInboundConnectorResponseHelper.assertExpectedResponse;
import static io.camunda.connector.runtime.instances.helpers.ActiveInboundConnectorResponseHelper.createResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import io.camunda.connector.runtime.inbound.controller.ActiveInboundConnectorResponse;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ConnectorInstancesReducerTest {
  private final ConnectorInstancesReducer reducer = new ConnectorInstancesReducer();

  @ParameterizedTest
  @MethodSource(
      "io.camunda.connector.runtime.instances.helpers.ActiveInboundConnectorResponseHelper#getConnectorInstancesWithExpectedResult")
  public void shouldReduceAndKeepDifferences(
      ActiveInboundConnectorResponse responseRuntime1,
      ActiveInboundConnectorResponse responseRuntime2,
      ActiveInboundConnectorResponse expectedResult) {
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

    // when
    var reducedResponse = reducer.reduce(connectorInstances1, connectorInstances2);

    // then
    assertExpectedResponse(expectedResult, reducedResponse, connectorInstances1);
  }

  @Test
  public void shouldThrowException_whenReducingDifferentConnectorTypes() {
    // given
    var connectorInstances1 = new ConnectorInstances("webhook", "Webhook connector", List.of());
    var connectorInstances2 = new ConnectorInstances("OTHER", "OTHER connector", List.of());

    // when/then
    var exception =
        org.junit.jupiter.api.Assertions.assertThrows(
            IllegalArgumentException.class,
            () -> reducer.reduce(connectorInstances1, connectorInstances2));

    assertThat(exception)
        .hasMessageContaining(
            "Cannot reduce ConnectorInstances with different connector IDs: webhook and OTHER");
  }
}
