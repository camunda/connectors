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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.InvalidTypeIdException;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.DocumentReferenceModel;
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
 * A {@code @FEEL} property is bound by the FEEL deserializer, which must still hand documents and
 * intrinsic functions to the document module rather than binding them as plain JSON. A document
 * returned by an expression materialises too; an intrinsic function returned by one does not run,
 * since evaluation results are bound without an intrinsic function executor.
 */
@ExtendWith(MockitoExtension.class)
class FeelPropertyDocumentInteropTest {

  private static final ObjectMapper PLAIN = new ObjectMapper();

  private final CamundaDocumentStore store = mock(CamundaDocumentStore.class);
  private final DocumentFactory factory = new DocumentFactoryImpl(store);
  private final IntrinsicFunctionExecutor functions = mock(IntrinsicFunctionExecutor.class);

  record StringProps(@FEEL String value) {}

  record DocumentProps(@FEEL Document value) {}

  record ObjectProps(@FEEL Object value) {}

  @Test
  void aDocumentReferenceBindsAsBase64OnAStringProperty() {
    var reference = createDocumentMock("Hello World", null, store);

    var bound = inboundMapper().convertValue(Map.of("value", reference), StringProps.class);

    assertThat(bound.value()).isEqualTo(base64("Hello World"));
  }

  @Test
  void anIntrinsicFunctionIsExecutedOnAStringProperty() {
    when(functions.execute(any(), any())).thenReturn("executed");

    var bound =
        inboundMapper().convertValue(Map.of("value", intrinsicFunction()), StringProps.class);

    assertThat(bound.value()).isEqualTo("executed");
  }

  @Test
  void aDocumentReferenceReturnedByAnExpressionBindsAsBase64OnAStringProperty() throws Exception {
    var reference = createDocumentMock("Hello World", null, store);

    StringProps bound = evaluate(PLAIN.convertValue(reference, Map.class), StringProps.class);

    assertThat(bound.value()).isEqualTo(base64("Hello World"));
  }

  @Test
  void anIntrinsicFunctionReturnedByAnExpressionIsRefusedOnAStringProperty() {
    // Evaluation results are bound without an intrinsic function executor: the function must be
    // refused, never run through the property mapper's executor nor passed on as JSON text.
    assertThatThrownBy(() -> evaluate(intrinsicFunction(), StringProps.class))
        .hasRootCauseInstanceOf(UnsupportedOperationException.class)
        .rootCause()
        .hasMessageContaining("Intrinsic function dispatch is disabled");
    verify(functions, never()).execute(any(), any());
  }

  @Test
  void anInvalidDocumentReferenceFailsOnAStringProperty() {
    // The document module took the payload but could not bind it: that error must surface rather
    // than the payload being bound as its JSON text.
    var invalid = Map.of(DocumentReferenceModel.DISCRIMINATOR_KEY, "unknown");

    assertThatThrownBy(
            () -> inboundMapper().convertValue(Map.of("value", invalid), StringProps.class))
        .hasRootCauseInstanceOf(InvalidTypeIdException.class);
    assertThatThrownBy(() -> evaluate(invalid, StringProps.class))
        .hasRootCauseInstanceOf(InvalidTypeIdException.class);
  }

  @Test
  void aPlainObjectIsStillSerializedOnAStringProperty() throws Exception {
    StringProps bound = evaluate(Map.of("a", 1), StringProps.class);

    assertThat(bound.value()).isEqualTo("{\"a\":1}");
  }

  @Test
  void aDocumentReferenceBindsOnADocumentProperty() throws Exception {
    var direct =
        inboundMapper()
            .convertValue(
                Map.of("value", createDocumentMock("Hello World", null, store)),
                DocumentProps.class);
    DocumentProps evaluated =
        evaluate(
            PLAIN.convertValue(createDocumentMock("Hello World", null, store), Map.class),
            DocumentProps.class);

    assertThat(direct.value().asByteArray()).isEqualTo("Hello World".getBytes());
    assertThat(evaluated.value().asByteArray()).isEqualTo("Hello World".getBytes());
  }

  @Test
  void anIntrinsicFunctionIsExecutedOnAnObjectProperty() {
    when(functions.execute(any(), any())).thenReturn("executed");

    var bound =
        inboundMapper().convertValue(Map.of("value", intrinsicFunction()), ObjectProps.class);

    assertThat(bound.value()).isEqualTo("executed");
  }

  private static Map<String, Object> intrinsicFunction() {
    return Map.of(IntrinsicFunctionModel.DISCRIMINATOR_KEY, "someFunction");
  }

  private static String base64(String content) {
    return Base64.getEncoder().encodeToString(content.getBytes());
  }

  private <T> T evaluate(Object result, Class<T> type) throws Exception {
    return FeelContextAwareObjectReader.of(inboundMapper())
        .withEvaluator(new StubEvaluator(result))
        .readValue("{\"value\":\"=expression\"}", type);
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

  private record StubEvaluator(Object answer) implements FeelExpressionEvaluator {
    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, Object... variables) {
      return (T) answer;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T evaluate(String expression, Class<T> targetType, Object... variables) {
      return (T) answer;
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
