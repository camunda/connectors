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
import java.time.LocalDate;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConnectorsObjectMapperSupplierTest {

  @Test
  void objectMapperConfigTest() throws JsonProcessingException {
    final var objectMapper = ConnectorsObjectMapperSupplier.getCopy();
    final var json = "{\"data\":\"2024-01-01\"}";
    final var jsonDeserialized = Map.of("data", LocalDate.of(2024, 1, 1));
    Assertions.assertThat(objectMapper.writeValueAsString(jsonDeserialized)).isEqualTo(json);
    var actual = objectMapper.readValue(json, new TypeReference<Map<String, LocalDate>>() {});
    Assertions.assertThat(actual).isEqualTo(jsonDeserialized);
  }
}
