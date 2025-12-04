/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 *       under one or more contributor license agreements. Licensed under a proprietary license.
 *       See the License.txt file for more information. You may not use this file
 *       except in compliance with the proprietary license.
 */
package io.camunda.connector.gemini.mapper;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.vertexai.generativeai.PartMaker;
import java.util.*;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PromptsMapper {

  private static final Logger LOGGER = LoggerFactory.getLogger(PromptsMapper.class);
  private static final TypeReference<LinkedHashMap<String, String>> typeRef =
      new TypeReference<>() {};

  public static final String INVALID_PROMPT_MSG_FORMAT = "Invalid prompt format: %s";
  public static final String EMPTY_PROMPT_MSG = "Prompt can not be empty";

  public static final String MIME_KEY = "mime";
  public static final String URI_KEY = "uri";
  public static final String TEXT_KEY = "text";

  private final ObjectMapper objectMapper;

  public PromptsMapper(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  public Object[] map(List<Object> prompts) {
    return prompts.stream()
        .map(
            prompt ->
                Optional.ofNullable(prompt)
                    .map(this::convertToStringsMap)
                    .map(this::mapToMediaOrText)
                    .orElseThrow(() -> createInvalidPromptException(EMPTY_PROMPT_MSG)))
        .toArray();
  }

  private LinkedHashMap<String, String> convertToStringsMap(Object o) {
    try {
      return objectMapper.convertValue(o, typeRef);
    } catch (RuntimeException e) {
      throw createInvalidPromptException(INVALID_PROMPT_MSG_FORMAT.formatted(o), e);
    }
  }

  private Object mapToMediaOrText(Map<String, String> map) {
    switch (map.size()) {
      case 1 -> {
        validateEntry(map, TEXT_KEY);
        return map.get(TEXT_KEY);
      }
      case 2 -> {
        validateEntry(map, MIME_KEY, URI_KEY);
        return PartMaker.fromMimeTypeAndData(map.get(MIME_KEY), map.get(URI_KEY));
      }
      default -> throw createInvalidPromptException(String.format(INVALID_PROMPT_MSG_FORMAT, map));
    }
  }

  private void validateEntry(Map<String, String> map, String... keys) {
    for (String key : keys) {
      if (!map.containsKey(key) || StringUtils.isBlank(map.get(key))) {
        throw createInvalidPromptException(String.format(INVALID_PROMPT_MSG_FORMAT, map));
      }
    }
  }

  private RuntimeException createInvalidPromptException(String message) {
    return createInvalidPromptException(message, null);
  }

  private RuntimeException createInvalidPromptException(String message, Exception e) {
    LOGGER.debug(message);
    return new IllegalArgumentException(message, e);
  }
}
