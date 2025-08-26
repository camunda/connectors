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
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

public class InstanceAwareActivityListReducerTest {
  private final Reducer<List<InstanceAwareModel.InstanceAwareActivity>> reducer =
      new ReducerRegistry().getReducer(new TypeReference<>() {});

  @Test
  public void shouldMergeActivityLogs() {
    List<InstanceAwareModel.InstanceAwareActivity> activities1 =
        List.of(
            new InstanceAwareModel.InstanceAwareActivity(
                Severity.INFO, "TAG1", OffsetDateTime.now(), "Message1", "hostname"),
            new InstanceAwareModel.InstanceAwareActivity(
                Severity.ERROR, "TAG2", OffsetDateTime.now(), "Message2", "hostname"));

    List<InstanceAwareModel.InstanceAwareActivity> activities2 =
        List.of(
            new InstanceAwareModel.InstanceAwareActivity(
                Severity.WARNING, "TAG3", OffsetDateTime.now(), "Message3", "hostname2"),
            new InstanceAwareModel.InstanceAwareActivity(
                Severity.DEBUG, "TAG4", OffsetDateTime.now(), "Message4", "hostname2"));

    List<InstanceAwareModel.InstanceAwareActivity> result =
        reducer.reduce(activities1, activities2);

    assertEquals(4, result.size());
    assertTrue(result.containsAll(activities1));
    assertTrue(result.containsAll(activities2));
  }

  @Test
  public void shouldMergeActivityLogs_whenEmpty() {
    List<InstanceAwareModel.InstanceAwareActivity> activities1 = List.of();
    List<InstanceAwareModel.InstanceAwareActivity> activities2 =
        List.of(
            new InstanceAwareModel.InstanceAwareActivity(
                Severity.WARNING, "TAG3", OffsetDateTime.now(), "Message3", "hostname2"),
            new InstanceAwareModel.InstanceAwareActivity(
                Severity.DEBUG, "TAG4", OffsetDateTime.now(), "Message4", "hostname2"));

    List<InstanceAwareModel.InstanceAwareActivity> result =
        reducer.reduce(activities1, activities2);

    assertEquals(2, result.size());
    assertTrue(result.containsAll(activities2));
  }
}
