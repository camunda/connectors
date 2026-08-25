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
package io.camunda.connector.runtime.core.document;

import static io.camunda.connector.runtime.core.document.DocumentDeserializationTest.createDocumentMock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.IntrinsicFunctionModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.feel.jackson.JacksonModuleSecretReference;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.FeelEvaluationResultMapper;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.util.Base64;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * The inbound mapper registers two things that both act on {@code String}: the document module's,
 * which turns a document reference into base64 and runs an intrinsic function, and the
 * secret-reference one. They compose — the second wraps the first rather than replacing it — so a
 * property gets all three behaviours.
 */
@ExtendWith(MockitoExtension.class)
class StringPropertyCompositionTest {

  private final CamundaDocumentStore store = mock(CamundaDocumentStore.class);
  private final DocumentFactory factory = new DocumentFactoryImpl(store);
  private final IntrinsicFunctionExecutor functions = mock(IntrinsicFunctionExecutor.class);

  record Props(String value) {}

  record UntypedProps(Object value) {}

  record MapProps(Map<String, Object> values) {}

  record UntypedResult(@FEEL Object payload) {}

  @Test
  void aDocumentReferenceStillBindsAsBase64() {
    var reference = createDocumentMock("Hello World", null, store);

    var bound = inboundMapper().convertValue(Map.of("value", reference), Props.class);

    assertThat(bound.value())
        .isEqualTo(Base64.getEncoder().encodeToString("Hello World".getBytes()));
  }

  @Test
  void anIntrinsicFunctionIsStillExecuted() {
    when(functions.execute(any(), any())).thenReturn("executed");

    var bound =
        inboundMapper()
            .convertValue(
                Map.of("value", Map.of(IntrinsicFunctionModel.DISCRIMINATOR_KEY, "someFunction")),
                Props.class);

    assertThat(bound.value()).isEqualTo("executed");
  }

  @Test
  void aSecretReferenceStillResolves() throws Exception {
    FeelExpressionEvaluator evaluator = new StubEvaluator("resolved-secret");

    Props bound =
        FeelContextAwareObjectReader.of(inboundMapper())
            .withEvaluator(evaluator)
            .readValue("{\"value\":\"=camunda.secrets.TOKEN\"}", Props.class);

    assertThat(bound.value()).isEqualTo("resolved-secret");
  }

  @Test
  void aPlainStringIsStillBoundAsItStands() {
    var bound = inboundMapper().convertValue(Map.of("value", "just text"), Props.class);

    assertThat(bound.value()).isEqualTo("just text");
  }

  @Test
  void aSecretReferenceResolvesThroughAnUntypedField() throws Exception {
    // The document module registers its own Object.class deserializer, whose scalar fallback must
    // not bypass whatever the mapper composes onto String.
    FeelExpressionEvaluator evaluator = new StubEvaluator("resolved-secret");

    UntypedProps bound =
        FeelContextAwareObjectReader.of(inboundMapper())
            .withEvaluator(evaluator)
            .readValue("{\"value\":\"=camunda.secrets.TOKEN\"}", UntypedProps.class);

    assertThat(bound.value()).isEqualTo("resolved-secret");
  }

  @Test
  void aSecretReferenceResolvesThroughAMapOfObjectValues() throws Exception {
    FeelExpressionEvaluator evaluator = new StubEvaluator("resolved-secret");

    MapProps bound =
        FeelContextAwareObjectReader.of(inboundMapper())
            .withEvaluator(evaluator)
            .readValue("{\"values\":{\"token\":\"=camunda.secrets.TOKEN\"}}", MapProps.class);

    assertThat(bound.values()).containsExactly(Map.entry("token", "resolved-secret"));
  }

  @Test
  void aSecretReferenceInsideAnEvaluationResultObjectStaysLiteral() throws Exception {
    // FeelEvaluationResultMapper registers the document module — so an Object-typed field inside a
    // FEEL evaluation result still binds through ObjectDeserializer — but not the secret-reference
    // module. Text that arrived as evaluation-result data must never be sent back for evaluation,
    // even now that the Object path routes a scalar string through the context.
    var evaluated = new java.util.ArrayList<String>();
    FeelExpressionEvaluator evaluator =
        new FeelExpressionEvaluator() {
          @SuppressWarnings("unchecked")
          private <T> T answer(String expression) {
            evaluated.add(expression);
            if ("=outer".equals(expression)) {
              return (T) Map.of("candidate", "=camunda.secrets.TOKEN");
            }
            return (T) "the-real-secret";
          }

          @Override
          public <T> T evaluate(String expression, Object... variables) {
            return answer(expression);
          }

          @Override
          public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
            return answer(expression);
          }

          @Override
          public <T> T evaluate(String expression, JavaType targetType, Object... variables) {
            return answer(expression);
          }

          @Override
          public String evaluateToJson(String expression, Object... variables) {
            throw new UnsupportedOperationException();
          }
        };

    UntypedResult bound =
        FeelContextAwareObjectReader.of(inboundMapper())
            .withEvaluator(evaluator)
            .readValue("{\"payload\":\"=outer\"}", UntypedResult.class);

    assertThat(((Map<?, ?>) bound.payload()).get("candidate")).isEqualTo("=camunda.secrets.TOKEN");
    assertThat(evaluated).containsExactly("=outer");
  }

  /** The inbound property mapper, as the runtime wires it. */
  private ObjectMapper inboundMapper() {
    var mapper = ConnectorsObjectMapperSupplier.getCopy();
    mapper.registerModules(
        new JacksonModuleDocumentDeserializer(
            factory, functions, JacksonModuleDocumentDeserializer.DocumentModuleSettings.create()),
        new JacksonModuleFeelFunction(FeelEvaluationResultMapper.create(factory)),
        new JacksonModuleSecretReference(),
        new JacksonModuleDocumentSerializer());
    return mapper;
  }

  private record StubEvaluator(String answer) implements FeelExpressionEvaluator {
    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, Object... variables) {
      return (T) answer;
    }

    @Override
    public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
      return targetType.cast(answer);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, JavaType targetType, Object... variables) {
      return (T) answer;
    }

    @Override
    public String evaluateToJson(String expression, Object... variables) {
      throw new UnsupportedOperationException();
    }
  }
}
