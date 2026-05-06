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
package io.camunda.connector.feel.function;

import java.util.List;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValDayTimeDuration;
import org.camunda.feel.syntaxtree.ValNumber;
import org.camunda.feel.syntaxtree.ValString;

/** Shared type-safe argument helpers for Connector FEEL functions. */
class FunctionHelper {

  private FunctionHelper() {}

  static String toString(List<Val> args, int index, String functionName, String paramName) {
    Val value = args.get(index);
    if (value instanceof ValString string) {
      return string.value();
    }
    throw new IllegalArgumentException(
        String.format("Parameter '%s' of function '%s' must be a String", paramName, functionName));
  }

  static ValContext toContext(List<Val> args, int index, String functionName, String paramName) {
    Val value = args.get(index);
    if (value instanceof ValContext map) {
      return map;
    }
    throw new IllegalArgumentException(
        String.format(
            "Parameter '%s' of function '%s' must be a Context", paramName, functionName));
  }

  static ValNumber toNumber(List<Val> args, int index, String functionName, String paramName) {
    Val value = args.get(index);
    if (value instanceof ValNumber number) {
      return number;
    }
    throw new IllegalArgumentException(
        String.format("Parameter '%s' of function '%s' must be a Number", paramName, functionName));
  }

  static ValDayTimeDuration toDuration(
      List<Val> args, int index, String functionName, String paramName) {
    Val value = args.get(index);
    if (value instanceof ValDayTimeDuration duration) {
      return duration;
    }
    throw new IllegalArgumentException(
        String.format(
            "Parameter '%s' of function '%s' must be a day-time duration (e.g. duration(\"PT1S\"))",
            paramName, functionName));
  }
}
