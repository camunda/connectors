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
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SecretReferencePropertyResolverTest {

  private static final String TENANT = "<default>";

  private static SecretReferenceAllowList allowList(String... rawValues) {
    return SecretReferenceAllowList.from(
        List.of(rawValues), ClusterVariableSecretReader.noop(), TENANT);
  }

  private static SecretReferencePropertyResolver resolverFor(Map<String, String> secrets) {
    return new SecretReferencePropertyResolver(
        references -> {
          var found = new java.util.HashMap<String, String>();
          references.forEach(
              reference -> {
                if (secrets.containsKey(reference)) {
                  found.put(reference, secrets.get(reference));
                }
              });
          return found;
        },
        SecretFilter.allowAll());
  }

  @Test
  void replacesAValueThatIsEntirelyAReference() {
    var resolver = resolverFor(Map.of("camunda.secrets.TOKEN", "s3cret"));

    var result =
        resolver.resolve(
            Map.of("apiKey", "=camunda.secrets.TOKEN"), allowList("=camunda.secrets.TOKEN"));

    assertThat(result).isEqualTo(Map.of("apiKey", "s3cret"));
  }

  @Test
  void leavesAReferenceEmbeddedInALongerValueAlone() {
    var resolver = resolverFor(Map.of("camunda.secrets.TOKEN", "s3cret"));
    var properties =
        Map.<String, Object>of(
            "url", "https://api.example.com?key=camunda.secrets.TOKEN",
            "bare", "camunda.secrets.TOKEN",
            "mixed", "=\"Bearer \" + camunda.secrets.TOKEN");

    var result =
        resolver.resolve(properties, allowList(properties.values().toArray(String[]::new)));

    // Every one of these is on the allow-list, but none is a whole-value reference, so this pass
    // substitutes nothing - and in particular never rewrites part of a value.
    assertThat(result).isEqualTo(properties);
  }

  @Test
  void replacesNestedAndListValues() {
    var resolver = resolverFor(Map.of("camunda.secrets.A", "va", "camunda.secrets.B", "vb"));
    Map<String, Object> properties =
        Map.of(
            "auth", Map.of("token", "=camunda.secrets.A"),
            "headers", List.of("=camunda.secrets.B", "literal"));

    var result =
        resolver.resolve(properties, allowList("=camunda.secrets.A", "=camunda.secrets.B"));

    assertThat(result)
        .isEqualTo(
            Map.of(
                "auth", Map.of("token", "va"),
                "headers", List.of("vb", "literal")));
  }

  @Test
  void resolvesEveryReferenceInOneCall() {
    var calls = new AtomicInteger();
    var resolver =
        new SecretReferencePropertyResolver(
            references -> {
              calls.incrementAndGet();
              return Map.of("camunda.secrets.A", "va", "camunda.secrets.B", "vb");
            },
            SecretFilter.allowAll());

    resolver.resolve(
        Map.of("one", "=camunda.secrets.A", "two", "=camunda.secrets.B"),
        allowList("=camunda.secrets.A", "=camunda.secrets.B"));

    assertThat(calls).hasValue(1);
  }

  @Test
  void asksForNothingWhenNoPropertyHoldsAWholeValueReference() {
    var calls = new AtomicInteger();
    var resolver =
        new SecretReferencePropertyResolver(
            references -> {
              calls.incrementAndGet();
              return Map.of();
            },
            SecretFilter.allowAll());

    var properties = Map.<String, Object>of("plain", "value", "number", 42);
    var result = resolver.resolve(properties, allowList("plain", "value"));

    assertThat(result).isEqualTo(properties);
    assertThat(calls).hasValue(0);
  }

  @Test
  void leavesAReferenceTheAllowListDoesNotPermitExactlyAsWritten() {
    var resolver = resolverFor(Map.of("camunda.secrets.SNEAKY", "should-not-appear"));

    // The allow-list was built from different properties, so this reference was never declared.
    var result =
        resolver.resolve(
            Map.of("apiKey", "=camunda.secrets.SNEAKY"), allowList("=camunda.secrets.DECLARED"));

    assertThat(result).isEqualTo(Map.of("apiKey", "=camunda.secrets.SNEAKY"));
  }

  @Test
  void failsWhenAPermittedReferenceHasNoValue() {
    var resolver = resolverFor(Map.of());

    assertThatThrownBy(
            () ->
                resolver.resolve(
                    Map.of("apiKey", "=camunda.secrets.MISSING"),
                    allowList("=camunda.secrets.MISSING")))
        .isInstanceOf(ConnectorInputException.class)
        .hasMessage("Secret with name 'MISSING' is not available");
  }

  @Test
  void leavesAReferenceTheFilterRefusesWithoutFailing() {
    var resolver =
        new SecretReferencePropertyResolver(
            references -> Map.of(), name -> false /* refuse everything */);

    var result =
        resolver.resolve(
            Map.of("apiKey", "=camunda.secrets.REFUSED"), allowList("=camunda.secrets.REFUSED"));

    // Refused is not the same outcome as missing: the reference stays, nothing throws.
    assertThat(result).isEqualTo(Map.of("apiKey", "=camunda.secrets.REFUSED"));
  }

  @Test
  void aSecretValueIsNeverRescannedOrEscaped() {
    // The value goes in as a value, so text that looks like another reference stays opaque and
    // nothing has to be JSON-escaped by this pass.
    var resolver =
        resolverFor(Map.of("camunda.secrets.A", "{\"quote\":\"=camunda.secrets.GHOST\"}"));

    var result =
        resolver.resolve(Map.of("apiKey", "=camunda.secrets.A"), allowList("=camunda.secrets.A"));

    assertThat(result).isEqualTo(Map.of("apiKey", "{\"quote\":\"=camunda.secrets.GHOST\"}"));
  }

  @Test
  void toleratesEmptyAndNullProperties() {
    var resolver = resolverFor(Map.of());

    assertThat(resolver.resolve(Map.of(), allowList("=camunda.secrets.A"))).isEmpty();
    assertThat(resolver.resolve(null, allowList("=camunda.secrets.A"))).isNull();
  }
}
