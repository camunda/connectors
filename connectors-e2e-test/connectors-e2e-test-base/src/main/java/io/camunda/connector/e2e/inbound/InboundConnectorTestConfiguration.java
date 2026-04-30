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
package io.camunda.connector.e2e.inbound;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.runtime.inbound.executable.InboundExecutableRegistry;
import io.camunda.connector.runtime.inbound.state.ProcessDefinitionInspector;
import java.time.Duration;
import java.util.Objects;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;

@TestConfiguration
public class InboundConnectorTestConfiguration {

  @Bean
  public InboundConnectorTestHelper inboundConnectorTestHelper(
      CacheManager cacheManager, InboundExecutableRegistry executableRegistry) {
    return new InboundConnectorTestHelper(cacheManager, executableRegistry);
  }

  public static class InboundConnectorTestHelper {

    private static final Duration DEFAULT_AWAIT_NO_EXECUTABLES_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_AWAIT_ACTIVE_EXECUTABLE_TIMEOUT = Duration.ofSeconds(15);

    private final CacheManager cacheManager;
    private final InboundExecutableRegistry executableRegistry;

    public InboundConnectorTestHelper(
        CacheManager cacheManager, InboundExecutableRegistry executableRegistry) {
      this.cacheManager = cacheManager;
      this.executableRegistry = executableRegistry;
    }

    public void setUpTest() {
      // PD keys can duplicate across tests (this does not happen in real world)
      // So we clear the inspector cache to avoid cross-test interference
      clearProcessDefinitionCache();

      // Wait for any executables from previous tests to be cleaned up
      // This prevents flakiness due to state carryover between tests
      awaitNoActiveInboundExecutables();
    }

    public void clearProcessDefinitionCache() {
      Objects.requireNonNull(
              cacheManager.getCache(ProcessDefinitionInspector.PROCESS_DEFINITION_CACHE_NAME))
          .clear();
    }

    /** Waits until there are no active executables in the registry. */
    public void awaitNoActiveInboundExecutables() {
      awaitNoActiveInboundExecutables(DEFAULT_AWAIT_NO_EXECUTABLES_TIMEOUT);
    }

    /** Waits until there are no active executables in the registry. */
    public void awaitNoActiveInboundExecutables(Duration waitDuration) {
      await("all executables should be cleaned up from previous tests")
          .atMost(waitDuration)
          .untilAsserted(
              () -> {
                var allExecutables = executableRegistry.query(f -> {});
                assertThat(allExecutables).isEmpty();
              });
    }

    /**
     * Waits until an inbound executable for the given BPMN element id is registered and reports
     * health {@link Health.Status#UP}. This is needed in addition to waiting for the BPMN element
     * to become active because the inbound executable registry processes activation events
     * asynchronously, so the subscription may not yet be reachable (e.g. a webhook would return
     * 404) when the element activates in the process.
     */
    public void awaitActiveInboundExecutable(String elementId) {
      awaitActiveInboundExecutable(elementId, DEFAULT_AWAIT_ACTIVE_EXECUTABLE_TIMEOUT);
    }

    /** See {@link #awaitActiveInboundExecutable(String)}. */
    public void awaitActiveInboundExecutable(String elementId, Duration waitDuration) {
      await("inbound executable for element '" + elementId + "' should be active and healthy")
          .atMost(waitDuration)
          .untilAsserted(
              () -> {
                var executables = executableRegistry.query(f -> f.elementId(elementId));
                assertThat(executables)
                    .as("executable for element '%s' should be registered", elementId)
                    .isNotEmpty();
                assertThat(executables)
                    .allSatisfy(
                        executable ->
                            assertThat(executable.health().getStatus())
                                .as(
                                    "executable for element '%s' should be UP, got: %s",
                                    elementId, executable.health())
                                .isEqualTo(Health.Status.UP));
              });
    }
  }
}
