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

import com.fasterxml.jackson.annotation.JsonEnumDefaultValue;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

  private record TestRecordWithString(String value) {}

  @Test
  void unknownEnumValueShouldBeDeserializedUsingDefaultValue() throws JsonProcessingException {
    final var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var json = "{\"status\":\"PAUSED\"}";
    var actual = objectMapper.readValue(json, TestRecordWithEnum.class);
    assertThat(actual.status()).isEqualTo(TestStatus.UNKNOWN);
  }

  private enum TestStatus {
    ACTIVE,
    INACTIVE,
    @JsonEnumDefaultValue
    UNKNOWN
  }

  private record TestRecordWithEnum(TestStatus status) {}
}
