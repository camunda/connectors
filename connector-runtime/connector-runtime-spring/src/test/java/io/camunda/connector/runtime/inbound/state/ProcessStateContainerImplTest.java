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
import io.camunda.connector.runtime.inbound.state.model.StateUpdateResult.ProcessDefinitionRefAndKey;
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

  private ProcessDefinitionRefAndKey processIdAndKey(
      String bpmnProcessId, String tenantId, Long key) {
    return new ProcessDefinitionRefAndKey(new ProcessDefinitionRef(bpmnProcessId, tenantId), key);
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

      // then
      assertThat(result.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
      assertThat(result.toDeactivate()).isEmpty();
    }

    @Test
    void shouldActivateAllVersionsOnFirstActiveSubscriptionsImport() {
      // given
      var processId = processId("process1", "tenant1");
      var importResult =
          importResult(processId, Set.of(1L, 2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS);

      // when
      var result = container.compareAndUpdate(importResult);

      // then
      assertThat(result.toActivate())
          .containsExactlyInAnyOrder(
              processIdAndKey("process1", "tenant1", 1L),
              processIdAndKey("process1", "tenant1", 2L),
              processIdAndKey("process1", "tenant1", 3L));
      assertThat(result.toDeactivate()).isEmpty();
    }

    @Test
    void shouldReturnEmptyResultOnEmptyImport() {
      // given
      var importResult = new ImportResult(Map.of(), ImportType.LATEST_VERSIONS);

      // when
      var result = container.compareAndUpdate(importResult);

      // then
      assertThat(result.toActivate()).isEmpty();
      assertThat(result.toDeactivate()).isEmpty();
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

      // then
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
      assertThat(result1.toDeactivate()).isEmpty();

      // when - import v2 as latest
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));

      // then
      assertThat(result2.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
      assertThat(result2.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
    }

    @Test
    void shouldHandleMultipleVersionUpgrades() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - import v2 as latest
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      assertThat(result2.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
      assertThat(result2.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - import v3 as latest
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(3L), ImportType.LATEST_VERSIONS));

      // then
      assertThat(result3.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 3L));
      assertThat(result3.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
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
      assertThat(result1.toActivate()).hasSize(2);

      // when - import v1, v2, v3 with subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then
      assertThat(result2.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 3L));
      assertThat(result2.toDeactivate()).isEmpty();
    }

    @Test
    void shouldDeactivateRemovedSubscriptionVersion() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1, v2, v3 with subscriptions
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.toActivate()).hasSize(3);

      // when - import only v2, v3 with subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then
      assertThat(result2.toActivate()).isEmpty();
      assertThat(result2.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
    }

    @Test
    void shouldActivateNewAndDeactivateOldWhenSubscriptionsChange() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1, v2 with subscriptions
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.toActivate()).hasSize(2);

      // when - import v2, v3 with subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L, 3L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then
      assertThat(result2.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 3L));
      assertThat(result2.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
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
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));

      // when - import v1, v2 with subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - v1 should be activated, v2 stays active (now has both flags)
      assertThat(result2.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
      assertThat(result2.toDeactivate()).isEmpty();
    }

    @Test
    void shouldKeepOldVersionActiveWhenSubscriptionsThenLatest() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 with subscription
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - import v2 as latest
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));

      // then - v2 activated, v1 stays active (still has subscription flag)
      assertThat(result2.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
      assertThat(result2.toDeactivate()).isEmpty();
    }

    @Test
    void shouldReactivateVersionWhenLatestFlagRemovedButSubscriptionAdded() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - import v2 as latest (v1 loses latest flag)
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      assertThat(result2.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - import v1, v2 with subscriptions
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - v1 should be re-activated
      assertThat(result3.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
      assertThat(result3.toDeactivate()).isEmpty();
    }

    @Test
    void shouldDeactivateVersionOnlyWhenBothFlagsRemoved() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - import v1 with subscription (v1 has both flags now)
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result2.toActivate()).isEmpty();
      assertThat(result2.toDeactivate()).isEmpty();

      // when - import v2 as latest (v1 loses latest flag but keeps subscription flag)
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      assertThat(result3.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
      assertThat(result3.toDeactivate()).isEmpty(); // v1 still active due to subscription

      // when - import empty subscriptions (v1 loses subscription flag)
      var result4 =
          container.compareAndUpdate(
              importResult(processId, Set.of(), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - v1 should be deactivated now
      assertThat(result4.toActivate()).isEmpty();
      assertThat(result4.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
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

      // then
      assertThat(result.toActivate())
          .containsExactlyInAnyOrder(
              processIdAndKey("process1", "tenant1", 1L),
              processIdAndKey("process2", "tenant1", 2L));
      assertThat(result.toDeactivate()).isEmpty();
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
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - import v1 for process2
      var result2 =
          container.compareAndUpdate(
              new ImportResult(
                  Map.of(process1, Set.of(1L), process2, Set.of(1L)), ImportType.LATEST_VERSIONS));
      assertThat(result2.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process2", "tenant1", 1L));

      // when - upgrade process1 to v2
      var result3 =
          container.compareAndUpdate(
              new ImportResult(
                  Map.of(process1, Set.of(2L), process2, Set.of(1L)), ImportType.LATEST_VERSIONS));

      // then - only process1 should be affected
      assertThat(result3.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
      assertThat(result3.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
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

      // then - only tenant1's v1 should be deactivated
      assertThat(result.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
      assertThat(result.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));
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
      assertThat(result1.toActivate()).hasSize(2);

      // when - remove v2
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result2.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));

      // when - re-add v2
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - v2 should be re-activated
      assertThat(result3.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
      assertThat(result3.toDeactivate()).isEmpty();
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
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - import v1 as latest again
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));

      // then - no changes
      assertThat(result2.toActivate()).isEmpty();
      assertThat(result2.toDeactivate()).isEmpty();

      // when - import v1 as latest third time
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.LATEST_VERSIONS));

      // then - still no changes
      assertThat(result3.toActivate()).isEmpty();
      assertThat(result3.toDeactivate()).isEmpty();
    }

    @Test
    void shouldHandleEmptySubscriptionsAfterHavingActiveSubscriptions() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v1, v2 with subscriptions
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result1.toActivate()).hasSize(2);

      // when - import empty subscriptions
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - all versions should be deactivated
      assertThat(result2.toActivate()).isEmpty();
      assertThat(result2.toDeactivate())
          .containsExactlyInAnyOrder(
              processIdAndKey("process1", "tenant1", 1L),
              processIdAndKey("process1", "tenant1", 2L));
    }

    @Test
    void shouldHandleLatestVersionAlsoInActiveSubscriptions() {
      // given
      var processId = processId("process1", "tenant1");

      // when - import v2 as latest
      var result1 =
          container.compareAndUpdate(
              importResult(processId, Set.of(2L), ImportType.LATEST_VERSIONS));
      assertThat(result1.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));

      // when - import v1, v2 with subscriptions (v2 is both latest and has subscription)
      var result2 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L, 2L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));
      assertThat(result2.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 1L));

      // when - remove v2 from subscriptions (but it's still latest)
      var result3 =
          container.compareAndUpdate(
              importResult(processId, Set.of(1L), ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then - v2 should NOT be deactivated (still latest)
      assertThat(result3.toActivate()).isEmpty();
      assertThat(result3.toDeactivate()).isEmpty();

      // when - v3 becomes latest (v2 loses latest flag but has no subscription)
      var result4 =
          container.compareAndUpdate(
              importResult(processId, Set.of(3L), ImportType.LATEST_VERSIONS));

      // then - v2 should be deactivated now
      assertThat(result4.toActivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 3L));
      assertThat(result4.toDeactivate())
          .containsExactlyInAnyOrder(processIdAndKey("process1", "tenant1", 2L));
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
      assertThat(result.toActivate()).hasSize(15);
      assertThat(result.toDeactivate()).isEmpty();

      // when - remove half of them
      var smallerSet = Set.of(8L, 9L, 10L, 11L, 12L, 13L, 14L, 15L);
      var result2 =
          container.compareAndUpdate(
              importResult(processId, smallerSet, ImportType.HAVE_ACTIVE_SUBSCRIPTIONS));

      // then
      assertThat(result2.toActivate()).isEmpty();
      assertThat(result2.toDeactivate()).hasSize(7);
    }
  }
}
