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
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JavaType;
import io.camunda.connector.api.annotation.FEEL;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentSerializer;
import io.camunda.connector.feel.FeelExpressionEvaluator;
import io.camunda.connector.feel.jackson.FeelContextAwareObjectReader;
import io.camunda.connector.feel.jackson.JacksonModuleFeelFunction;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import io.camunda.connector.runtime.core.FeelEvaluationResultMapper;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * A document reaching a property through a FEEL evaluation is bound by the result mapper, and the
 * runtime's result mapper resolves its {@link DocumentFactory} per call from the {@link
 * DocumentFactory#PHYSICAL_TENANT_ID_ATTRIBUTE} reader attribute. Result conversion has to carry
 * that attribute over from the binding that started it, or a runtime serving more than one physical
 * tenant cannot resolve a factory at all.
 */
@ExtendWith(MockitoExtension.class)
class FeelResultPhysicalTenantIdTest {

  private final CamundaDocumentStore storeA = mock(CamundaDocumentStore.class);
  private final CamundaDocumentStore storeB = mock(CamundaDocumentStore.class);
  private final DocumentFactory factoryA = new DocumentFactoryImpl(storeA);
  private final DocumentFactory factoryB = new DocumentFactoryImpl(storeB);
  private final IntrinsicFunctionExecutor functions = mock(IntrinsicFunctionExecutor.class);
  private final Map<String, DocumentFactory> factories =
      Map.of("tenant-a", factoryA, "tenant-b", factoryB);

  record AnnotatedDocument(@FEEL Document doc) {}

  record DeferredDocument(Function<Object, Document> loader) {}

  @Test
  void anAnnotatedPropertyResolvesADocumentForTheBindingsPhysicalTenant() throws Exception {
    var ref = createDocumentMock("Hello from tenant B", null, storeB);
    var evaluator = new StubEvaluator(referenceAsData(ref));

    AnnotatedDocument bound =
        readerFor(evaluator).readValue("{\"doc\":\"=someExpression\"}", AnnotatedDocument.class);

    assertThat(bound.doc().reference()).isEqualTo(ref);
  }

  @Test
  void aDeferredCallbackResolvesADocumentForTheBindingsPhysicalTenant() throws Exception {
    var ref = createDocumentMock("Hello from tenant B", null, storeB);
    var evaluator = new StubEvaluator(referenceAsData(ref));

    DeferredDocument bound =
        readerFor(evaluator).readValue("{\"loader\":\"=someExpression\"}", DeferredDocument.class);

    assertThat(bound.loader().apply(Map.of()).reference()).isEqualTo(ref);
  }

  /** The document reference as the cluster hands it back: plain JSON data in the result. */
  private Object referenceAsData(Object reference) {
    return ConnectorsObjectMapperSupplier.getCopy().convertValue(reference, Map.class);
  }

  /** The inbound property mapper as the runtime wires it, for a tenant-b binding. */
  private com.fasterxml.jackson.databind.ObjectReader readerFor(FeelExpressionEvaluator evaluator) {
    var propertyMapper = ConnectorsObjectMapperSupplier.getCopy();
    propertyMapper.registerModules(
        new JacksonModuleDocumentDeserializer(
            factories,
            functions,
            JacksonModuleDocumentDeserializer.DocumentModuleSettings.create()),
        new JacksonModuleFeelFunction(
            true, evaluator, evaluator, FeelEvaluationResultMapper.create(factories)),
        new JacksonModuleDocumentSerializer());
    return FeelContextAwareObjectReader.of(propertyMapper)
        .withEvaluator(evaluator)
        .withAttribute(DocumentFactory.PHYSICAL_TENANT_ID_ATTRIBUTE, "tenant-b");
  }

  private record StubEvaluator(Object answer) implements FeelExpressionEvaluator {
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
