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
package io.camunda.connector.api.json;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import io.camunda.connector.document.annotation.jackson.DocumentReferenceModel.CamundaDocumentReferenceModel;
import io.camunda.connector.document.annotation.jackson.JacksonModuleDocumentDeserializer.DocumentModuleSettings;
import io.camunda.document.Document;
import io.camunda.document.factory.DocumentFactoryImpl;
import io.camunda.document.store.InMemoryDocumentStore;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConnectorsObjectMapperSupplierTest {

  @Test
  void java8DatesShouldBeSupported() throws JsonProcessingException {
    final var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var json = "{\"data\":\"2024-01-01\"}";
    final var jsonDeserialized = Map.of("data", LocalDate.of(2024, 1, 1));
    Assertions.assertThat(objectMapper.writeValueAsString(jsonDeserialized)).isEqualTo(json);
    var actual = objectMapper.readValue(json, new TypeReference<Map<String, LocalDate>>() {});
    Assertions.assertThat(actual).isEqualTo(jsonDeserialized);
  }

  @Test
  void singlePrimitiveValueShouldBeAcceptedAsArray() throws JsonProcessingException {
    final var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var json = "1";
    var actual = objectMapper.readValue(json, int[].class);
    Assertions.assertThat(actual).isEqualTo(new int[] {1});
  }

  @Test
  void singleDocumentShouldBeAcceptedAsArray() throws JsonProcessingException {
    final var objectMapper =
        ConnectorsObjectMapperSupplier.getCopy(
            new DocumentFactoryImpl(InMemoryDocumentStore.INSTANCE),
            DocumentModuleSettings.create());
    final var documentReference =
        new CamundaDocumentReferenceModel(
            "default", UUID.randomUUID().toString(), null, Optional.empty());
    final var json = "{\"documents\":" + objectMapper.writeValueAsString(documentReference) + "}";
    var actual = objectMapper.readValue(json, TestRecordWithDocumentList.class);
    Assertions.assertThat(actual.documents()).hasSize(1);
    Assertions.assertThat(actual.documents().get(0).reference()).isEqualTo(documentReference);
  }

  private record TestRecordWithDocumentList(List<Document> documents) {}
}
