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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module;
import io.camunda.document.factory.DocumentFactory;
import io.camunda.document.operation.DefaultOperationExecutor;
import io.camunda.document.operation.OperationExecutor;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class OperationDeserializationTest {

  private DocumentFactory factory = mock(DocumentFactory.class);
  private final OperationExecutor operationExecutor = spy(new DefaultOperationExecutor(List.of()));

  private final ObjectMapper objectMapper =
      new ObjectMapper()
          .registerModule(new JacksonModuleDocumentDeserializer(factory, operationExecutor))
          .registerModule(new JacksonModuleDocumentSerializer())
          .registerModule(new Jdk8Module());

  public record Base64InputModel(String document) {}

  @Test
  void operationInvoked() {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString, null, factory);

    final var payload =
        Map.of("document", Map.of("camunda.operation.type", "base64", "params", List.of(ref)));
    final var result = objectMapper.convertValue(payload, Base64InputModel.class);

    assertThat(result.document())
        .isEqualTo(Base64.getEncoder().encodeToString(contentString.getBytes()));
  }

  @Test
  void wrongOperationName() {
    var contentString = "Hello World";
    var ref = createDocumentMock(contentString, null, factory);

    final var payload =
        Map.of("document", Map.of("camunda.operation.type", "wrong", "params", List.of(ref)));
    final var e =
        assertThrows(
            IllegalArgumentException.class,
            () -> objectMapper.convertValue(payload, Base64InputModel.class));

    assertThat(e).hasMessageContaining("No operation found with name 'wrong'");
  }
}
