/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
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
  class IsJsonValidTests {
    @Test
    public void shouldReturnTrue_whenJsonIsValid() {
      // given
      String jsonString = "{\"key\": \"value\"}";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnFalse_whenJsonIsInvalid() {
      // given
      String jsonString = "{\"key\": \"value\"";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnFalse_whenJsonIsInvalidMissingBrackets() {
      // given
      String jsonString = "key: value";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isFalse();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsNull() {
      // given
      String jsonString = "{\"key\": null}";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsArrayOfObjects() {
      // given
      String jsonString = "[{\"key\": \"value\"}]";
      // when
      boolean result = JsonHelper.isJsonValid(jsonString);
      // then
      assertThat(result).isTrue();
    }

    @Test
    public void shouldReturnTrue_whenJsonContainsArrayOfStrings() {
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
      assertThat(jsonElement).isNotNull();
      // assert Class in TextNode
      assertThat(jsonElement.getNodeType()).isEqualTo(JsonNodeType.STRING);
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
