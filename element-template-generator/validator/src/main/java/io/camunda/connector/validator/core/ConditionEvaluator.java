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
package io.camunda.connector.validator.core;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;

/**
 * Evaluates an element-template property {@code condition} against an assignment of property
 * values.
 */
public final class ConditionEvaluator {

  private ConditionEvaluator() {}

  public static boolean evaluate(JsonNode condition, Map<String, String> assignment) {
    if (condition == null || condition.isMissingNode() || condition.isNull()) {
      return true;
    }
    JsonNode allMatch = condition.path(ElementTemplate.ALL_MATCH);
    if (allMatch.isArray()) {
      for (JsonNode sub : allMatch) {
        if (!evaluate(sub, assignment)) {
          return false;
        }
      }
      return true;
    }
    JsonNode propertyRef = condition.path(ElementTemplate.PROPERTY);
    if (!propertyRef.isTextual()) {
      return false;
    }
    String value = assignment.get(propertyRef.asText());
    // isEmpty is evaluated before the missing-value shortcut below: an unassigned property is
    // precisely the case `isEmpty: true` is meant to hold for, so it cannot be short-circuited to
    // false the way a value-comparing operator can.
    JsonNode isEmpty = condition.path(ElementTemplate.IS_EMPTY);
    if (isEmpty.isBoolean()) {
      boolean empty = value == null || value.isBlank();
      return isEmpty.asBoolean() == empty;
    }
    if (value == null) {
      return false;
    }
    JsonNode equals = condition.path(ElementTemplate.EQUALS);
    if (equals.isTextual()) {
      return value.equals(equals.asText());
    }
    JsonNode oneOf = condition.path(ElementTemplate.ONE_OF);
    if (oneOf.isArray()) {
      for (JsonNode v : oneOf) {
        if (v.isTextual() && value.equals(v.asText())) {
          return true;
        }
      }
      return false;
    }
    return false;
  }
}
