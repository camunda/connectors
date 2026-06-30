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
package io.camunda.connector.runtime.core.inbound;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

/**
 * Verifies prefix derivation against a fixture that mirrors the structure of a real connector model
 * (a wrapper around a bean with scalars, an enum, a container, a sealed polymorphic type and a FEEL
 * function), without depending on any connector module.
 */
class DeduplicationPropertyResolverTest {

  @Test
  void resolvesConnectorScopedPrefixesAndStopsAtPolymorphicTypes() {
    var prefixes = DeduplicationPropertyResolver.resolvePrefixes(List.of(ConnectorWrapper.class));

    assertThat(prefixes)
        .containsExactlyInAnyOrder(
            "inbound.method",
            "inbound.context",
            "inbound.mode",
            "inbound.scopes",
            "inbound.auth",
            "inbound.verification");
    // the sealed/polymorphic type is a stopping point: its subtype fields are NOT enumerated
    assertThat(prefixes).noneMatch(p -> p.startsWith("inbound.auth."));
  }

  @Test
  void scopeMatchingCoversNestedPolymorphicKeysButNotElementScopedOnes() {
    var prefixes = DeduplicationPropertyResolver.resolvePrefixes(List.of(ConnectorWrapper.class));

    // the single inbound.auth prefix transparently covers every discriminator/subtype key
    assertThat(DeduplicationPropertyResolver.matchesScope("inbound.auth.type", prefixes)).isTrue();
    assertThat(DeduplicationPropertyResolver.matchesScope("inbound.auth.jwt.url", prefixes))
        .isTrue();
    assertThat(DeduplicationPropertyResolver.matchesScope("inbound.method", prefixes)).isTrue();
    // element-scoped properties are not in scope
    assertThat(DeduplicationPropertyResolver.matchesScope("inbound.responseExpression", prefixes))
        .isFalse();
    // prefix matching must not treat a string prefix as a key prefix (requires a '.' boundary)
    assertThat(DeduplicationPropertyResolver.matchesScope("inbound.contextual", prefixes))
        .isFalse();
  }

  @Test
  void resolvesElementScopedClassIndependently() {
    var prefixes = DeduplicationPropertyResolver.resolvePrefixes(List.of(DynamicWrapper.class));

    assertThat(prefixes)
        .containsExactlyInAnyOrder("inbound.responseExpression", "inbound.responseBodyExpression");
    assertThat(DeduplicationPropertyResolver.matchesScope("inbound.responseExpression", prefixes))
        .isTrue();
    assertThat(DeduplicationPropertyResolver.matchesScope("inbound.method", prefixes)).isFalse();
  }

  @Test
  void ignoresVoidAndEmptyInput() {
    assertThat(DeduplicationPropertyResolver.resolvePrefixes(List.of())).isEmpty();
    assertThat(DeduplicationPropertyResolver.resolvePrefixes(List.of(Void.class))).isEmpty();
  }

  // --- fixtures -------------------------------------------------------------------------------

  record ConnectorWrapper(ConnectorProperties inbound) {}

  record ConnectorProperties(
      String method,
      String context,
      Mode mode,
      String[] scopes,
      Auth auth,
      Function<Object, Object> verification) {}

  enum Mode {
    ONE,
    TWO
  }

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes({
    @JsonSubTypes.Type(value = BasicAuth.class, name = "BASIC"),
    @JsonSubTypes.Type(value = JwtAuth.class, name = "JWT")
  })
  sealed interface Auth permits BasicAuth, JwtAuth {}

  record BasicAuth(String username, String password) implements Auth {}

  record JwtAuth(Jwt jwt) implements Auth {}

  record Jwt(String url) {}

  record DynamicWrapper(DynamicProperties inbound) {}

  record DynamicProperties(String responseExpression, String responseBodyExpression) {}
}
