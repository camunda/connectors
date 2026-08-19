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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretContext;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CentralStoreSecretProviderTest {

  private final RecordingResolver engineA = new RecordingResolver();
  private final RecordingResolver engineB = new RecordingResolver();

  @Test
  void looksUpALegacyNameAsACamundaSecretsReference() {
    engineA.holds("camunda.secrets.TOKEN", "tok-1");
    var provider = new CentralStoreSecretProvider(Map.of("engine-a", engineA));

    String value = provider.getSecret("TOKEN", context("engine-a"));

    assertThat(value).isEqualTo("tok-1");
    assertThat(engineA.requested).containsExactly(List.of("camunda.secrets.TOKEN"));
  }

  @Test
  void readsFromTheEngineTheSecretIsBeingResolvedFor() {
    engineA.holds("camunda.secrets.TOKEN", "from-a");
    engineB.holds("camunda.secrets.TOKEN", "from-b");
    var provider = new CentralStoreSecretProvider(Map.of("engine-a", engineA, "engine-b", engineB));

    assertThat(provider.getSecret("TOKEN", context("engine-b"))).isEqualTo("from-b");
    assertThat(engineA.requested).isEmpty();
  }

  @Test
  void usesTheOnlyConfiguredEngineWhenTheLookupCarriesNoPhysicalTenant() {
    engineA.holds("camunda.secrets.TOKEN", "tok-1");
    var provider = new CentralStoreSecretProvider(Map.of("engine-a", engineA));

    assertThat(provider.getSecret("TOKEN", context(null))).isEqualTo("tok-1");
    assertThat(provider.getSecret("TOKEN", null)).isEqualTo("tok-1");
  }

  @Test
  void readsNothingWhenSeveralEnginesAreConfiguredAndTheLookupSaysNothing() {
    engineA.holds("camunda.secrets.TOKEN", "from-a");
    var provider = new CentralStoreSecretProvider(Map.of("engine-a", engineA, "engine-b", engineB));

    assertThat(provider.getSecret("TOKEN", context(null))).isNull();
    assertThat(engineA.requested).isEmpty();
  }

  @Test
  void readsNothingWhenNoEngineMatchesThePhysicalTenant() {
    var provider = new CentralStoreSecretProvider(Map.of("engine-a", engineA));

    assertThat(provider.getSecret("TOKEN", context("engine-unknown"))).isNull();
    assertThat(engineA.requested).isEmpty();
  }

  @Test
  void reportsNothingForANameTheStoreDoesNotHold() {
    var provider = new CentralStoreSecretProvider(Map.of("engine-a", engineA));

    assertThat(provider.getSecret("MISSING", context("engine-a"))).isNull();
  }

  @ParameterizedTest
  @ValueSource(strings = {"myapp/db.pass", "with.dot", "with/slash", "with-dash", "with space"})
  void saysSoForANameThatCannotBeWrittenAsAReference(String name) {
    // The reference charset is narrower than the legacy secret-name charset. Reporting the name as
    // merely unavailable would look exactly like an ordinary missing secret.
    var provider = new CentralStoreSecretProvider(Map.of("engine-a", engineA));

    assertThatThrownBy(() -> provider.getSecret(name, context("engine-a")))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessageContaining(name)
        .hasMessageContaining("letters, digits and underscores");
    assertThat(engineA.requested).isEmpty();
  }

  private static SecretContext context(String physicalTenantId) {
    return new SecretContext("tenant", "process", physicalTenantId);
  }

  private static final class RecordingResolver extends SecretReferenceResolver {
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<List<String>> requested = new ArrayList<>();

    private RecordingResolver() {
      super(null);
    }

    private void holds(String reference, String value) {
      values.put(reference, value);
    }

    @Override
    public Map<String, String> resolve(Collection<String> references) {
      requested.add(List.copyOf(references));
      Map<String, String> resolved = new LinkedHashMap<>();
      references.stream().filter(values::containsKey).forEach(r -> resolved.put(r, values.get(r)));
      return resolved;
    }
  }
}
