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
package io.camunda.connector.jackson;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.camunda.connector.api.document.Document;
import io.camunda.connector.document.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.document.DocumentFactoryImpl;
import io.camunda.document.store.InMemoryDocumentStore;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConnectorsObjectMapperSupplierTest {

  @Test
  void java8DatesShouldBeSupported() throws JsonProcessingException {
    final var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var json = "{\"data\":\"2024-01-01\"}";
    final var jsonDeserialized = Map.of("data", LocalDate.of(2024, 1, 1));
    assertThat(objectMapper.writeValueAsString(jsonDeserialized)).isEqualTo(json);
    var actual = objectMapper.readValue(json, new TypeReference<Map<String, LocalDate>>() {});
    assertThat(actual).isEqualTo(jsonDeserialized);
  }

  @Test
  void singlePrimitiveValueShouldBeAcceptedAsArray() throws JsonProcessingException {
    final var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var json = "1";
    var actual = objectMapper.readValue(json, int[].class);
    assertThat(actual).isEqualTo(new int[] {1});
  }

  @Test
  void singleDocumentShouldBeAcceptedAsArray() throws JsonProcessingException {
    final var objectMapper =
        ConnectorsObjectMapperSupplier.getCopy(
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
            DocumentModuleSettings.create());
    final var documentReference =
        new CamundaDocumentReferenceModel("default", UUID.randomUUID().toString(), "hash", null);
    final var json = "{\"documents\":" + objectMapper.writeValueAsString(documentReference) + "}";
    var actual = objectMapper.readValue(json, TestRecordWithDocumentList.class);
    assertThat(actual.documents()).hasSize(1);
    assertThat(actual.documents().getFirst().reference()).isEqualTo(documentReference);
  }

  @Test
  void singleElementDocumentArrayShouldBeAcceptedAsObject() throws JsonProcessingException {
    final var objectMapper =
        ConnectorsObjectMapperSupplier.getCopy(
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
            DocumentModuleSettings.create());
    final var documentReference =
        new CamundaDocumentReferenceModel("default", UUID.randomUUID().toString(), "hash", null);
    final var json =
        "{\"document\":" + objectMapper.writeValueAsString(List.of(documentReference)) + "}";
    var actual = objectMapper.readValue(json, TestRecordWithDocument.class);
    assertThat(actual.document()).isNotNull();
    assertThat(actual.document().reference()).isEqualTo(documentReference);
  }

  @Test
  void multipleElementDocumentArrayShouldNotBeAcceptedAsObject() throws JsonProcessingException {
    final var objectMapper =
        ConnectorsObjectMapperSupplier.getCopy(
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
            DocumentModuleSettings.create());
    final var documentReference =
        new CamundaDocumentReferenceModel("default", UUID.randomUUID().toString(), "hash", null);
    final var json =
        "{\"document\":"
            + objectMapper.writeValueAsString(List.of(documentReference, documentReference))
            + "}";
    Assertions.assertThatThrownBy(() -> objectMapper.readValue(json, TestRecordWithDocument.class))
        .isInstanceOf(JsonMappingException.class)
        .hasCauseInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void singleElementStringArrayBindingToObject() throws JsonProcessingException {
    final var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var json = "{\"value\":" + objectMapper.writeValueAsString(List.of("hey")) + "}";
    var actual = objectMapper.readValue(json, TestRecordWithString.class);
    assertThat(actual.value).isEqualTo("hey");
  }

  @Test
  void multipleElementStringArrayShouldNotBindToObject() throws JsonProcessingException {
    final var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var json = "{\"value\":" + objectMapper.writeValueAsString(List.of("hey", "yo")) + "}";
    Assertions.assertThatThrownBy(() -> objectMapper.readValue(json, TestRecordWithString.class))
        .isInstanceOf(MismatchedInputException.class);
  }

  @Test
  void intrinsicFunctionShouldBeDeserialized() throws JsonProcessingException {
    final var objectMapper =
        ConnectorsObjectMapperSupplier.getCopy(
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
            DocumentModuleSettings.create());

    final var json =
        """
        {
          "value": {
            "camunda.function.type": "base64",
            "params": ["hello"]
          }
        }
        """;

    var actual = objectMapper.readValue(json, TestRecordWithString.class);
    assertThat(actual.value()).isEqualTo(Base64.getEncoder().encodeToString("hello".getBytes()));
  }

  private record TestRecordWithDocumentList(List<Document> documents) {}

  private record TestRecordWithDocument(Document document) {}

  private record TestRecordWithString(String value) {}
}
