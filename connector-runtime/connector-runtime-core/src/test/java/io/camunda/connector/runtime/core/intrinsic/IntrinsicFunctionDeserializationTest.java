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
package io.camunda.connector.runtime.core.intrinsic;

import static io.camunda.connector.runtime.core.document.DocumentDeserializationTest.createDocumentMock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.IntrinsicFunctionExecutor;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.connector.document.jackson.v3.JacksonModuleDocumentSerializer;
import io.camunda.connector.runtime.core.document.DocumentFactoryImpl;
import io.camunda.connector.runtime.core.document.store.CamundaDocumentStore;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DatabindException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
public class IntrinsicFunctionDeserializationTest {

  private final CamundaDocumentStore documentStore = mock(CamundaDocumentStore.class);

  private final ObjectMapper objectMapper;

  public IntrinsicFunctionDeserializationTest() {
    /*
     * Order of initialization is important here. The operationExecutor is created first and then
     * the objectMapper is created with the operationExecutor. This is because the operationExecutor
     * needs an objectMapper configured with the same modules. ObjectMapper is immutable in Jackson
     * 3, so a MutableObjectMapperSupplier bridges the executor to the final, fully-built mapper.
     */
    var mapperHolder = new MutableObjectMapperSupplier();
    IntrinsicFunctionExecutor operationExecutor =
        spy(new DefaultIntrinsicFunctionExecutor(mapperHolder));

    final var settings = DocumentModuleSettings.create();
    settings.setMaxIntrinsicFunctions(2);
    final DocumentFactory factory = new DocumentFactoryImpl(documentStore);
    objectMapper =
        JsonMapper.builder()
            .addModules(
                new JacksonModuleDocumentDeserializer(factory, operationExecutor, settings),
                new JacksonModuleDocumentSerializer())
            .build();
    mapperHolder.set(objectMapper);
  }

  private record StringResultModel(String result) {}

  @Test
  void operationWithDocumentParameter() {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString, null, documentStore);

    final var payload =
        Map.of(
            "result",
            Map.of("camunda.function.type", "test_documentContent", "params", List.of(ref)));
    final var result = objectMapper.convertValue(payload, StringResultModel.class);

    assertThat(result.result).isEqualTo(contentString);
  }

  @Test
  void wrongOperationName() {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString, null, documentStore);

    final var payload =
        Map.of("result", Map.of("camunda.function.type", "wrong", "params", List.of(ref)));
    final var e =
        assertThrows(
            DatabindException.class,
            () -> objectMapper.convertValue(payload, StringResultModel.class));

    assertThat(e).hasCauseInstanceOf(IllegalArgumentException.class);
    assertThat(e).hasMessageContaining("No intrinsic function found with name: wrong");
  }

  @Test
  void operationWithNullableParameter_acceptsNull() {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString, null, documentStore);

    final var payload =
        Map.of(
            "result",
            Map.of("camunda.function.type", "test_documentContent", "params", List.of(ref)));
    final var result = objectMapper.convertValue(payload, StringResultModel.class);

    assertThat(result.result).isEqualTo(contentString);
  }

  @Test
  void operationWithNullableParameter_acceptsNonNull() {
    var contentString = "Hello World";
    var contentStringInAnotherCharset = contentString.getBytes(StandardCharsets.UTF_16);
    var ref = createDocumentMock(contentStringInAnotherCharset, null, documentStore);

    final var payload =
        Map.of(
            "result",
            Map.of(
                "camunda.function.type", "test_documentContent", "params", List.of(ref, "UTF-16")));
    final var result = objectMapper.convertValue(payload, StringResultModel.class);

    assertThat(result.result).isEqualTo(contentString);
  }

  @Test
  void nestedOperation() {
    var contentString = " World";
    var ref = createDocumentMock(contentString, null, documentStore);

    final var payload =
        Map.of(
            "result",
            Map.of(
                "camunda.function.type",
                "test_concat",
                "params",
                List.of(
                    "Hello",
                    Map.of(
                        "camunda.function.type", "test_documentContent", "params", List.of(ref)))));

    final var result = objectMapper.convertValue(payload, StringResultModel.class);
    assertThat(result.result).isEqualTo("Hello World");
  }

  @Test
  void operationWithObjectParameter_acceptsString() throws JacksonException {
    var string = "Hello World";

    final var payload =
        Map.of(
            "result",
            Map.of("camunda.function.type", "test_anythingToString", "params", List.of(string)));
    final var result = objectMapper.convertValue(payload, StringResultModel.class);

    assertThat(result.result).isEqualTo(objectMapper.writeValueAsString(string));
  }

  @Test
  void operationWithObjectParameter_nestedOperation() throws JacksonException {
    var contentString = " World";
    var ref = createDocumentMock(contentString, null, documentStore);

    final var payload =
        Map.of(
            "result",
            Map.of(
                "camunda.function.type",
                "test_anythingToString",
                "params",
                List.of(
                    Map.of(
                        "camunda.function.type", "test_documentContent", "params", List.of(ref)))));

    final var result = objectMapper.convertValue(payload, StringResultModel.class);
    assertThat(result.result).isEqualTo(objectMapper.writeValueAsString(contentString));
  }

  @Test
  void intrinsicFunctionLimit_Wide() {
    var payload =
        """
            {
              "result": [
                {
                  "camunda.function.type": "test_concat",
                  "params": [ "Hello", " World" ]
                },
                {
                  "camunda.function.type": "test_concat",
                  "params": [ "Hello", " World" ]
                },
                {
                  "camunda.function.type": "test_concat",
                  "params": [ "Hello", " World" ]
                }
              ]
            }
            """;

    var exception =
        assertThrows(DatabindException.class, () -> objectMapper.readValue(payload, Map.class));

    assertThat(exception).hasMessageContaining("Intrinsic function limit exceeded");
  }

  @Test
  void intrinsicFunctionLimit_Deep() {
    var payload =
        """
            {
              "result": {
                "camunda.function.type": "test_concat",
                "params": [
                  {
                    "camunda.function.type": "test_concat",
                    "params": [
                      {
                        "camunda.function.type": "test_concat",
                        "params": [
                          "Hello",
                          { "camunda.function.type": "test_concat", "params": ["Hello", " World"] }
                        ]
                      }, " World"
                    ]
                  }, " World"
                ]
              }
            }
            """;

    var exception =
        assertThrows(DatabindException.class, () -> objectMapper.readValue(payload, Map.class));

    assertThat(exception).hasMessageContaining("Intrinsic function limit exceeded");
  }
}
