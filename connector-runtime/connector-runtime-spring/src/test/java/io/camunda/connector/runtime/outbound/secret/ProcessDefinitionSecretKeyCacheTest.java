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
import static org.mockito.Mockito.when;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.camunda.connector.runtime.core.secret.SecretFilter.Secret;
import io.camunda.connector.runtime.outbound.secret.SecretKeyCache.SecretKeyContext;
import io.camunda.operate.CamundaOperateClient;
import io.camunda.zeebe.model.bpmn.Bpmn;
import io.camunda.zeebe.model.bpmn.BpmnModelInstance;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProcessDefinitionSecretKeyCacheTest {

  private static final long PROCESS_DEF_KEY = 1L;

  @Mock private CamundaOperateClient camundaOperateClient;

  private ProcessDefinitionSecretKeyCache secretKeyCache;

  @BeforeEach
  void setUp() {
    // A real Caffeine cache, not a mock: exercises the actual get(key, Function) contract this
    // class now relies on, including unchecked-exception propagation.
    secretKeyCache =
        new ProcessDefinitionSecretKeyCache(camundaOperateClient, Caffeine.newBuilder().build());
  }

  @Test
  void getSecretKeys_singleTaskWithSecrets_returnsExtractedKeys() throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-secrets.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .extracting(Secret::secretName)
        .containsExactlyInAnyOrder("API_KEY", "MY_TOKEN");
  }

  @Test
  void getSecretKeys_dottedTarget_splitsIntoAMultiSegmentFieldPath() throws Exception {
    // Projecting away fieldPath (as every other assertion in this class does, via
    // Secret::secretName) would let a regression that assigns every secret an empty or wrong
    // path pass unnoticed — this asserts the complete Secret, name and path together, against a
    // target with more than one segment.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-dotted-target.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys).containsExactly(new Secret("API_KEY", List.of("auth", "token")));
  }

  @Test
  void getSecretKeys_secretPropagatesToAnInputThatReferencesTheDeclaringInputsVariable()
      throws Exception {
    // baseUrl declares BASE_URL_SFDC; url is a FEEL expression built from baseUrl and path
    // ("=baseUrl + \"/services/apexrest/\" + path"). url's runtime value can carry the secret's
    // resolved text just as directly as baseUrl's can, so the secret must be allowed under url's
    // own field path too -- not just under baseUrl's.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-referenced-variable.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .contains(
            new Secret("BASE_URL_SFDC", List.of("baseUrl")),
            new Secret("BASE_URL_SFDC", List.of("url")));
  }

  @Test
  void getSecretKeys_propagatesTransitivelyThroughAChainOfReferences() throws Exception {
    // innerBaseUrl declares INNER_SECRET; baseUrl = "=innerBaseUrl + \"/static-context\""
    // references innerBaseUrl; url = "=baseUrl" references baseUrl, not innerBaseUrl directly.
    // The secret has to reach url through two hops of the dependency chain, not just one.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-chained-references.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .containsExactlyInAnyOrder(
            new Secret("INNER_SECRET", List.of("innerBaseUrl")),
            new Secret("INNER_SECRET", List.of("baseUrl")),
            new Secret("INNER_SECRET", List.of("url")));
  }

  @Test
  void getSecretKeys_propagationDoesNotReachAnInputThatDoesNotReferenceTheDeclaringVariable()
      throws Exception {
    // method ("get") and connectionTimeoutInSeconds ("20") are unrelated static inputs on the same
    // element -- propagation must not leak the secret onto every sibling input, only onto ones
    // whose own expression actually reads the variable that carries it.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-referenced-variable.bpmn"));

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
  void getSecretKeys_referenceToASiblingFieldDoesNotPropagateASiblingsSecret() throws Exception {
    // authentication.token and authentication.type are siblings that merely share a top-level
    // target segment. usesSiblingFieldOnly references authentication.type specifically, not the
    // whole authentication object, so it must not inherit AUTH_TOKEN from its sibling field.
    // usesWholeAuth, by reading the whole authentication object, legitimately picks it up.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-sibling-fields.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .contains(
            new Secret("AUTH_TOKEN", List.of("authentication", "token")),
            new Secret("AUTH_TOKEN", List.of("usesWholeAuth")))
        .doesNotContain(
            new Secret("AUTH_TOKEN", List.of("usesSiblingFieldOnly")),
            new Secret("AUTH_TOKEN", List.of("authentication", "type")));
  }

  @Test
  void getSecretKeys_referenceToAVariableDefinedLaterDoesNotPropagateItsSecret() throws Exception {
    // url ("=baseUrl") is declared before baseUrl ("secrets.API_KEY") in the same ioMapping.
    // zeebe:input mappings evaluate in declaration order, each against the output the ones before
    // it already built, so url's expression sees the process-scope baseUrl, not this mapping's
    // own -- API_KEY must not be allowed on url.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-reverse-order-reference.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .contains(new Secret("API_KEY", List.of("baseUrl")))
        .doesNotContain(new Secret("API_KEY", List.of("url")));
  }

  @Test
  void getSecretKeys_laterParentOverwriteInvalidatesAnEarlierDescendantWriter() throws Exception {
    // authentication.token = secrets.TOKEN is declared first, then authentication (the whole
    // parent object) is overwritten wholesale, replacing that field's secret-bearing value. url
    // references authentication.token afterwards, but by then the token write is shadowed by
    // the parent overwrite -- TOKEN must not be allowed on url, nor on its own now-overwritten
    // path: the runtime field at authentication.token holds whatever the parent overwrite put
    // there, not the secret, so granting TOKEN there would let that overwritten (potentially
    // attacker-controlled) value resolve a secret it never carried.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-parent-overwrite.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    // Nothing in this BPMN ever reads authentication.token from a path that's still effective, so
    // TOKEN isn't just absent from the two paths above -- it's not granted anywhere at all. This
    // is stricter than doesNotContain(...) on its own: it also catches a regression that grants
    // TOKEN at some third path this test doesn't otherwise name.
    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_laterWriteToTheSameExactPathInvalidatesTheEarlierWriter() throws Exception {
    // Two inputs target the identical path authentication.token: the first carries TOKEN, the
    // second overwrites it with a plain value. The runtime field holds only the second input's
    // value, so TOKEN must not be granted at that path -- the earlier grant would otherwise let
    // the overwriting (potentially attacker-controlled) value resolve a secret it never carried.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-same-path-overwrite.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_laterOverwriteOfAPropagationTargetInvalidatesThePropagatedGrant()
      throws Exception {
    // baseUrl = secrets.TOKEN is declared first; url = "=baseUrl" propagates TOKEN to url while
    // baseUrl is still the effective writer url's expression reads. A third mapping then
    // overwrites url directly with attacker-controlled data -- url's runtime value comes from that
    // third mapping, not from the (no longer effective) propagation through the second, so TOKEN
    // must not be granted at url even though the dependency edge to it was genuinely resolved at
    // the time it was computed.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-overwritten-propagation-target.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys)
        .containsExactly(new Secret("TOKEN", List.of("baseUrl")))
        .doesNotContain(new Secret("TOKEN", List.of("url")));
  }

  @Test
  void getSecretKeys_laterChildWriteRevokesTheAncestorGrant() throws Exception {
    // authentication = secrets.WHOLE_SECRET is declared first; authentication.token is then
    // written by a later mapping. A grant is a path prefix, so a grant at [authentication] also
    // authorizes a lookup at authentication.token -- the field the later mapping controls. If that
    // mapping's source were process data rather than the fixture's literal, a placeholder in it
    // would resolve WHOLE_SECRET, a secret that value never carried. The whole grant therefore
    // goes, at the parent's own path and at wholeObjectRead, which copies the object afterwards.
    // Nothing is lost here: the child write replaces the parent's scalar with an object, so the
    // secret's text is not present at runtime under authentication at all.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-child-overwrite.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_laterChildWriteRevokesAnAncestorGrantThatNestsItsSecretElsewhere()
      throws Exception {
    // The fail-closed half of the rule above, pinned deliberately. Here the parent's own FEEL
    // object nests USER at authentication.user, which the later authentication.token write does
    // not touch -- so the grant is legitimate for that one field and only over-broad for the
    // shadowed sibling. A prefix cannot express "under authentication except .token", so the
    // grant is dropped rather than narrowed, and USER stops resolving on this element. Accepted
    // over-denial: the alternative is authorizing the secret at a field a later mapping fills
    // from process data.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-nested-parent-and-child-overwrite.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys).isEmpty();
  }

  @Test
  void getSecretKeys_shadowedInputDoesNotRelayItsOwnDependenciesSecret() throws Exception {
    // The shadowed input is itself a dependent here: baseUrl carries T, authentication reads
    // baseUrl, authentication.token is then written from process data, and copy reads the whole
    // authentication object. Muting only the shadowed input's *own* names is not enough -- the
    // walk would keep traversing through it and reach baseUrl, granting T at copy, which
    // prefix-matches copy.token, the field that carries the shadowed subtree's attacker data.
    // A shadowed input terminates the branch instead.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-with-shadowed-input-as-dependency.bpmn"));

    var keys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "service-task-1"));

    assertThat(keys).containsExactly(new Secret("T", List.of("baseUrl")));
  }

  @Test
  void getSecretKeys_multipleTasksWithSecrets_returnsOnlyKeysForRequestedElement()
      throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-multiple-tasks-with-secrets.bpmn"));

    var alphaKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task-alpha"));
    var betaKeys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task-beta"));

    assertThat(alphaKeys).extracting(Secret::secretName).containsExactly("SECRET_ALPHA");
    assertThat(betaKeys)
        .extracting(Secret::secretName)
        .containsExactlyInAnyOrder("SECRET_BETA", "SECRET_GAMMA");
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
  void getSecretKeys_taskWithoutTemplate_returnsItsOwnSecrets() throws Exception {
    // zeebe:modelerTemplate records how an element was authored, not what it is. A task that
    // declares a secret in its own input mapping must be allow-listed for it whether or not it was
    // built by applying an element template in Modeler.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-no-template.bpmn"));

    var keys = secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "plain-task"));

    assertThat(keys).extracting(Secret::secretName).containsExactly("SECRET_X");
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

    assertThat(keys).extracting(Secret::secretName).containsExactly(secretKey);
  }

  private static Stream<Arguments> otherEligibleTaskTypes() {
    return Stream.of(
        Arguments.of("send-task", "SEND_SECRET"),
        Arguments.of("script-task", "SCRIPT_SECRET"),
        Arguments.of("business-rule-task", "BRT_SECRET"));
  }

  @Test
  void getSecretKeys_nestedEmbeddedSubProcessesAndGrandchild_returnsEachElementsOwnSecrets()
      throws Exception {
    // A subprocess (embedded, event, multi-instance, or nested) can itself be a connector host,
    // at any nesting depth -- not just its children.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-nested-embedded-subprocess.bpmn"));

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
      throws Exception {
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenReturn(loadBpmn("outbound-message-events.bpmn"));

    var throwEventKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "send-message-throw"));
    var endEventKeys =
        secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "send-message-end"));

    assertThat(throwEventKeys).extracting(Secret::secretName).containsExactly("THROW_EVENT_SECRET");
    assertThat(endEventKeys).extracting(Secret::secretName).containsExactly("END_EVENT_SECRET");
  }

  @Test
  void getSecretKeys_noCamundaOperateClient_throwsSecretFilterUnavailableException() {
    var cacheWithoutClient =
        new ProcessDefinitionSecretKeyCache(null, Caffeine.newBuilder().build());

    assertThatThrownBy(
            () -> cacheWithoutClient.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task")))
        .isInstanceOf(SecretFilterUnavailableException.class)
        .hasMessageContaining("No CamundaOperateClient available");
  }

  @Test
  void getSecretKeys_operateLookupFails_propagatesTheOperateExceptionWrappedExactlyOnce()
      throws Exception {
    // Caffeine's Cache#get(key, Function) rethrows the mapping function's exception unwrapped --
    // SecretKeyLookupException is the only wrapper this path ever introduces, needed solely to
    // cross the Function boundary with the checked OperateException.
    when(camundaOperateClient.getProcessDefinitionModel(PROCESS_DEF_KEY))
        .thenThrow(new io.camunda.operate.exception.OperateException("404"));

    assertThatThrownBy(
            () -> secretKeyCache.getSecretKeys(new SecretKeyContext(PROCESS_DEF_KEY, "task")))
        .isInstanceOf(SecretKeyLookupException.class)
        .hasCauseInstanceOf(io.camunda.operate.exception.OperateException.class);
  }

  private BpmnModelInstance loadBpmn(String fileName) throws Exception {
    try (var stream = getClass().getClassLoader().getResourceAsStream("bpmn/" + fileName)) {
      if (stream == null) {
        throw new IllegalArgumentException("BPMN resource not found: bpmn/" + fileName);
      }
      return Bpmn.readModelFromStream(stream);
    }
  }
}
