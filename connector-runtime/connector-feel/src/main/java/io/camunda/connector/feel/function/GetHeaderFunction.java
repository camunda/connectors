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
import java.util.Map;
import org.camunda.feel.context.JavaFunction;
import org.camunda.feel.syntaxtree.Val;
import org.camunda.feel.syntaxtree.ValContext;
import org.camunda.feel.syntaxtree.ValNull$;
import scala.jdk.javaapi.CollectionConverters;

/**
 * Looks up an HTTP header by name, ignoring case (RFC 9110 header field names are
 * case-insensitive), e.g. {@code getHeader(response.headers, "content-type")}.
 *
 * <p>An exact match wins; otherwise the first key equal to {@code name} ignoring case is used.
 * Returns {@code null} if the header is absent or {@code headers} is {@code null}.
 */
public class GetHeaderFunction {

  public static final String NAME = "getHeader";

  private static final List<String> ARGUMENTS = List.of("headers", "name");

  private static final JavaFunction FUNCTION =
      new JavaFunction(ARGUMENTS, GetHeaderFunction::getHeader);

  public static final List<JavaFunction> FUNCTIONS = List.of(FUNCTION);

  private static Val getHeader(List<Val> args) {
    if (args.getFirst() == ValNull$.MODULE$) {
      return ValNull$.MODULE$;
    }
    ValContext headers = FunctionHelper.toContext(args, 0, NAME, ARGUMENTS.get(0));
    String name = FunctionHelper.toString(args, 1, NAME, ARGUMENTS.get(1));

    Map<String, Object> entries =
        CollectionConverters.asJava(headers.context().variableProvider().getVariables());
    if (entries.containsKey(name)) {
      return toVal(entries.get(name));
    }
    return entries.entrySet().stream()
        .filter(entry -> entry.getKey().equalsIgnoreCase(name))
        .findFirst()
        .map(entry -> toVal(entry.getValue()))
        .orElse(ValNull$.MODULE$);
  }

  private static Val toVal(Object value) {
    if (value instanceof Val val) {
      return val;
    }
    throw new IllegalStateException(
        String.format("Unexpected header value type in function '%s': %s", NAME, value));
  }
}
