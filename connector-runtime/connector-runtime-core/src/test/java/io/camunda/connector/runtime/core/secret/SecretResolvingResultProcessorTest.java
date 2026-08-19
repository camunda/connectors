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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.camunda.client.api.response.SecretReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SecretResolvingResultProcessorTest {

  private final RecordingResolver resolver = new RecordingResolver();
  private final SecretResolvingResultProcessor processor =
      new SecretResolvingResultProcessor(resolver);

  @Test
  void substitutesAReportedReferenceInAStringResult() {
    resolver.holds("camunda.secrets.TOKEN", "tok-1");

    Object result = processor.process("camunda.secrets.TOKEN", references("TOKEN"));

    assertThat(result).isEqualTo("tok-1");
  }

  @Test
  void substitutesAReferenceInsideACompositeExpressionResult() {
    resolver.holds("camunda.secrets.TOKEN", "tok-1");

    Object result = processor.process("Bearer camunda.secrets.TOKEN", references("TOKEN"));

    assertThat(result).isEqualTo("Bearer tok-1");
  }

  @Test
  void substitutesInsideMapValuesAndListElements() {
    resolver.holds("camunda.secrets.TOKEN", "tok-1");
    Object result =
        processor.process(
            Map.of(
                "headers", Map.of("Authorization", "Bearer camunda.secrets.TOKEN"),
                "list", List.of("camunda.secrets.TOKEN")),
            references("TOKEN"));

    assertThat(result)
        .isEqualTo(
            Map.of("headers", Map.of("Authorization", "Bearer tok-1"), "list", List.of("tok-1")));
  }

  @Test
  void leavesReferenceShapedTextTheClusterDidNotReport() {
    // The defence against injection: reference text arriving as process data — a webhook payload,
    // a correlated variable, a plain JSON cluster variable — is reported by nothing, so it stays
    // literal even though this evaluation legitimately used a different secret.
    resolver.holds("camunda.secrets.DECLARED", "declared-value");
    resolver.holds("camunda.secrets.NOT_DECLARED", "must-not-be-read");

    Object result =
        processor.process(
            Map.of(
                "declared", "camunda.secrets.DECLARED", "fromData", "camunda.secrets.NOT_DECLARED"),
            references("DECLARED"));

    assertThat(result)
        .isEqualTo(
            Map.of("declared", "declared-value", "fromData", "camunda.secrets.NOT_DECLARED"));
    assertThat(resolver.requested).containsExactly(List.of("camunda.secrets.DECLARED"));
  }

  @Test
  void resolvesNothingWhenTheClusterReportsNoReference() {
    resolver.holds("camunda.secrets.TOKEN", "tok-1");

    Object result = processor.process("camunda.secrets.TOKEN", List.of());

    assertThat(result).isEqualTo("camunda.secrets.TOKEN");
    assertThat(resolver.requested).isEmpty();
  }

  @Test
  void resolvesNothingWhenTheReportIsAbsentAltogether() {
    // The Camunda client normalises an absent report to an empty list, so production never passes
    // null. Tolerating it anyway keeps a hand-built or older client from causing an NPE here, in
    // the one place where failing open would be a leak.
    resolver.holds("camunda.secrets.TOKEN", "tok-1");

    Object result = processor.process("camunda.secrets.TOKEN", null);

    assertThat(result).isEqualTo("camunda.secrets.TOKEN");
    assertThat(resolver.requested).isEmpty();
  }

  @Test
  void resolvesNothingAgainstAClusterThatReportsNoReferences() {
    // An orchestration cluster too old to report referenced secrets answers with an empty list,
    // which is indistinguishable from an expression that used no secret. Either way nothing is
    // resolved: the placeholder survives and the connector fails visibly.
    resolver.holds("camunda.secrets.TOKEN", "tok-1");

    Object result = processor.process("Bearer camunda.secrets.TOKEN", List.of());

    assertThat(result).isEqualTo("Bearer camunda.secrets.TOKEN");
    assertThat(resolver.requested).isEmpty();
  }

  @Test
  void makesNoResolveCallWhenTheResultHoldsNoReference() {
    Object result = processor.process("plain value", references("TOKEN"));

    assertThat(result).isEqualTo("plain value");
    assertThat(resolver.requested).isEmpty();
  }

  @Test
  void collapsesTwoReportsOfTheSameNameFromDifferentStores() {
    resolver.holds("camunda.secrets.TOKEN", "tok-1");

    Object result =
        processor.process(
            "camunda.secrets.TOKEN",
            List.of(reference("store-a", "TOKEN"), reference("store-b", "TOKEN")));

    assertThat(result).isEqualTo("tok-1");
    assertThat(resolver.requested).containsExactly(List.of("camunda.secrets.TOKEN"));
  }

  @Test
  void leavesThePlaceholderWhenResolutionFails() {
    Object result = processor.process("camunda.secrets.TOKEN", references("TOKEN"));

    assertThat(result).isEqualTo("camunda.secrets.TOKEN");
  }

  @Test
  void leavesAValueThatOnlyPartlyResolves() {
    resolver.holds("camunda.secrets.A", "a-value");

    Object result = processor.process("camunda.secrets.A camunda.secrets.B", references("A", "B"));

    assertThat(result).isEqualTo("a-value camunda.secrets.B");
  }

  @Test
  void doesNotTreatAResolvedValueAsAnExpression() {
    resolver.holds("camunda.secrets.TOKEN", "=1+1");

    Object result = processor.process("camunda.secrets.TOKEN", references("TOKEN"));

    assertThat(result).isEqualTo("=1+1");
  }

  @Test
  void doesNotResolveAReferenceCarriedInsideAResolvedValue() {
    resolver.holds("camunda.secrets.OUTER", "camunda.secrets.INNER");
    resolver.holds("camunda.secrets.INNER", "inner-value");

    Object result = processor.process("camunda.secrets.OUTER", references("OUTER", "INNER"));

    assertThat(result).isEqualTo("camunda.secrets.INNER");
  }

  @Test
  void handlesANullAnywhereInTheResult() {
    // An expression may evaluate to a structure holding nulls. Walking it must not fail the bind.
    resolver.holds("camunda.secrets.TOKEN", "tok-1");
    var withNulls = new LinkedHashMap<String, Object>();
    withNulls.put("empty", null);
    withNulls.put("secret", "camunda.secrets.TOKEN");
    withNulls.put("list", Arrays.asList(null, "camunda.secrets.TOKEN"));

    Object result = processor.process(withNulls, references("TOKEN"));

    var expected = new LinkedHashMap<String, Object>();
    expected.put("empty", null);
    expected.put("secret", "tok-1");
    expected.put("list", Arrays.asList(null, "tok-1"));
    assertThat(result).isEqualTo(expected);
  }

  @Test
  void handlesANullInAResultWithNothingToResolve() {
    var withNulls = new LinkedHashMap<String, Object>();
    withNulls.put("empty", null);

    assertThat(processor.process(withNulls, references("TOKEN"))).isEqualTo(withNulls);
  }

  @Test
  void passesThroughAResultWithNoStrings() {
    assertThat(processor.process(42, references("TOKEN"))).isEqualTo(42);
    assertThat(processor.process(null, references("TOKEN"))).isNull();
    assertThat(processor.process(List.of(1, true), references("TOKEN")))
        .isEqualTo(List.of(1, true));
  }

  @Test
  void ignoresAReportWithNoName() {
    resolver.holds("camunda.secrets.TOKEN", "tok-1");
    var withNull = new ArrayList<SecretReference>();
    withNull.add(null);
    withNull.add(reference(null, null));
    withNull.add(reference("store", "TOKEN"));

    Object result = processor.process("camunda.secrets.TOKEN", withNull);

    assertThat(result).isEqualTo("tok-1");
  }

  @Test
  void preservesNonStringLeavesAndKeysWhileSubstituting() {
    resolver.holds("camunda.secrets.TOKEN", "tok-1");
    Map<String, Object> input = new LinkedHashMap<>();
    input.put("secret", "camunda.secrets.TOKEN");
    input.put("count", 3);
    input.put("nested", List.of(Map.of("inner", "camunda.secrets.TOKEN")));

    Object result = processor.process(input, references("TOKEN"));

    Map<String, Object> expected = new LinkedHashMap<>();
    expected.put("secret", "tok-1");
    expected.put("count", 3);
    expected.put("nested", List.of(Map.of("inner", "tok-1")));
    assertThat(result).isEqualTo(expected);
  }

  private static List<SecretReference> references(String... names) {
    return Arrays.stream(names).map(name -> reference("default", name)).toList();
  }

  private static SecretReference reference(String storeId, String secretName) {
    var reference = mock(SecretReference.class);
    when(reference.getStoreId()).thenReturn(storeId);
    when(reference.getSecretName()).thenReturn(secretName);
    return reference;
  }

  /** Resolves only what it was told to hold, and records every request it was given. */
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
