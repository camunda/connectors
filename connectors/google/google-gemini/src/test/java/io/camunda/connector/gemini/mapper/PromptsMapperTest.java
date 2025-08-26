/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.mapper;

import static io.camunda.connector.gemini.TestUtil.readValue;
import static io.camunda.connector.gemini.mapper.PromptsMapper.*;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.cloud.vertexai.generativeai.PartMaker;
import io.camunda.connector.jackson.ConnectorsObjectMapperSupplier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PromptsMapperTest {

  private final PromptsMapper promptsMapper =
      new PromptsMapper(ConnectorsObjectMapperSupplier.getCopy());

  @Test
  void mapWithValidPrompts() throws Exception {
    List<Object> input = readValue("src/test/resources/prompts.json", List.class);

    Object[] result = promptsMapper.map(input);

    Object[] expected = {
      "tell me abut this band",
      PartMaker.fromMimeTypeAndData("video/*", "https://youtu.be/Snhb-97lMcQ?si=_cFMlEcGldkQ6h63")
    };

    assertThat(result).isEqualTo(expected);
  }

  @Test
  void mapWithOneEmptyEntryShouldThrowEx() throws Exception {
    List<Object> input = readValue("src/test/resources/prompts_with_empty_entry.json", List.class);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> promptsMapper.map(input));
    assertThat(ex).hasMessage(INVALID_PROMPT_MSG_FORMAT.formatted(Map.of()));
  }

  @Test
  void mapNullEntryShouldThrowEx() throws Exception {
    List<Object> input = readValue("src/test/resources/prompts_with_null_entry.json", List.class);

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> promptsMapper.map(input));
    assertThat(ex).hasMessage(EMPTY_PROMPT_MSG);
  }

  @ParameterizedTest
  @ValueSource(strings = {MIME_KEY, URI_KEY, TEXT_KEY})
  void mapWithPossibleKeyWithEmptyValueShouldThrowEx(String key) {
    Map<String, String> map = Map.of(key, " ");

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> promptsMapper.map(List.of(map)));
    assertThat(ex).hasMessage(String.format(INVALID_PROMPT_MSG_FORMAT, map));
  }

  @Test
  void mapWithImpossibleKeyShouldThrowEx() {
    Map<String, String> map = Map.of("texttt", "tell me");

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> promptsMapper.map(List.of(map)));
    assertThat(ex).hasMessage(String.format(INVALID_PROMPT_MSG_FORMAT, map));
  }

  @Test
  void mapWithThreeKeysInOneEntryShouldThrowEx() {
    Map<String, String> map =
        Map.of(
            MIME_KEY, "video/*",
            URI_KEY, "http/",
            TEXT_KEY, "tell me");

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> promptsMapper.map(List.of(map)));
    assertThat(ex).hasMessage(String.format(INVALID_PROMPT_MSG_FORMAT, map));
  }

  @Test
  void mapWithTwoKeysEntryShouldConsistOfMimeAndUri() {
    Map<String, String> map =
        Map.of(
            MIME_KEY, "video/*",
            TEXT_KEY, "tell me");

    IllegalArgumentException ex =
        assertThrows(IllegalArgumentException.class, () -> promptsMapper.map(List.of(map)));
    assertThat(ex).hasMessage(String.format(INVALID_PROMPT_MSG_FORMAT, map));
  }
}
