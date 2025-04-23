package io.camunda.connector.runtime.instances.reducer;

import static io.camunda.connector.runtime.instances.helpers.ConnectorInstancesListResponseHelper.assertResponse;
import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

public class ConnectorInstancesListReducerTest {
  private final ConnectorInstancesListReducer reducer = new ConnectorInstancesListReducer();

  @ParameterizedTest
  @MethodSource(
      "io.camunda.connector.runtime.instances.helpers.ConnectorInstancesListResponseHelper#getConnectorInstancesListsWithExpectedResult")
  public void shouldReduceAndKeepDifferences(
      List<ConnectorInstances> responseRuntime1,
      List<ConnectorInstances> responseRuntime2,
      List<ConnectorInstances> expectedResult) {
    // given
    // when
    var reducedResponse = reducer.reduce(responseRuntime1, responseRuntime2);

    // then
    assertResponse(reducedResponse, expectedResult);
  }
}
