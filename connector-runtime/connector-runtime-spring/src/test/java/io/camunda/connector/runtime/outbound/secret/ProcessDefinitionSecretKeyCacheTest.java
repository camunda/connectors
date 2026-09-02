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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.camunda.client.CamundaClient;
import io.camunda.client.api.fetch.ProcessDefinitionGetXmlRequest;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.concurrent.ConcurrentMapCache;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionSecretKeyCacheTest {

  private static final long PROCESS_DEF_KEY = 1L;

  @Mock private CamundaClient camundaClient;
  @Mock private ProcessDefinitionGetXmlRequest xmlRequest;
  @Mock private Cache cache;

  private ProcessDefinitionSecretKeyCache secretKeyCache;

  @BeforeEach
  void setUp() throws Exception {
    secretKeyCache = new ProcessDefinitionSecretKeyCache("tenant", camundaClient, cache);
    // lenient: the cross-tenant collision test below builds its own separate clients/caches and
    // doesn't exercise these shared fields
    lenient()
        .when(camundaClient.newProcessDefinitionGetXmlRequest(anyLong()))
        .thenReturn(xmlRequest);
    lenient()
        .when(cache.get(any(), any(Callable.class)))
        .thenAnswer(
            invocation -> {
              Callable<?> loader = invocation.getArgument(1);
              return loader.call();
            });
  }

  @Test
  void getSecretKeys_singleTaskWithSecrets_returnsExtractedKeys() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-with-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .extracting(Secret::secretName)
        .containsExactlyInAnyOrder("API_KEY", "MY_TOKEN");
  }

  @Test
  void getSecretKeys_dottedTarget_splitsIntoAMultiSegmentFieldPath() throws IOException {
    // Projecting away fieldPath (as every other assertion in this class does, via
    // Secret::secretName) would let a regression that assigns every secret an empty or wrong
    // path pass unnoticed — this asserts the complete Secret, name and path together, against a
    // target with more than one segment.
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-with-dotted-target.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys).containsExactly(new Secret("API_KEY", List.of("auth", "token")));
  }

  @Test
  void getSecretKeys_secretPropagatesToAnInputThatReferencesTheDeclaringInputsVariable()
      throws IOException {
    // baseUrl declares BASE_URL_SFDC; url is a FEEL expression built from baseUrl and path
    // ("=baseUrl + \"/services/apexrest/\" + path"). url's runtime value can carry the secret's
    // resolved text just as directly as baseUrl's can, so the secret must be allowed under url's
    // own field path too -- not just under baseUrl's.
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-with-referenced-variable.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .contains(
            new Secret("BASE_URL_SFDC", List.of("baseUrl")),
            new Secret("BASE_URL_SFDC", List.of("url")));
  }

  @Test
  void getSecretKeys_propagationDoesNotReachAnInputThatDoesNotReferenceTheDeclaringVariable()
      throws IOException {
    // method ("get") and connectionTimeoutInSeconds ("20") are unrelated static inputs on the same
    // element -- propagation must not leak the secret onto every sibling input, only onto ones
    // whose own expression actually reads the variable that carries it.
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-with-referenced-variable.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .doesNotContain(
            new Secret("BASE_URL_SFDC", List.of("method")),
            new Secret("BASE_URL_SFDC", List.of("connectionTimeoutInSeconds")),
            new Secret("BASE_URL_SFDC", List.of("path")),
            new Secret("BASE_URL_SFDC", List.of("authentication", "type")));
  }

  @Test
  void getSecretKeys_multipleTasksWithSecrets_returnsOnlyKeysForRequestedElement()
      throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-multiple-tasks-with-secrets.bpmn"));

    var alphaKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task-alpha"));
    var betaKeys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task-beta"));

    assertThat(alphaKeys).extracting(Secret::secretName).containsExactly("SECRET_ALPHA");
    assertThat(betaKeys)
        .extracting(Secret::secretName)
        .containsExactlyInAnyOrder("SECRET_BETA", "SECRET_GAMMA");
  }

  @Test
  void getSecretKeys_taskWithNoSecrets_returnsEmptyList() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-no-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "no-secrets-task"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_taskWithoutTemplate_returnsItsOwnSecrets() throws IOException {
    // zeebe:modelerTemplate records how an element was authored, not what it is. A task that
    // declares a secret in its own input mapping must be allow-listed for it whether or not it was
    // built by applying an element template in Modeler.
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-no-template.bpmn"));

    var keys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "plain-task"));

    assertThat(keys).extracting(Secret::secretName).containsExactly("SECRET_X");
  }

  @Test
  void getSecretKeys_adHocSubProcessAndChild_returnsEachElementsOwnSecrets() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-adhoc-subprocess.bpmn"));

    var subProcessKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "agent-subprocess"));
    var childKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "child-task"));

    assertThat(subProcessKeys).extracting(Secret::secretName).containsExactly("ADHOC_SECRET");
    assertThat(childKeys).extracting(Secret::secretName).containsExactly("CHILD_SECRET");
  }

  @Test
  void getSecretKeys_nestedEmbeddedSubProcessesAndGrandchild_returnsEachElementsOwnSecrets()
      throws IOException {
    // Verifies the fix generalizes beyond AdHocSubProcess: a plain (embedded) SubProcess can
    // itself be a connector host, at any nesting depth, same as the ad-hoc flavor.
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-nested-embedded-subprocess.bpmn"));

    var outerKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "outer-subprocess"));
    var innerKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "inner-subprocess"));
    var grandchildKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "grandchild-task"));

    assertThat(outerKeys).extracting(Secret::secretName).containsExactly("OUTER_SECRET");
    assertThat(innerKeys).extracting(Secret::secretName).containsExactly("INNER_SECRET");
    assertThat(grandchildKeys).extracting(Secret::secretName).containsExactly("GRANDCHILD_SECRET");
  }

  @Test
  void getSecretKeys_messageIntermediateThrowEventAndEndEvent_returnsEachElementsOwnSecrets()
      throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-message-events.bpmn"));

    var throwEventKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "send-message-throw"));
    var endEventKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "send-message-end"));

    assertThat(throwEventKeys).extracting(Secret::secretName).containsExactly("THROW_EVENT_SECRET");
    assertThat(endEventKeys).extracting(Secret::secretName).containsExactly("END_EVENT_SECRET");
  }

  @Test
  void getSecretKeys_unknownElementId_returnsEmptyList() throws IOException {
    when(xmlRequest.execute()).thenReturn(loadBpmn("outbound-with-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "nonexistent-task"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_collidingProcessDefinitionKeyAcrossPhysicalTenants_doesNotLeakBetweenTenants()
      throws IOException {
    // a real (unmocked) shared cache, proving the compound key actually disambiguates rather than
    // relying on a mock that never really stores anything
    Cache sharedCache = new ConcurrentMapCache("secret-keys");
    var clientA = mock(CamundaClient.class);
    var clientB = mock(CamundaClient.class);
    var xmlRequestA = mock(ProcessDefinitionGetXmlRequest.class);
    var xmlRequestB = mock(ProcessDefinitionGetXmlRequest.class);
    when(clientA.newProcessDefinitionGetXmlRequest(PROCESS_DEF_KEY)).thenReturn(xmlRequestA);
    when(clientB.newProcessDefinitionGetXmlRequest(PROCESS_DEF_KEY)).thenReturn(xmlRequestB);
    when(xmlRequestA.execute()).thenReturn(loadBpmn("outbound-with-secrets.bpmn"));
    when(xmlRequestB.execute()).thenReturn(loadBpmn("outbound-no-secrets.bpmn"));

    var cacheForTenantA = new ProcessDefinitionSecretKeyCache("tenant-a", clientA, sharedCache);
    var cacheForTenantB = new ProcessDefinitionSecretKeyCache("tenant-b", clientB, sharedCache);

    var keysA =
        cacheForTenantA.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));
    var keysB =
        cacheForTenantB.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "no-secrets-task"));

    assertThat(keysA)
        .extracting(Secret::secretName)
        .containsExactlyInAnyOrder("API_KEY", "MY_TOKEN");
    assertThat(keysB).isEmpty();
    // each tenant's own client was queried exactly once, proving neither served the other's cache
    // entry despite the identical processDefinitionKey
    verify(clientA).newProcessDefinitionGetXmlRequest(PROCESS_DEF_KEY);
    verify(clientB).newProcessDefinitionGetXmlRequest(PROCESS_DEF_KEY);
  }

  private String loadBpmn(String fileName) throws IOException {
    try (var stream = getClass().getClassLoader().getResourceAsStream("bpmn/" + fileName)) {
      if (stream == null) {
        throw new IllegalArgumentException("BPMN resource not found: bpmn/" + fileName);
      }
      return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
    }
  }
}
