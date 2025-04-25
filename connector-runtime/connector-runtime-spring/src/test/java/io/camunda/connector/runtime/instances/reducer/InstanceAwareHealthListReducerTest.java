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
package io.camunda.connector.runtime.instances.reducer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InstanceAwareHealthListReducerTest {
  private final Reducer<List<InstanceAwareModel.InstanceAwareHealth>> reducer =
      new ReducerRegistry().getReducer(new TypeReference<>() {});

  @Test
  public void shouldMergeHealth() {
    Health up = Health.up();
    List<InstanceAwareModel.InstanceAwareHealth> health1 =
        List.of(
            new InstanceAwareModel.InstanceAwareHealth(
                up.getStatus(), up.getError(), up.getDetails(), "hostname"));

    Health down = Health.down();
    List<InstanceAwareModel.InstanceAwareHealth> health2 =
        List.of(
            new InstanceAwareModel.InstanceAwareHealth(
                down.getStatus(), down.getError(), down.getDetails(), "hostname2"));

    List<InstanceAwareModel.InstanceAwareHealth> result = reducer.reduce(health1, health2);

    assertEquals(2, result.size());
    assertTrue(result.containsAll(health1));
    assertTrue(result.containsAll(health2));
  }

  @Test
  public void shouldMergeHealth_whenEmpty() {
    List<InstanceAwareModel.InstanceAwareHealth> health1 = List.of();
    Health down = Health.down();
    List<InstanceAwareModel.InstanceAwareHealth> health2 =
        List.of(
            new InstanceAwareModel.InstanceAwareHealth(
                down.getStatus(), down.getError(), down.getDetails(), "hostname2"));

    List<InstanceAwareModel.InstanceAwareHealth> result = reducer.reduce(health1, health2);

    assertEquals(1, result.size());
    assertTrue(result.containsAll(health2));
  }
}
