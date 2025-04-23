package io.camunda.connector.runtime.instances.reducer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import java.util.Collection;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InstanceAwareHealthListReducerTest {
  private final Reducer<Collection<InstanceAwareModel.InstanceAwareHealth>> reducer =
      new ReducerRegistry().getReducer(new TypeReference<>() {});

  @Test
  public void shouldMergeHealth() {
    Collection<InstanceAwareModel.InstanceAwareHealth> health1 =
        List.of(new InstanceAwareModel.InstanceAwareHealth(Health.up(), "hostname"));

    Collection<InstanceAwareModel.InstanceAwareHealth> health2 =
        List.of(new InstanceAwareModel.InstanceAwareHealth(Health.down(), "hostname2"));

    Collection<InstanceAwareModel.InstanceAwareHealth> result = reducer.reduce(health1, health2);

    assertEquals(2, result.size());
    assertTrue(result.containsAll(health1));
    assertTrue(result.containsAll(health2));
  }

  @Test
  public void shouldMergeHealth_whenEmpty() {
    Collection<InstanceAwareModel.InstanceAwareHealth> health1 = List.of();
    Collection<InstanceAwareModel.InstanceAwareHealth> health2 =
        List.of(new InstanceAwareModel.InstanceAwareHealth(Health.down(), "hostname2"));

    Collection<InstanceAwareModel.InstanceAwareHealth> result = reducer.reduce(health1, health2);

    assertEquals(1, result.size());
    assertTrue(result.containsAll(health2));
  }
}
