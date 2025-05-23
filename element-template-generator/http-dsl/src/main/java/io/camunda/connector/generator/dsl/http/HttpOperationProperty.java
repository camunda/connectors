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
package io.camunda.connector.generator.dsl.http;

import java.util.List;
import java.util.Objects;

public record HttpOperationProperty(
    String id,
    Target target,
    String description,
    boolean required,
    Type type,
    List<String> choices,
    String example) {

  public static HttpOperationProperty createStringProperty(
      String id, Target target, String description, boolean required, String example) {
    return new HttpOperationProperty(id, target, description, required, Type.STRING, null, example);
  }

  public static HttpOperationProperty createHiddenProperty(
      String id, Target target, String description, boolean required, String example) {
    return new HttpOperationProperty(id, target, description, required, Type.HIDDEN, null, example);
  }

  public static HttpOperationProperty createEnumProperty(
      String id, Target target, String description, boolean required, List<?> choices) {

    List<String> strChoices =
        choices.stream().filter(Objects::nonNull).map(String::valueOf).toList();

    return new HttpOperationProperty(
        id, target, description, required, Type.ENUM, strChoices, null);
  }

  public static HttpOperationProperty createFeelProperty(
      String id, Target target, String description, boolean required, String example) {
    return new HttpOperationProperty(id, target, description, required, Type.FEEL, null, example);
  }

  public enum Target {
    PATH,
    QUERY,
    HEADER,
    BODY
  }

  public enum Type {
    STRING,
    ENUM,
    FEEL,
    HIDDEN
  }
}
