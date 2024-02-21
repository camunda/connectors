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
package io.camunda.connector.runtime.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class ConnectorHelperTest {

  @Test
  void feelEngineWrapperTest() throws JsonProcessingException {
    final var jsonDeserialized = Map.of("data", LocalDate.of(2024, 1, 1));
    Assertions.assertThat(
            ConnectorHelper.FEEL_ENGINE_WRAPPER.evaluateToJson("{res: data}", jsonDeserialized))
        .isEqualTo("{\"res\":\"2024-01-01\"}");

    final var jsonDeserialized2 =
        Map.of(
            "data",
            List.of(
                Map.of("date", LocalDate.of(2024, 1, 1), "attr", "value1"),
                Map.of("date", LocalDate.of(2024, 2, 1), "attr", "value2")));
    final var actual =
        ConnectorHelper.OBJECT_MAPPER.readValue(
            ConnectorHelper.FEEL_ENGINE_WRAPPER.evaluateToJson(
                """
				{
					res1: data[item.attr = "value1"][1].date,
				                res2: "hallo" + res1,
				                res3: 1 + 2,
					res4: data[item.date = "2024-02-01"][1].attr,
					res5: data[date(item.date) = date("2024-02-01")][1].attr,
					res6: today()
				}
				""",
                jsonDeserialized2),
            new TypeReference<Map<String, Object>>() {});
    Assertions.assertThat(actual)
        .contains(
            Map.entry("res1", "2024-01-01"),
            Map.entry("res2", "hallo2024-01-01"),
            Map.entry("res3", 3),
            Map.entry("res4", "value2"),
            Map.entry("res5", "value2"),
            Map.entry("res6", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)));
  }
}
