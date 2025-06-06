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
package io.camunda.connector.feel.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.camunda.connector.feel.FeelEngineWrapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import scala.compiletime.ops.string;

/**
 * A Jackson deserializer for FEEL expressions. It can be used to deserialize a string that contains
 * a FEEL expression, e.g. in inbound connector properties. NB: for outbound connectors, FEEL
 * expressions in connector variables are evaluated by Zeebe, so this deserializer is not needed.
 */
public class FeelDeserializer extends AbstractFeelDeserializer<Object> {

  private final JavaType outputType;
  private static final FeelEngineWrapper FEEL_ENGINE_WRAPPER = new FeelEngineWrapper();

  public FeelDeserializer() { // needed for references in @JsonDeserialize
    this(FEEL_ENGINE_WRAPPER, TypeFactory.unknownType());
  }

  protected FeelDeserializer(FeelEngineWrapper feelEngineWrapper, JavaType outputType) {
    super(feelEngineWrapper, true);
    this.outputType = outputType;
  }

  @Override
  protected Object doDeserialize(
      JsonNode node, JsonNode feelContext, DeserializationContext jacksonCtx) throws IOException {

    if (isFeelExpression(node.textValue())) {
      return feelEngineWrapper.evaluate(jacksonCtx, node.textValue(), outputType, feelContext);
    }

    if (node.isTextual()) {
      String textValue = node.textValue();
      if (outputType.isCollectionLikeType()
          && outputType.hasContentType()
          && !textValue.trim().startsWith("[")) {
        // Support legacy list like formats like: a,b,c | 1,2,3
        return handleListLikeFormat(textValue);
      } else if (outputType.isJavaLangObject()
          && ((textValue.startsWith("\"") && textValue.endsWith("\""))
              || (textValue.startsWith("'") && textValue.endsWith("'")))) {
        return handleNormalJsonNode(node, jacksonCtx);
      } else {
        var jsonFactory = jacksonCtx.getParser().getCodec().getFactory();
        try (JsonParser jsonParser = jsonFactory.createParser(textValue)) {
          // check if this string contains a JSON object/array/etc inside (i.e. it's not just a
          // string)
          JsonNode jsonNode = jsonParser.readValueAsTree();
          if (jsonNode != null && !jsonNode.isNull()) {
            return handleNormalJsonNode(jsonNode, jacksonCtx);
          }
        } catch (IOException e) {
          // ignore, this is just a string, we will take care of it below
        }
      }
    }
    return handleNormalJsonNode(node, jacksonCtx);
  }

  protected Object handleNormalJsonNode(JsonNode node, DeserializationContext context)
      throws IOException {

    if (node == null || node.isNull()) {
      return null;
    }
    if (outputType.getRawClass() == String.class && node.isObject()) {
      return BLANK_OBJECT_MAPPER.writeValueAsString(node);
    }
    return context.readTreeAsValue(node, outputType);
  }

  protected Object handleListLikeFormat(String textValue) {
    if (outputType.getContentType().hasRawClass(Long.class)) {
      return convertStringToListOfLongs(textValue);
    } else if (outputType.getContentType().hasRawClass(Integer.class)) {
      return convertStringToListOfIntegers(textValue);
    } else if (outputType.getContentType().hasRawClass(String.class)) {
      return convertStringToListOfStrings(textValue);
    } else {
      throw new IllegalArgumentException("Unsupported output type: " + outputType);
    }
  }

  private static List<Long> convertStringToListOfLongs(String string) {
    var value = string.trim();
    if (value.isBlank()) {
      return new ArrayList<>();
    }
    return Arrays.stream(string.split(","))
        .map(s -> Long.parseLong(s.trim()))
        .collect(Collectors.toList());
  }

  private static List<Integer> convertStringToListOfIntegers(String string) {
    var value = string.trim();
    if (value.isBlank()) {
      return new ArrayList<>();
    }
    return Arrays.stream(string.split(","))
        .map(s -> Integer.parseInt(s.trim()))
        .collect(Collectors.toList());
  }

  private static List<String> convertStringToListOfStrings(String string) {
    var value = string.trim();
    if (value.isBlank()) {
      return new ArrayList<>();
    }
    return Arrays.stream(string.split(",")).map(String::trim).collect(Collectors.toList());
  }

  @Override
  public JsonDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
    return new FeelDeserializer(feelEngineWrapper, property.getType());
  }
}
