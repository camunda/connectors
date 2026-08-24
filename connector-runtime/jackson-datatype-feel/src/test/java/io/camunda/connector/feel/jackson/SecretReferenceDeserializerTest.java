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
package io.camunda.connector.feel.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Only a fraction of connector credential fields carry {@code @FEEL}; a secret reference written in
 * one of the others has to resolve too. These tests cover the shapes those fields actually take,
 * and pin that nothing else about String binding changes.
 */
class SecretReferenceDeserializerTest {

  private final RecordingEvaluator evaluator = new RecordingEvaluator();

  private final ObjectMapper mapper =
      new ObjectMapper()
          .registerModule(new JacksonModuleFeelFunction(true, evaluator))
          .registerModule(new JacksonModuleSecretReference());

  record PlainString(String hmacSecret) {}

  record StringMap(Map<String, String> headers) {}

  record StringList(List<String> values) {}

  record Untyped(Object body) {}

  record Annotated(@FEEL String token) {}

  record Nested(PlainString auth) {}

  @Test
  void resolvesAReferenceInAFieldFeelNeverEvaluates() {
    evaluator.resolves("=camunda.secrets.HMAC", "hmac-value");

    var bound = read("{\"hmacSecret\":\"=camunda.secrets.HMAC\"}", PlainString.class);

    assertThat(bound.hmacSecret()).isEqualTo("hmac-value");
  }

  @Test
  void resolvesAReferenceInAMapValue() {
    // An Authorization header is exactly where a secret goes, and the polling connector binds its
    // headers as Map<String, String>.
    evaluator.resolves("=\"Bearer \" + camunda.secrets.TOKEN", "Bearer tok-1");

    var bound =
        read(
            "{\"headers\":{\"Authorization\":\"=\\\"Bearer \\\" + camunda.secrets.TOKEN\","
                + "\"Accept\":\"application/json\"}}",
            StringMap.class);

    assertThat(bound.headers())
        .containsExactlyInAnyOrderEntriesOf(
            Map.of("Authorization", "Bearer tok-1", "Accept", "application/json"));
  }

  @Test
  void resolvesAReferenceInAListElement() {
    evaluator.resolves("=camunda.secrets.A", "a-value");

    var bound = read("{\"values\":[\"=camunda.secrets.A\",\"plain\"]}", StringList.class);

    assertThat(bound.values()).containsExactly("a-value", "plain");
  }

  @Test
  void resolvesAReferenceReachedThroughAnUntypedField() {
    evaluator.resolves("=camunda.secrets.A", "a-value");

    var bound = read("{\"body\":{\"key\":\"=camunda.secrets.A\"}}", Untyped.class);

    assertThat(bound.body()).isEqualTo(Map.of("key", "a-value"));
  }

  @Test
  void resolvesAReferenceInAnUntypedScalarField() {
    evaluator.resolves("=camunda.secrets.A", "a-value");

    var bound = read("{\"body\":\"=camunda.secrets.A\"}", Untyped.class);

    assertThat(bound.body()).isEqualTo("a-value");
  }

  @Test
  void resolvesAReferenceInANestedProperty() {
    evaluator.resolves("=camunda.secrets.HMAC", "hmac-value");

    var bound = read("{\"auth\":{\"hmacSecret\":\"=camunda.secrets.HMAC\"}}", Nested.class);

    assertThat(bound.auth().hmacSecret()).isEqualTo("hmac-value");
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "plain value",
        "a,b,c",
        "\"quoted\"",
        "{\"key\": \"value\"}",
        "[1, 2, 3]",
        "camunda.secrets.NO_LEADING_EQUALS",
        "Bearer camunda.secrets.EMBEDDED_WITHOUT_EQUALS",
        "=1 + 1",
        "=camunda.vars.env.setting",
        // Carry the prefix as a substring without naming the engine's camunda root, so the cluster
        // reports no secret for any of them. A plain property holding one stays a plain property:
        // it is not sent for evaluation at all.
        "=mycamunda.secrets.TOKEN",
        "=foo.camunda.secrets.TOKEN",
        "=camunda.secrets2.TOKEN",
        "=`camunda.secrets.TOKEN`",
        "=camunda.secrets.",
        "",
        " "
      })
  void bindsAValueWithoutAReferenceExactlyAsBefore(String value) throws Exception {
    String json = mapper.writeValueAsString(new PlainString(value));

    var bound = read(json, PlainString.class);

    assertThat(bound.hmacSecret()).isEqualTo(value);
    assertThat(evaluator.evaluated).isEmpty();
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "=camunda.secrets.TOKEN",
        "=\"Bearer \" + camunda.secrets.TOKEN",
        // A dashed name is only ever authored backtick-escaped: FEEL reads a bare dash as minus.
        "=camunda.secrets.`db-password`",
        "=(camunda.secrets.TOKEN)",
        "={header: camunda.secrets.TOKEN}",
        "=[camunda.secrets.TOKEN]",
        "=if flag then camunda.secrets.TOKEN else null"
      })
  void evaluatesAReferenceWhateverSurroundsIt(String value) throws Exception {
    String json = mapper.writeValueAsString(new PlainString(value));

    read(json, PlainString.class);

    assertThat(evaluator.evaluated).containsExactly(value);
  }

  @Test
  void bindsANullWithoutEvaluating() {
    var bound = read("{\"hmacSecret\":null}", PlainString.class);

    assertThat(bound.hmacSecret()).isNull();
    assertThat(evaluator.evaluated).isEmpty();
  }

  @Test
  void leavesAnAnnotatedPropertyToTheFeelDeserializer() {
    // A property-level deserializer wins over a type-registered one, so a @FEEL field keeps
    // evaluating everything it evaluated before, reference or not.
    evaluator.resolves("=camunda.secrets.TOKEN", "tok-1");

    var bound = read("{\"token\":\"=camunda.secrets.TOKEN\"}", Annotated.class);

    assertThat(bound.token()).isEqualTo("tok-1");
    assertThat(evaluator.evaluated).containsExactly("=camunda.secrets.TOKEN");
  }

  record WithLocator(java.util.function.Function<Object, String> apiKeyLocator) {}

  @Test
  void leavesAReferenceReturnedByADeferredCallbackAlone() throws Exception {
    // Mirrors the webhook API-key path: apiKeyLocator is a Function<Object, String> applied to the
    // incoming request at request time. Its result is attacker-controlled data, and converting it
    // must not evaluate it — otherwise a caller could send a header reading "=camunda.secrets.X"
    // and have the runtime hand back the real secret it is about to be compared against.
    evaluator.resolves("=request.headers.authorization", "=camunda.secrets.API_KEY");
    evaluator.resolves("=camunda.secrets.API_KEY", "the-real-key");
    // A Function property keeps its module-configured evaluator, so it has to be wired as the
    // function evaluator for the callback to run against the recording double at all.
    var withCallback =
        new ObjectMapper()
            .registerModule(
                new JacksonModuleFeelFunction(true, evaluator, evaluator, new ObjectMapper()))
            .registerModule(new JacksonModuleSecretReference());

    WithLocator bound =
        FeelContextAwareObjectReader.of(withCallback)
            .withEvaluator(evaluator)
            .readValue("{\"apiKeyLocator\":\"=request.headers.authorization\"}", WithLocator.class);
    String located = bound.apiKeyLocator().apply(Map.of("headers", Map.of("authorization", "x")));

    assertThat(located).isEqualTo("=camunda.secrets.API_KEY");
    assertThat(evaluator.evaluated).doesNotContain("=camunda.secrets.API_KEY");
  }

  @Test
  void leavesAReferenceAloneWhenTheReaderCarriesNoEvaluator() {
    // The inbound mapper is also used to round-trip raw properties through legacy secret
    // replacement, with no evaluator on the reader. The reference has to survive that untouched,
    // or the property binding that follows would never see it.
    evaluator.resolves("=camunda.secrets.HMAC", "hmac-value");

    var bound =
        readWithoutEvaluator("{\"hmacSecret\":\"=camunda.secrets.HMAC\"}", PlainString.class);

    assertThat(bound.hmacSecret()).isEqualTo("=camunda.secrets.HMAC");
    assertThat(evaluator.evaluated).isEmpty();
  }

  @Test
  void keepsTheReferenceVisibleWhenEvaluationAnswersWithNothing() {
    // An older cluster does not preserve the reference through evaluation. Binding the property as
    // empty would hide that; leaving the reference in place makes it diagnosable.
    var bound = read("{\"hmacSecret\":\"=camunda.secrets.HMAC\"}", PlainString.class);

    assertThat(bound.hmacSecret()).isEqualTo("=camunda.secrets.HMAC");
    assertThat(evaluator.evaluated).containsExactly("=camunda.secrets.HMAC");
  }

  @Test
  void usesTheEvaluatorTheReaderCarries() {
    // The runtime hands each connector context its own cluster-backed, secret-resolving evaluator
    // through the reader, not through the module.
    var perReader = new RecordingEvaluator();
    perReader.resolves("=camunda.secrets.HMAC", "from-reader");

    PlainString bound =
        readWith(perReader, "{\"hmacSecret\":\"=camunda.secrets.HMAC\"}", PlainString.class);

    assertThat(bound.hmacSecret()).isEqualTo("from-reader");
    assertThat(evaluator.evaluated).isEmpty();
  }

  @Test
  void evaluatesOnlyTheValuesThatNameAReference() {
    evaluator.resolves("=camunda.secrets.A", "a-value");

    read(
        "{\"headers\":{\"one\":\"=camunda.secrets.A\",\"two\":\"=camunda.vars.env.x\","
            + "\"three\":\"plain\"}}",
        StringMap.class);

    assertThat(evaluator.evaluated).containsExactly("=camunda.secrets.A");
  }

  private <T> T read(String json, Class<T> type) {
    return readWith(evaluator, json, type);
  }

  /** Binds without a reader-supplied evaluator, as the legacy property round-trip does. */
  private <T> T readWithoutEvaluator(String json, Class<T> type) {
    try {
      return mapper.readValue(json, type);
    } catch (Exception e) {
      throw new AssertionError("failed to bind " + json, e);
    }
  }

  private <T> T readWith(FeelExpressionEvaluator readerEvaluator, String json, Class<T> type) {
    try {
      return FeelContextAwareObjectReader.of(mapper)
          .withEvaluator(readerEvaluator)
          .readValue(json, type);
    } catch (Exception e) {
      throw new AssertionError("failed to bind " + json, e);
    }
  }

  /** Answers only the expressions it was told about, and records every expression it saw. */
  private static final class RecordingEvaluator implements FeelExpressionEvaluator {
    private final Map<String, String> answers = new java.util.LinkedHashMap<>();
    private final List<String> evaluated = new ArrayList<>();

    private void resolves(String expression, String value) {
      answers.put(expression, value);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, Object... variables) {
      evaluated.add(expression);
      return (T) answers.get(expression);
    }

    @Override
    public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
      evaluated.add(expression);
      return targetType.cast(answers.get(expression));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, JavaType targetType, Object... variables) {
      evaluated.add(expression);
      return (T) answers.get(expression);
    }

    @Override
    public String evaluateToJson(String expression, Object... variables) {
      throw new UnsupportedOperationException();
    }
  }
}
