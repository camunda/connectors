package io.camunda.connector.runtime.instances.reducer;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.runtime.inbound.executable.ConnectorInstances;
import java.util.List;
import org.junit.jupiter.api.Test;

public class ReducerRegistryTest {
  private final ReducerRegistry reducerRegistry = new ReducerRegistry();

  @Test
  public void shouldReturnReducer_whenClassParameter() {
    // given
    var targetClass = new TypeReference<ConnectorInstances>() {};

    // when
    var reducer = reducerRegistry.getReducer(targetClass);

    // then
    assertThat(reducer).isNotNull().isInstanceOf(ConnectorInstancesReducer.class);
  }

  @Test
  public void shouldReturnReducer_whenListParameter() {
    // given
    var targetClass = new TypeReference<List<ConnectorInstances>>() {};

    // when
    var reducer = reducerRegistry.getReducer(targetClass);

    // then
    assertThat(reducer).isNotNull().isInstanceOf(ConnectorInstancesListReducer.class);
  }
}
