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
package io.camunda.connector.document.jackson;

import static io.camunda.connector.document.jackson.DocumentDeserializationTest.createDocumentMock;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.camunda.connector.api.document.DocumentFactory;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.document.DocumentFactoryImpl;
import io.camunda.document.store.CamundaDocumentStore;
import io.camunda.intrinsic.DefaultIntrinsicFunctionExecutor;
import io.camunda.intrinsic.IntrinsicFunctionExecutor;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class IntrinsicFunctionDeserializationTest {

  private final CamundaDocumentStore documentStore = mock(CamundaDocumentStore.class);

  private final ObjectMapper objectMapper = new ObjectMapper();

  public IntrinsicFunctionDeserializationTest() {
    /*
     * Order of initialization is important here. The operationExecutor is created first and then
     * the objectMapper is created with the operationExecutor. This is because the operationExecutor
     * needs an objectMapper configured with the same modules.
     */
    IntrinsicFunctionExecutor operationExecutor =
        spy(new DefaultIntrinsicFunctionExecutor(objectMapper));

    final var settings = DocumentModuleSettings.create();
    settings.setMaxIntrinsicFunctions(2);
    final DocumentFactory factory = new DocumentFactoryImpl(documentStore);
    objectMapper
        .registerModule(new JacksonModuleDocumentDeserializer(factory, operationExecutor, settings))
        .registerModule(new JacksonModuleDocumentSerializer())
        .registerModule(new Jdk8Module());
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
            IllegalArgumentException.class,
            () -> objectMapper.convertValue(payload, StringResultModel.class));

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
  void operationWithObjectParameter_acceptsString() throws JsonProcessingException {
    var string = "Hello World";

    final var payload =
        Map.of(
            "result",
            Map.of("camunda.function.type", "test_anythingToString", "params", List.of(string)));
    final var result = objectMapper.convertValue(payload, StringResultModel.class);

    assertThat(result.result).isEqualTo(objectMapper.writeValueAsString(string));
  }

  @Test
  void operationWithObjectParameter_nestedOperation() throws JsonProcessingException {
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
        assertThrows(JsonMappingException.class, () -> objectMapper.readValue(payload, Map.class));

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
        assertThrows(JsonMappingException.class, () -> objectMapper.readValue(payload, Map.class));

    assertThat(exception).hasMessageContaining("Intrinsic function limit exceeded");
  }
}
