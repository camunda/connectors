/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.aiagent.tool.runtime;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolCall;
import io.camunda.connector.agenticai.aiagent.api.tool.RuntimeToolException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** The call of a runtime tool as the agent hands it to the tool. */
public final class RuntimeToolCallImpl implements RuntimeToolCall {

  private static final Logger LOGGER = LoggerFactory.getLogger(RuntimeToolCallImpl.class);

  private final String id;
  private final String name;
  private final Map<String, Object> arguments;
  private final ObjectMapper argumentsMapper;

  /**
   * @param argumentsMapper the mapper created by {@link #argumentsMapper(ObjectMapper)}, which
   *     binds the arguments
   */
  public RuntimeToolCallImpl(
      String id, String name, Map<String, Object> arguments, ObjectMapper argumentsMapper) {
    this.id = id;
    this.name = name;
    // a model can send an explicit null value, which an immutable Map.copyOf would reject
    this.arguments = Collections.unmodifiableMap(new LinkedHashMap<>(arguments));
    this.argumentsMapper = argumentsMapper;
  }

  /**
   * A copy of the mapper that binds the arguments of a call. It ignores arguments the type has no
   * property for, as a model sends them now and then.
   */
  public static ObjectMapper argumentsMapper(ObjectMapper objectMapper) {
    return objectMapper.copy().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  }

  @Override
  public String id() {
    return id;
  }

  @Override
  public String name() {
    return name;
  }

  @Override
  public Map<String, Object> arguments() {
    return arguments;
  }

  @Override
  public <A> A argumentsAs(Class<A> type) {
    try {
      return argumentsMapper.convertValue(arguments, type);
    } catch (IllegalArgumentException e) {
      LOGGER.debug(
          "The arguments of the call '{}' of the tool '{}' do not fit {}", id, name, type, e);
      throw new RuntimeToolException("Invalid arguments for tool '%s'.".formatted(name), e);
    }
  }
}
