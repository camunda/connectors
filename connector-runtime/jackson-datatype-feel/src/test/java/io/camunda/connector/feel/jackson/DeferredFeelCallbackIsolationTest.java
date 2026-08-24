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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * Values returned by an evaluation are bound by the result mapper, which registers neither the FEEL
 * nor the secret-reference module.
 *
 * <p>Deferred {@code Function}/{@code Supplier} properties are bound once and invoked many times —
 * for an inbound webhook, once per request and therefore concurrently — all sharing the {@link
 * DeserializationContext} captured at bind time. These tests force that interleaving and assert
 * what has to hold under it: every invocation evaluates its own expression, and caller-supplied
 * {@code =camunda.secrets.<name>} text returned by one invocation stays literal, including while
 * another invocation is converting its own result.
 */
class DeferredFeelCallbackIsolationTest {

  private static final String REAL_SECRET = "the-real-key";
  private static final String REFERENCE = "=camunda.secrets.API_KEY";

  /**
   * Its own threads, not the common pool: these tests hold a worker at a gate until another
   * invocation has run to completion, and the common pool has one worker on a two-processor
   * machine, where that would deadlock rather than test anything.
   */
  private final ExecutorService threads = Executors.newCachedThreadPool();

  @AfterEach
  void clearGatesAndThreads() {
    GatedStringDeserializer.GATES.clear();
    threads.shutdownNow();
  }

  private <T> CompletableFuture<T> onItsOwnThread(Supplier<T> work) {
    return CompletableFuture.supplyAsync(work, threads);
  }

  @Test
  @Timeout(30)
  void anInvocationEvaluatesWhileAnotherIsConvertingItsResult() throws Exception {
    // The first invocation is held inside the conversion of its own result while the second runs
    // from start to finish. The second must evaluate its own expression.
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=slow", Map.of("value", "gate-a"));
    evaluator.answers("=token", "token-value");
    var gateA = GatedStringDeserializer.gate("gate-a");
    var bound =
        read(evaluator, "{\"slow\":\"=slow\",\"token\":\"=token\"}", ConvertingAndSupplying.class);

    var converting = onItsOwnThread(() -> bound.slow().apply(Map.of()));
    gateA.awaitEntry();
    String token = bound.token().get();
    gateA.release();

    assertThat(token).isEqualTo("token-value");
    assertThat(converting.get(20, TimeUnit.SECONDS).value()).isEqualTo("gate-a");
    assertThat(evaluator.evaluated).containsExactlyInAnyOrder("=slow", "=token");
  }

  @Test
  @Timeout(30)
  void anInvocationFinishingItsConversionDoesNotUnmaskAnotherStillInsideOne() throws Exception {
    // Two invocations converting at once, one finishing while the other is still binding the
    // fields of its own result. The reference the second one is carrying is caller-supplied data
    // and must not reach the evaluator.
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=first", Map.of("value", "gate-a"));
    var victimResult = new LinkedHashMap<String, Object>();
    victimResult.put("gate", "gate-b");
    victimResult.put("candidate", REFERENCE);
    evaluator.answers("=second", victimResult);
    evaluator.answers(REFERENCE, REAL_SECRET);
    // Three points to stop at: the second invocation between evaluating and converting, and each
    // invocation partway through its conversion.
    var evaluated = evaluator.gateOn("=second");
    var gateA = GatedStringDeserializer.gate("gate-a");
    var gateB = GatedStringDeserializer.gate("gate-b");
    var bound =
        read(evaluator, "{\"first\":\"=first\",\"second\":\"=second\"}", TwoConverting.class);

    // Both invocations evaluate before either converts, which is what the shared mark could not
    // survive: each then converts believing it was the one that set the mark.
    var second = onItsOwnThread(() -> bound.second().apply(Map.of()));
    evaluated.awaitEntry();
    var first = onItsOwnThread(() -> bound.first().apply(Map.of()));
    gateA.awaitEntry();
    evaluated.release();
    gateB.awaitEntry();
    // The outer conversion runs its restore while the inner one is still binding its next field.
    gateA.release();
    first.get(20, TimeUnit.SECONDS);
    gateB.release();

    assertThat(second.get(20, TimeUnit.SECONDS).candidate()).isEqualTo(REFERENCE);
    assertThat(evaluator.evaluated).doesNotContain(REFERENCE);
  }

  @Test
  void aFunctionTypedFieldInsideAnEvaluationResultDoesNotBind() {
    // An evaluation result can carry expression-shaped text anywhere in it. The result mapper
    // registers no Function deserializer, so such text cannot become a callback over itself: a
    // callback's source comes from the model.
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=outer", Map.of("locator", REFERENCE));
    evaluator.answers(REFERENCE, REAL_SECRET);

    assertThatThrownBy(() -> read(evaluator, "{\"payload\":\"=outer\"}", ResultWithFunction.class))
        .rootCause()
        .isInstanceOf(InvalidDefinitionException.class);
    assertThat(evaluator.evaluated).containsExactly("=outer");
  }

  @Test
  void aSupplierTypedFieldInsideAnEvaluationResultDoesNotBind() {
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=outer", Map.of("value", REFERENCE));
    evaluator.answers(REFERENCE, REAL_SECRET);

    assertThatThrownBy(() -> read(evaluator, "{\"payload\":\"=outer\"}", ResultWithSupplier.class))
        .rootCause()
        .isInstanceOf(InvalidDefinitionException.class);
    assertThat(evaluator.evaluated).containsExactly("=outer");
  }

  @Test
  void aFieldPointedAtTheFeelDeserializerInsideAResultCannotEvaluate() {
    // A model may name the deserializer directly rather than through @FEEL, and such a field is
    // registered on any mapper that binds its type. The result reader carries an evaluator that
    // answers with the text, so the field cannot reach the binding's cluster-backed evaluator and
    // cannot fall back to a local engine either: the text binds as the text.
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=outer", Map.of("candidate", REFERENCE));
    evaluator.answers(REFERENCE, REAL_SECRET);

    var bound = read(evaluator, "{\"payload\":\"=outer\"}", ResultWithExplicitFeel.class);

    assertThat(bound.payload().candidate()).isEqualTo(REFERENCE);
    assertThat(evaluator.evaluated).containsExactly("=outer");
  }

  @Test
  void expressionShapedResultTextIsNotComputed() {
    // Not only secret references: any expression in a result is data. A local engine would compute
    // this one, and would run a FEEL function contributed through the SPI.
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=outer", Map.of("candidate", "=1+1"));

    var bound = read(evaluator, "{\"payload\":\"=outer\"}", ResultWithExplicitFeel.class);

    assertThat(bound.payload().candidate()).isEqualTo("=1+1");
    assertThat(evaluator.evaluated).containsExactly("=outer");
  }

  @Test
  void aReferenceInAnEvaluationResultStaysLiteral() {
    // The plain-string case: the property mapper resolves such a reference, the result mapper does
    // not, so text that arrived as data keeps its shape.
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=outer", Map.of("candidate", REFERENCE));
    evaluator.answers(REFERENCE, REAL_SECRET);

    var bound = read(evaluator, "{\"payload\":\"=outer\"}", ResultWithString.class);

    assertThat(bound.payload().candidate()).isEqualTo(REFERENCE);
    assertThat(evaluator.evaluated).containsExactly("=outer");
  }

  @Test
  void aCallbackBoundFromTheModelKeepsEvaluating() {
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=request.body", "value");
    var bound = read(evaluator, "{\"apiKeyLocator\":\"=request.body\"}", WithLocator.class);

    assertThat(bound.apiKeyLocator().apply(Map.of())).isEqualTo("value");
    assertThat(evaluator.evaluated).containsExactly("=request.body");
  }

  @Test
  void aNestedFeelFieldInsideAnEvaluationResultIsNotEvaluated() {
    // The result mapper processes no @FEEL annotation, so the text reaches a plain List<String>
    // field as data and fails to bind as a list, rather than being evaluated one level down.
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=outer", Map.of("tags", "=camunda.secrets.LIST"));
    evaluator.answers("=camunda.secrets.LIST", List.of(REAL_SECRET));

    assertThatThrownBy(() -> read(evaluator, "{\"payload\":\"=outer\"}", ResultWithFeelList.class))
        .rootCause()
        .isInstanceOf(MismatchedInputException.class);
    assertThat(evaluator.evaluated).containsExactly("=outer");
  }

  @Test
  void aPropertyStillResolvesAfterACallbackHasRunOnTheSameThread() {
    // The two mappers do not interfere: a callback returning reference-shaped data leaves it
    // literal, while a model property naming the same reference resolves.
    var evaluator = new RecordingEvaluator();
    evaluator.answers("=request.body", "=camunda.secrets.HMAC");
    evaluator.answers("=camunda.secrets.HMAC", "hmac-value");
    var callback = read(evaluator, "{\"apiKeyLocator\":\"=request.body\"}", WithLocator.class);

    assertThat(callback.apiKeyLocator().apply(Map.of())).isEqualTo("=camunda.secrets.HMAC");
    var bound = read(evaluator, "{\"hmacSecret\":\"=camunda.secrets.HMAC\"}", PlainString.class);

    assertThat(bound.hmacSecret()).isEqualTo("hmac-value");
  }

  private <T> T read(FeelExpressionEvaluator evaluator, String json, Class<T> type) {
    // A Function/Supplier property keeps its module-configured evaluator, so the double has to be
    // wired as the function evaluator too for the callback to run against it at all.
    // As the runtime wires it: a property mapper that evaluates and resolves, and a result mapper
    // that registers neither module.
    var resultMapper = new ObjectMapper();
    var mapper =
        new ObjectMapper()
            .registerModule(new JacksonModuleFeelFunction(true, evaluator, evaluator, resultMapper))
            .registerModule(new JacksonModuleSecretReference());
    try {
      return FeelContextAwareObjectReader.of(mapper).withEvaluator(evaluator).readValue(json, type);
    } catch (Exception e) {
      throw new AssertionError("failed to bind " + json, e);
    }
  }

  record WithLocator(Function<Object, String> apiKeyLocator) {}

  record PlainString(String hmacSecret) {}

  record ConvertingAndSupplying(Function<Object, Gated> slow, Supplier<String> token) {}

  record TwoConverting(Function<Object, Gated> first, Function<Object, Victim> second) {}

  record Gated(@JsonDeserialize(using = GatedStringDeserializer.class) String value) {}

  /** A value bound field by field, with a gate to stop inside it and a plain string after it. */
  record Victim(
      @JsonDeserialize(using = GatedStringDeserializer.class) String gate, String candidate) {}

  record NestedFunction(Function<Object, String> locator) {}

  record ResultWithFunction(@FEEL NestedFunction payload) {}

  record NestedSupplier(Supplier<String> value) {}

  record ResultWithSupplier(@FEEL NestedSupplier payload) {}

  record NestedFeelList(@FEEL List<String> tags) {}

  record NestedString(String candidate) {}

  record NestedExplicitFeel(@JsonDeserialize(using = FeelDeserializer.class) Object candidate) {}

  record ResultWithExplicitFeel(@FEEL NestedExplicitFeel payload) {}

  record ResultWithString(@FEEL NestedString payload) {}

  record ResultWithFeelList(@FEEL NestedFeelList payload) {}

  /**
   * Stops a conversion in the middle of binding a value, so another conversion can be driven to a
   * chosen point while it waits. A gate is keyed by the string it stops on.
   */
  static final class GatedStringDeserializer extends JsonDeserializer<String> {

    static final Map<String, Gate> GATES = new ConcurrentHashMap<>();

    static Gate gate(String value) {
      Gate gate = new Gate();
      GATES.put(value, gate);
      return gate;
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {
      String value = parser.getValueAsString();
      Gate gate = GATES.get(value);
      if (gate != null) {
        gate.enterAndWait();
      }
      return value;
    }

    static final class Gate {
      private final CountDownLatch entered = new CountDownLatch(1);
      private final CountDownLatch released = new CountDownLatch(1);

      void awaitEntry() throws InterruptedException {
        if (!entered.await(20, TimeUnit.SECONDS)) {
          throw new IllegalStateException("the conversion never reached the gate");
        }
      }

      void release() {
        released.countDown();
      }

      void enterAndWait() {
        entered.countDown();
        try {
          if (!released.await(20, TimeUnit.SECONDS)) {
            throw new IllegalStateException("the gate was never released");
          }
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }
    }
  }

  /** Answers only what it was told about, and records every expression it saw. */
  private static final class RecordingEvaluator implements FeelExpressionEvaluator {
    private final Map<String, Object> answers = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<String> evaluated = new ConcurrentLinkedQueue<>();

    private final Map<String, GatedStringDeserializer.Gate> gates = new ConcurrentHashMap<>();

    void answers(String expression, Object value) {
      answers.put(expression, value);
    }

    /** Stops an evaluation on its way out, before its result is converted. */
    GatedStringDeserializer.Gate gateOn(String expression) {
      var gate = new GatedStringDeserializer.Gate();
      gates.put(expression, gate);
      return gate;
    }

    private Object answer(String expression) {
      evaluated.add(expression);
      Object value = answers.get(expression);
      var gate = gates.get(expression);
      if (gate != null) {
        gate.enterAndWait();
      }
      return value;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, Object... variables) {
      return (T) answer(expression);
    }

    @Override
    public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
      return targetType.cast(answer(expression));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, JavaType targetType, Object... variables) {
      return (T) answer(expression);
    }

    @Override
    public String evaluateToJson(String expression, Object... variables) {
      throw new UnsupportedOperationException();
    }
  }
}
