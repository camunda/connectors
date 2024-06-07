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
package io.camunda.connector.http.base.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import io.camunda.connector.api.json.ConnectorsObjectMapperSupplier;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

public class JsonHelperTest {

  @Nested
  class IsJsonStringValidTests {
    @Test
    public void shouldReturnTrue_whenJsonIsValid() {
      // given
      String jsonString = "{\"key\": \"value\"}";
      // when
      boolean result = JsonHelper.isJsonStringValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnFalse_whenJsonIsInvalid() {
      // given
      String jsonString = "{\"key\": \"value\"";
      // when
      boolean result = JsonHelper.isJsonStringValid(jsonString);
      // then
      assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnFalse_whenJsonIsInvalidMissingBrackets() {
      // given
      String jsonString = "key: value";
      // when
      boolean result = JsonHelper.isJsonStringValid(jsonString);
      // then
      assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsNull() {
      // given
      String jsonString = "{\"key\": null}";
      // when
      boolean result = JsonHelper.isJsonStringValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsArrayOfObjects() {
      // given
      String jsonString = "[{\"key\": \"value\"}]";
      // when
      boolean result = JsonHelper.isJsonStringValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsArrayOfStrings() {
      // given
      String jsonString = "[\"value\"]";
      // when
      boolean result = JsonHelper.isJsonStringValid(jsonString);
      // then
      assertThat(result).isTrue();
    }
  }

  @Nested
  class IsJsonValidTests {
    @Test
    public void shouldReturnTrue_whenJsonIsValid() throws JsonProcessingException {
      // given
      String jsonString = "{\"key\": \"value\"}";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnFalse_whenJsonIsInvalid() throws JsonProcessingException {
      // given
      String jsonString = "{\"key\": \"value\"";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnFalse_whenJsonIsInvalidMissingBrackets()
        throws JsonProcessingException {
      // given
      String jsonString = "key: value";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsNull() throws JsonProcessingException {
      // given
      String jsonString = "{\"key\": null}";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsArrayOfObjects() throws JsonProcessingException {
      // given
      String jsonString = "[{\"key\": \"value\"}]";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsArrayOfStrings() throws JsonProcessingException {
      // given
      String jsonString = "[\"value\"]";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isTrue();
    }
  }

  @Nested
  class GetAsJsonElementTests {
    private final ObjectMapper objectMapper = ConnectorsObjectMapperSupplier.DEFAULT_MAPPER;

    @Test
    public void shouldReturnStringJsonNode_whenStringParameter() throws JsonProcessingException {
      // given
      String input = "hello";
      // when
      JsonNode jsonElement = JsonHelper.getAsJsonElement(input, objectMapper);
      // then
      assertThat(jsonElement).isNull();
    }

    @Test
    public void shouldReturnObjectJsonNode_whenStringObjectParameter()
        throws JsonProcessingException {
      // given
      String input = "{\"key\": \"value\"}";
      // when
      JsonNode jsonElement = JsonHelper.getAsJsonElement(input, objectMapper);
      // then
      assertThat(jsonElement).isNotNull();
      // assert Class in TextNode
      assertThat(jsonElement.getNodeType()).isEqualTo(JsonNodeType.OBJECT);
    }

    @Test
    public void shouldReturnNull_whenNullParameter() throws JsonProcessingException {
      // given
      String input = null;
      // when
      JsonNode jsonElement = JsonHelper.getAsJsonElement(input, objectMapper);
      // then
      assertThat(jsonElement).isNull();
    }

    @Test
    public void shouldReturnObjectJsonNode_whenMapParameter() throws JsonProcessingException {
      // given
      Map<String, String> input = new HashMap<>(Map.of("key", "value"));
      input.put("key2", null);
      // when
      JsonNode jsonElement = JsonHelper.getAsJsonElement(input, objectMapper);
      // then
      assertThat(jsonElement).isNotNull();
      // assert Class in ObjectNode
      assertThat(jsonElement.getNodeType()).isEqualTo(JsonNodeType.OBJECT);
      assertThat(jsonElement.get("key").asText()).isEqualTo("value");
      assertThat(jsonElement.get("key2").isNull()).isTrue();
    }
  }
}
