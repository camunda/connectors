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
package io.camunda.connector.runtime.inbound.state;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.runtime.inbound.state.model.ImportResult;
import io.camunda.connector.runtime.inbound.state.model.ImportResult.ImportType;
import io.camunda.connector.runtime.inbound.state.model.ProcessDefinitionRef;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ProcessStateContainerImplTest {

  private ProcessStateContainerImpl container;

  @BeforeEach
  void setUp() {
    container = new ProcessStateContainerImpl();
  }

  // Helper methods
  private ProcessDefinitionRef processId(String bpmnProcessId, String tenantId) {
    return new ProcessDefinitionRef(bpmnProcessId, tenantId);
  }

  // only for single process imports in tests
  // for multiple processes, use the constructor of ImportResult directly
  private ImportResult importResult(
      ProcessDefinitionRef processId, Set<Long> keys, ImportType type) {
    return new ImportResult(Map.of(processId, keys), type);
  }

  @Nested
  class BasicSingleImportScenarios {

    @Test
    void shouldActivateVersionOnFirstLatestVersionsImport() {
      // given
      var processId = processId("process1", "tenant1");
      var importResult = importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS);

      // when
      var result = container.compareAndUpdate(importResult);

      // then - process1 is affected, active versions are [1]
      assertThat(result.affectedProcesses()).containsKey(processId);
      assertThat(result.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L);
    }

    @Test
    void shouldActivateAllVersionsOnFirstActiveSubscriptionsImport() {
      // given
      var processId = processId("process1", "tenant1");
      var importResult =
          importResult(processId, Set.of(1L, 2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS);

      // when
      var result = container.compareAndUpdate(importResult);

      // then - all three versions should be active
      assertThat(result.affectedProcesses()).containsKey(processId);
      assertThat(result.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void shouldReturnEmptyResultOnEmptyImport() {
      // given
      var importResult = new ImportResult(Map.of(), ImportType.LATEST_VERSIONS);

      // when
      var result = container.compareAndUpdate(importResult);

      // then
      assertThat(result.isEmpty()).isTrue();
    }
  }

  @Nested
  class SequentialLatestVersionsImports {

    @Test
    void shouldActivateNewAndDeactivateOldOnVersionUpgrade() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));

      // then - v1 is now active
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L);

      // when - import v2 as latest
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));

      // then - v2 is active, v1 is no longer active
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(2L);
    }

    @Test
    void shouldHandleMultipleVersionUpgrades() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L);

      // when - import v2 as latest
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(2L);

      // when - import v3 as latest
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(3L), ImportType.LATEST_VERSIONS));

      // then - v3 is the only active version
      assertThat(result3.affectedProcesses().get(processId)).containsExactlyInAnyOrder(3L);
    }
  }

  @Nested
  class SequentialActiveSubscriptionsImports {

    @Test
    void shouldActivateNewSubscriptionVersion() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1, v2 with subscriptions
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);

      // when - import v1, v2, v3 with subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - all three versions are now active
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void shouldDeactivateRemovedSubscriptionVersion() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1, v2, v3 with subscriptions
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L, 3L);

      // when - import only v2, v3 with subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - only v2 and v3 are active now
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(2L, 3L);
    }

    @Test
    void shouldActivateNewAndDeactivateOldWhenSubscriptionsChange() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1, v2 with subscriptions
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);

      // when - import v2, v3 with subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - v2 and v3 are active, v1 was removed
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(2L, 3L);
    }
  }

  @Nested
  class MixedTypeSequentialImports {

    @Test
    void shouldActivateNewVersionWhenLatestThenSubscriptions() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v2 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(2L);

      // when - import v1, v2 with subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - both v1 and v2 should be active
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldKeepOldVersionActiveWhenSubscriptionsThenLatest() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 with subscription
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L);

      // when - import v2 as latest
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));

      // then - both v1 (subscription) and v2 (latest) should be active
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldReactivateVersionWhenLatestFlagRemovedButSubscriptionAdded() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L);

      // when - import v2 as latest (v1 loses latest flag)
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(2L);

      // when - import v1, v2 with subscriptions
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - both v1 and v2 should be active again
      assertThat(result3.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);
    }

    @Test
    void shouldDeactivateVersionOnlyWhenBothFlagsRemoved() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L);

      // when - import v1 with subscription (v1 has both flags now)
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      // no state change - v1 was already active
      assertThat(result2.isEmpty()).isTrue();

      // when - import v2 as latest (v1 loses latest flag but keeps subscription flag)
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      // v1 still active due to subscription, v2 now active as latest
      assertThat(result3.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);

      // when - import empty subscriptions (v1 loses subscription flag)
      var result4 =
          container.compareAndUpdate(
              importResult(processId, Set.of(), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - only v2 should be active now
      assertThat(result4.affectedProcesses().get(processId)).containsExactlyInAnyOrder(2L);
    }
  }

  @Nested
  class MultipleProcessDefinitions {

    @Test
    void shouldHandleMultipleProcessDefinitionsSimultaneously() {
      // given
      var process1 = processId("process1", "tenant1");
      var process2 = processId("process2", "tenant1");
      var importResult =
          new ImportResult(
              Map.of(
                  process1, Set.of(1L),
                  process2, Set.of(2L)),
              ImportType.LATEST_VERSIONS);

      // when
      var result = container.compareAndUpdate(importResult);

      // then - both processes should be affected
      assertThat(result.affectedProcesses()).hasSize(2);
      assertThat(result.affectedProcesses().get(process1)).containsExactlyInAnyOrder(1L);
      assertThat(result.affectedProcesses().get(process2)).containsExactlyInAnyOrder(2L);
    }

    @Test
    void shouldHandleSequentialImportsForDifferentProcessesIndependently() {
      // given
      var process1 = processId("process1", "tenant1");
      var process2 = processId("process2", "tenant1");

      // when - import v1 for process1
      var result1 =
          container.compareAndUpdate(
              importResult(process1, Set.of(1L), ImportType.LATEST_VERSIONS));
      assertThat(result1.affectedProcesses().get(process1)).containsExactlyInAnyOrder(1L);

      // when - import v1 for both process1 and process2
      var result2 =
          container.compareAndUpdate(
              new ImportResult(
                  Map.of(process1, Set.of(1L), process2, Set.of(1L)), ImportType.LATEST_VERSIONS));
      // only process2 is affected (process1 state unchanged)
      assertThat(result2.affectedProcesses()).hasSize(1);
      assertThat(result2.affectedProcesses().get(process2)).containsExactlyInAnyOrder(1L);

      // when - upgrade process1 to v2
      var result3 =
          container.compareAndUpdate(
              new ImportResult(
                  Map.of(process1, Set.of(2L), process2, Set.of(1L)), ImportType.LATEST_VERSIONS));

      // then - only process1 should be affected
      assertThat(result3.affectedProcesses()).hasSize(1);
      assertThat(result3.affectedProcesses().get(process1)).containsExactlyInAnyOrder(2L);
    }
  }

  @Nested
  class MultiTenancyScenarios {

    @Test
    void shouldHandleVersionUpgradeInOneTenantWithoutAffectingAnother() {
      // given
      var processTenant1 = processId("process1", "tenant1");
      var processTenant2 = processId("process1", "tenant2");

      // when - both tenants start with v1
      container.compareAndUpdate(
          new ImportResult(
              Map.of(
                  processTenant1, Set.of(1L),
                  processTenant2, Set.of(1L)),
              ImportType.LATEST_VERSIONS));

      // when - only tenant1 upgrades to v2
      var result =
          container.compareAndUpdate(
              new ImportResult(
                  Map.of(
                      processTenant1, Set.of(2L),
                      processTenant2, Set.of(1L)),
                  ImportType.LATEST_VERSIONS));

      // then - only tenant1 should be affected
      assertThat(result.affectedProcesses()).hasSize(1);
      assertThat(result.affectedProcesses().get(processTenant1)).containsExactlyInAnyOrder(2L);
    }
  }

  @Nested
  class ComplexSequentialScenarios {

    @Test
    void shouldHandleVersionFlapping() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1, v2 with subscriptions
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);

      // when - remove v2
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L);

      // when - re-add v2
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - both v1 and v2 should be active again
      assertThat(result3.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);
    }
  }

  @Nested
  class EdgeCases {

    @Test
    void shouldBeIdempotentWhenImportingSameVersionsMultipleTimes() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L);

      // when - import v1 as latest again
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));

      // then - no changes (empty result)
      assertThat(result2.isEmpty()).isTrue();

      // when - import v1 as latest third time
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));

      // then - still no changes
      assertThat(result3.isEmpty()).isTrue();
    }

    @Test
    void shouldHandleEmptySubscriptionsAfterHavingActiveSubscriptions() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1, v2 with subscriptions
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);

      // when - import empty subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - process is affected with empty active versions set
      assertThat(result2.affectedProcesses()).containsKey(processId);
      assertThat(result2.affectedProcesses().get(processId)).isEmpty();
    }

    @Test
    void shouldHandleLatestVersionAlsoInActiveSubscriptions() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v2 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      assertThat(result1.affectedProcesses().get(processId)).containsExactlyInAnyOrder(2L);

      // when - import v1, v2 with subscriptions (v2 is both latest and has subscription)
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result2.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 2L);

      // when - remove v2 from subscriptions (but it's still latest)
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - no state change: v2 is still active (latest), v1 is still active (subscription)
      assertThat(result3.isEmpty()).isTrue();

      // when - v3 becomes latest (v2 loses latest flag and has no subscription)
      var result4 =
          container.compareAndUpdate(
              importResult(processId, Set.of(3L), ImportType.LATEST_VERSIONS));

      // then - v1 (subscription) and v3 (latest) should be active, v2 removed
      assertThat(result4.affectedProcesses().get(processId)).containsExactlyInAnyOrder(1L, 3L);
    }

    @Test
    void shouldHandleLargeNumberOfVersions() {
      // given
      var processId = processId("process1", "tenant1");
      var largeVersionSet =
          Set.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);

      // when
      var result =
          container.compareAndUpdate(
              importResult(processId, largeVersionSet, ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then
      assertThat(result.affectedProcesses().get(processId)).hasSize(15);

      // when - remove half of them
      var smallerSet = Set.of(8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
      var result2 =
          container.compareAndUpdate(
              importResult(processId, smallerSet, ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then
      assertThat(result2.affectedProcesses().get(processId)).hasSize(8);
      assertThat(result2.affectedProcesses().get(processId))
          .containsExactlyInAnyOrder(8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
    }
  }
}
