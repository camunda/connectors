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
package io.camunda.connector.runtime.inbound;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

import io.camunda.connector.api.inbound.*;
import io.camunda.connector.runtime.instances.InstanceAwareModel;
import java.time.ZoneOffset;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
class InboundConnectorRestControllerMultiInstancesTest extends BaseMultiInstancesTest {

  @Test
  public void shouldReturnActivityLogs_whenTypeProvided() {
    ResponseEntity<List<Collection<InstanceAwareModel.InstanceAwareActivity>>> response =
        restTemplate.exchange(
            "http://localhost:" + port1 + "/tenants/tenantId/inbound/ProcessC/id2/logs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    var logs = response.getBody();
    assertEquals(2, logs.size());
    assertThat(
        logs.stream().flatMap(Collection::stream).toList(),
        containsInAnyOrder(
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME1_ACTIVITY1.severity(),
                RUNTIME1_ACTIVITY1.tag(),
                RUNTIME1_ACTIVITY1.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME1_ACTIVITY1.message(),
                "instance1"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME1_ACTIVITY2.severity(),
                RUNTIME1_ACTIVITY2.tag(),
                RUNTIME1_ACTIVITY2.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME1_ACTIVITY2.message(),
                "instance1"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME2_ACTIVITY1.severity(),
                RUNTIME2_ACTIVITY1.tag(),
                RUNTIME2_ACTIVITY1.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME2_ACTIVITY1.message(),
                "instance2"),
            new InstanceAwareModel.InstanceAwareActivity(
                RUNTIME2_ACTIVITY2.severity(),
                RUNTIME2_ACTIVITY2.tag(),
                RUNTIME2_ACTIVITY2.timestamp().withOffsetSameInstant(ZoneOffset.UTC),
                RUNTIME2_ACTIVITY2.message(),
                "instance2")));
  }

  @Test
  public void shouldNotReturnActivityLogs_whenNoLogs() {
    ResponseEntity<List<Collection<InstanceAwareModel.InstanceAwareActivity>>> response =
        restTemplate.exchange(
            "http://localhost:"
                + port1
                + "/tenants/tenantId/inbound/ProcessA/elementIdProcessA/logs",
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<>() {});
    var logs = response.getBody();
    assertTrue(logs.isEmpty());
  }
}
