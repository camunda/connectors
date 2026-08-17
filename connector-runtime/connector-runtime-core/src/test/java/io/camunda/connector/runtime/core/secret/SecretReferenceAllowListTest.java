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
package io.camunda.connector.runtime.core.secret;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SecretReferenceAllowListTest {

  private static final String TENANT = "<default>";

  @Test
  void allowsAReferenceWrittenAsAWholePropertyValue() {
    var allowList =
        SecretReferenceAllowList.from(
            List.of("=camunda.secrets.TOKEN"), ClusterVariableSecretReader.noop(), TENANT);

    assertThat(allowList.allows("camunda.secrets.TOKEN")).isTrue();
    assertThat(allowList.writtenInProperties()).containsExactly("camunda.secrets.TOKEN");
  }

  @Test
  void doesNotAllowAReferenceEmbeddedInALongerValue() {
    // ADR-0007 §11: only a whole property value counts. Embedded text is not a supported form, so
    // it must not even reach the allow-list.
    var allowList =
        SecretReferenceAllowList.from(
            List.of(
                "https://api.example.com?key=camunda.secrets.TOKEN",
                "camunda.secrets.BARE",
                "=\"Bearer \" + camunda.secrets.MIXED"),
            ClusterVariableSecretReader.noop(),
            TENANT);

    assertThat(allowList.isEmpty()).isTrue();
    assertThat(allowList.allows("camunda.secrets.TOKEN")).isFalse();
    assertThat(allowList.allows("camunda.secrets.BARE")).isFalse();
    assertThat(allowList.allows("camunda.secrets.MIXED")).isFalse();
  }

  @Test
  void allowsAReferenceDeclaredByANamedClusterVariable() {
    ClusterVariableSecretReader reader =
        (references, tenantId) -> {
          assertThat(references)
              .containsExactly(
                  new ClusterVariableReference(ClusterVariableReference.Scope.ENV, "CFG"));
          assertThat(tenantId).isEqualTo(TENANT);
          return Set.of("camunda.secrets.FROM_VARIABLE");
        };

    var allowList = SecretReferenceAllowList.from(List.of("=camunda.vars.env.CFG"), reader, TENANT);

    assertThat(allowList.allows("camunda.secrets.FROM_VARIABLE")).isTrue();
    // It came from a variable, not from the properties, so the raw-property pass must not claim it.
    assertThat(allowList.writtenInProperties()).isEmpty();
  }

  @Test
  void doesNotConsultTheClusterWhenNoPropertyNamesAVariable() {
    var calls = new AtomicInteger();
    ClusterVariableSecretReader counting =
        (references, tenantId) -> {
          calls.incrementAndGet();
          return Set.of();
        };

    SecretReferenceAllowList.from(
        List.of("=camunda.secrets.TOKEN", "plain value"), counting, TENANT);

    assertThat(calls).hasValue(0);
  }

  @Test
  void combinesBothSources() {
    ClusterVariableSecretReader reader =
        (references, tenantId) -> Set.of("camunda.secrets.FROM_VARIABLE");

    var allowList =
        SecretReferenceAllowList.from(
            List.of("=camunda.secrets.WRITTEN", "=camunda.vars.cluster.CFG"), reader, TENANT);

    assertThat(allowList.all())
        .containsExactlyInAnyOrder("camunda.secrets.WRITTEN", "camunda.secrets.FROM_VARIABLE");
  }

  @Test
  void permitsNothingWhenThereAreNoProperties() {
    assertThat(
            SecretReferenceAllowList.from(List.of(), ClusterVariableSecretReader.noop(), TENANT)
                .isEmpty())
        .isTrue();
    assertThat(
            SecretReferenceAllowList.from(null, ClusterVariableSecretReader.noop(), TENANT)
                .isEmpty())
        .isTrue();
    assertThat(SecretReferenceAllowList.empty().allows("camunda.secrets.ANY")).isFalse();
  }

  @Test
  void aReaderThatFailsLeavesTheListNarrowerRatherThanWider() {
    // The reader reports nothing when it cannot read the cluster. The written-in-properties half
    // still stands, and the variable-declared secret simply does not resolve.
    var allowList =
        SecretReferenceAllowList.from(
            List.of("=camunda.secrets.WRITTEN", "=camunda.vars.env.CFG"),
            ClusterVariableSecretReader.noop(),
            TENANT);

    assertThat(allowList.allows("camunda.secrets.WRITTEN")).isTrue();
    assertThat(allowList.allows("camunda.secrets.FROM_VARIABLE")).isFalse();
  }
}
