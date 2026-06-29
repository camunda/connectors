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
package io.camunda.connector.runtime.core.inbound.activitylog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.ActivityLogTag;
import io.camunda.connector.api.inbound.Severity;
import io.camunda.connector.runtime.core.inbound.ExecutableId;
import org.junit.jupiter.api.Test;

class ActivityLogRegistryTest {

  private final ActivityLogRegistry registry = new ActivityLogRegistry();

  private static ActivityLogEntry entry(ExecutableId id, String message) {
    return new ActivityLogEntry(
        id,
        ActivitySource.RUNTIME,
        Activity.newBuilder()
            .withSeverity(Severity.INFO)
            .withTag(ActivityLogTag.LIFECYCLE)
            .withMessage(message)
            .build());
  }

  @Test
  void remove_removesAllLogsForExecutable() {
    var id = ExecutableId.fromDeduplicationId("someConnector");
    registry.log(entry(id, "activated"));
    registry.log(entry(id, "something happened"));

    registry.remove(id);

    assertThat(registry.getLogs(id)).isEmpty();
  }

  @Test
  void remove_doesNotAffectOtherExecutables() {
    var id1 = ExecutableId.fromDeduplicationId("connector-1");
    var id2 = ExecutableId.fromDeduplicationId("connector-2");
    registry.log(entry(id1, "activated"));
    registry.log(entry(id2, "activated"));

    registry.remove(id1);

    assertThat(registry.getLogs(id1)).isEmpty();
    assertThat(registry.getLogs(id2)).hasSize(1);
  }

  @Test
  void remove_unknownId_isNoOp() {
    var id = ExecutableId.fromDeduplicationId("nonexistent");
    assertThatCode(() -> registry.remove(id)).doesNotThrowAnyException();
    assertThat(registry.getLogs(id)).isEmpty();
  }
}
