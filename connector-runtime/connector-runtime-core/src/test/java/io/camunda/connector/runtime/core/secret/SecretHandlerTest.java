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
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.camunda.connector.api.error.ConnectorInputException;
import io.camunda.connector.api.secret.SecretContext;
import io.camunda.connector.api.secret.SecretProvider;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SecretHandlerTest {

  @Test
  void references_areDedupedAndBatchedIntoOneCall() {
    SecretReferenceResolver resolver = mock(SecretReferenceResolver.class);
    when(resolver.resolve(anyCollection()))
        .thenReturn(Map.of("camunda.secrets.A", "va", "camunda.secrets.B", "vb"));
    var secretHandler = new SecretHandler(noopProvider(), SecretFilter.allowAll(), resolver);
    String input = "camunda.secrets.A camunda.secrets.B camunda.secrets.A";

    String result = secretHandler.replaceSecrets(input, null);

    assertThat(result).isEqualTo("va vb va");
    @SuppressWarnings("unchecked")
    ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
    verify(resolver, times(1)).resolve(captor.capture());
    assertThat(captor.getValue())
        .containsExactlyInAnyOrder("camunda.secrets.A", "camunda.secrets.B");
  }

  @Test
  void unresolvedReference_throwsConnectorInputException() {
    SecretReferenceResolver resolver = references -> Map.of();
    var secretHandler = new SecretHandler(noopProvider(), SecretFilter.allowAll(), resolver);

    assertThatThrownBy(() -> secretHandler.replaceSecrets("camunda.secrets.MISSING", null))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessage("Secret with name 'MISSING' is not available");
  }

  @Test
  void refusedReference_leavesTextAndDoesNotThrow() {
    String input = "camunda.secrets.ALLOWED camunda.secrets.REFUSED";
    // Built the way production does: the allow-list is the bare names actually found in the
    // input, minus the one we want refused - not a hand-picked list that happens to match.
    var allowList = new ArrayList<>(SecretUtil.retrieveSecretKeysInInput(input));
    allowList.remove("REFUSED");
    var filter = SecretFilter.allowOnly(allowList);
    SecretReferenceResolver resolver = references -> Map.of("camunda.secrets.ALLOWED", "value-a");
    var secretHandler = new SecretHandler(noopProvider(), filter, resolver);

    String result = secretHandler.replaceSecrets(input, null);

    assertThat(result).isEqualTo("value-a camunda.secrets.REFUSED");
  }

  @Test
  void filterIsKeyedByBareName() {
    var filter = SecretFilter.allowOnly(List.of("FOO"));
    SecretReferenceResolver resolver = mock(SecretReferenceResolver.class);
    when(resolver.resolve(anyCollection())).thenReturn(Map.of("camunda.secrets.FOO", "value"));
    var secretHandler = new SecretHandler(noopProvider(), filter, resolver);

    String result = secretHandler.replaceSecrets("camunda.secrets.FOO", null);

    assertThat(result).isEqualTo("value");
    // the resolver was asked for the WHOLE reference; only the filter check used the bare name
    verify(resolver).resolve(eq(List.of("camunda.secrets.FOO")));
  }

  @Test
  void nullSecretContext_isTolerated() {
    // The SecretContext still flows to the legacy SecretProvider and SecretUtil - only the
    // camunda.secrets.<name> resolver dropped it, since it's already scoped to one physical
    // tenant's client and never used it (see SecretReferenceResolver).
    SecretReferenceResolver resolver = mock(SecretReferenceResolver.class);
    when(resolver.resolve(anyCollection())).thenReturn(Map.of("camunda.secrets.FOO", "v"));
    var secretHandler = new SecretHandler(noopProvider(), SecretFilter.allowAll(), resolver);

    String result = secretHandler.replaceSecrets("camunda.secrets.FOO", null);

    assertThat(result).isEqualTo("v");
  }

  @Test
  void noSecretReferences_makesZeroResolverCalls() {
    // Covers the "keep them apart" guard for both the old-form-only payload (no new-form
    // references at all) and the outbound path (which always uses the 2-arg constructor below,
    // i.e. this same noop()-equivalent resolver) - neither ever reaches a cluster.
    SecretReferenceResolver resolver = mock(SecretReferenceResolver.class);
    var secretHandler =
        new SecretHandler(
            mapProvider(Map.of("OLD", "old-value")), SecretFilter.allowAll(), resolver);

    String result = secretHandler.replaceSecrets("{{secrets.OLD}}", null);

    assertThat(result).isEqualTo("old-value");
    verifyNoInteractions(resolver);
  }

  @Test
  void twoArgConstructor_defaultsToNoopResolver() {
    // The 2-arg constructor is what JobHandlerContext uses (via AbstractConnectorContext's 3-arg
    // constructor) - the outbound path stays on noop() and therefore never resolves the new form.
    var secretHandler = new SecretHandler(noopProvider(), SecretFilter.allowAll());

    assertThatThrownBy(() -> secretHandler.replaceSecrets("camunda.secrets.FOO", null))
        .isInstanceOf(ConnectorInputException.class);
  }

  private static SecretProvider noopProvider() {
    return mapProvider(Map.of());
  }

  private static SecretProvider mapProvider(Map<String, String> values) {
    return new SecretProvider() {
      @Override
      public String getSecret(String name, SecretContext context) {
        return values.get(name);
      }
    };
  }
}
