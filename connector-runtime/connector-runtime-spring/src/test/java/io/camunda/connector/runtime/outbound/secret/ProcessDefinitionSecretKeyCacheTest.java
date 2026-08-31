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
package io.camunda.connector.runtime.outbound.secret;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.io.IOException;
import java.util.concurrent.Callable;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionSecretKeyCacheTest {

  private static final long PROCESS_DEF_KEY = 1L;

  @Mock private CamundaOperateClient camundaOperateClient;
  @Mock private Cache cache;

  private ProcessDefinitionSecretKeyCache secretKeyCache;

  @BeforeEach
  void setUp() throws Exception {
    secretKeyCache = new ProcessDefinitionSecretKeyCache(camundaOperateClient, cache);
    when(cache.get(anyLong(), any(Callable.class)))
        .thenAnswer(
            invocation -> {
              Callable<?> loader = invocation.getArgument(1);
              return loader.call();
            });
  }

  @Test
  void getSecretKeys_singleTaskWithSecrets_returnsExtractedKeys() throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys).containsExactlyInAnyOrder("API_KEY", "MY_TOKEN");
  }

  @Test
  void getSecretKeys_multipleTasksWithSecrets_returnsOnlyKeysForRequestedElement()
      throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-multiple-tasks-with-secrets.bpmn"));

    var alphaKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task-alpha"));
    var betaKeys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task-beta"));

    assertThat(alphaKeys).containsExactly("SECRET_ALPHA");
    assertThat(betaKeys).containsExactlyInAnyOrder("SECRET_BETA", "SECRET_GAMMA");
  }

  @Test
  void getSecretKeys_taskWithNoSecrets_returnsEmptyList() throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-no-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "no-secrets-task"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_taskWithoutTemplate_excluded_returnsEmptyList() throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-no-template.bpmn"));

    var keys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "plain-task"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_unknownElementId_returnsEmptyList() throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "nonexistent-task"));

    assertThat(keys).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("otherEligibleTaskTypes")
  void getSecretKeys_otherEligibleTaskTypes_returnsExtractedKeys(String elementId, String secretKey)
      throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-task-types.bpmn"));

    var keys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, elementId));

    assertThat(keys).containsExactly(secretKey);
  }

  private static Stream<Arguments> otherEligibleTaskTypes() {
    return Stream.of(
        Arguments.of("send-task", "SEND_SECRET"),
        Arguments.of("script-task", "SCRIPT_SECRET"),
        Arguments.of("business-rule-task", "BRT_SECRET"));
  }

  @Test
  void getSecretKeys_messageIntermediateThrowEventAndEndEvent_returnsEachElementsOwnSecrets()
      throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-message-events.bpmn"));

    var throwEventKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "send-message-throw"));
    var endEventKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "send-message-end"));

    assertThat(throwEventKeys).containsExactly("THROW_EVENT_SECRET");
    assertThat(endEventKeys).containsExactly("END_EVENT_SECRET");
  }

  @Test
  void getSecretKeys_nestedEmbeddedSubProcessesAndGrandchild_returnsEachElementsOwnSecrets()
      throws Exception {
    // Verifies subprocess coverage isn't ad-hoc-specific: a plain (embedded) SubProcess can
    // itself be a connector host, at any nesting depth.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-nested-embedded-subprocess.bpmn"));

    var outerKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "outer-subprocess"));
    var innerKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "inner-subprocess"));
    var grandchildKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "grandchild-task"));

    assertThat(outerKeys).containsExactly("OUTER_SECRET");
    assertThat(innerKeys).containsExactly("INNER_SECRET");
    assertThat(grandchildKeys).containsExactly("GRANDCHILD_SECRET");
  }

  @Test
  void getSecretKeys_noCamundaOperateClient_throwsIllegalStateException() {
    var cacheWithoutClient = new ProcessDefinitionSecretKeyCache(null, cache);

    assertThatThrownBy(
            () -> cacheWithoutClient.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task")))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("No CamundaOperateClient available");
  }

  private BpmnModelInstance loadBpmn(String fileName) throws IOException {
    try (var stream = getClass().getClassLoader().getResourceAsStream("bpmn/" + fileName)) {
      if (stream == null) {
        throw new IllegalArgumentException("BPMN resource not found: bpmn/" + fileName);
      }
      return Bpmn.readModelFromStream(stream);
    }
  }
}
