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
package io.camunda.connector.generator.dsl;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskHeader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

public record PropertyGroup(String id, String label, @JsonIgnore List<Property> properties) {

  public static PropertyGroup OUTPUT_GROUP_OUTBOUND =
      PropertyGroup.builder()
          .id("output")
          .label("Output mapping")
          .properties(
              CommonProperties.RESULT_VARIABLE
                  .binding(new ZeebeTaskHeader("resultVariable"))
                  .build(),
              CommonProperties.RESULT_EXPRESSION
                  .binding(new ZeebeTaskHeader("resultExpression"))
                  .build())
          .build();

  public static PropertyGroup OUTPUT_GROUP_INBOUND =
      PropertyGroup.builder()
          .id("output")
          .label("Output mapping")
          .properties(
              CommonProperties.RESULT_VARIABLE.binding(new ZeebeProperty("resultVariable")).build(),
              CommonProperties.RESULT_EXPRESSION
                  .binding(new ZeebeProperty("resultExpression"))
                  .build())
          .build();

  public static PropertyGroup ERROR_GROUP =
      PropertyGroup.builder()
          .id("error")
          .label("Error handling")
          .properties(
              CommonProperties.ERROR_EXPRESSION
                  .binding(new ZeebeTaskHeader("errorExpression"))
                  .build())
          .build();

  public static PropertyGroup RETRIES_GROUP =
      PropertyGroup.builder()
          .id("retries")
          .label("Retries")
          .properties(
              CommonProperties.RETRY_COUNT.binding(ZeebeTaskDefinition.RETRIES).build(),
              CommonProperties.RETRY_BACKOFF.binding(new ZeebeTaskHeader("retryBackoff")).build())
          .build();

  public static PropertyGroup ACTIVATION_GROUP_WITHOUT_MESSAGE_ID_EXPR =
      PropertyGroup.builder()
          .id("activation")
          .label("Activation")
          .properties(
              CommonProperties.CORRELATION_KEY_PROCESS
                  .binding(new ZeebeProperty("correlationKey"))
                  .build(),
              CommonProperties.CORRELATION_KEY_PAYLOAD
                  .binding(new ZeebeProperty("correlationKey"))
                  .build(),
              CommonProperties.ACTIVATION_CONDITION
                  .binding(new ZeebeProperty("activationCondition"))
                  .build())
          .build();

  public static PropertyGroup ACTIVATION_GROUP_WITH_MESSAGE_ID_EXP =
      PropertyGroup.builder()
          .id("activation")
          .label("Activation")
          .properties(
              CommonProperties.CORRELATION_KEY_PROCESS
                  .binding(new ZeebeProperty("correlationKey"))
                  .build(),
              CommonProperties.CORRELATION_KEY_PAYLOAD
                  .binding(new ZeebeProperty("correlationKey"))
                  .build(),
              CommonProperties.MESSAGE_ID_EXPRESSION
                  .binding(new ZeebeProperty("messageId"))
                  .build(),
              CommonProperties.ACTIVATION_CONDITION
                  .binding(new ZeebeProperty("activationCondition"))
                  .build())
          .build();

  public PropertyGroup {
    if (id == null) {
      throw new IllegalArgumentException("id is required");
    }
    if (label == null) {
      throw new IllegalArgumentException("label is required");
    }
    if (properties == null) {
      properties = Collections.emptyList();
    }
  }

  public static PropertyGroupBuilder builder() {
    return new PropertyGroupBuilder();
  }

  public static final class PropertyGroupBuilder {

    private String id;
    private String label;
    private final List<Property> properties = new ArrayList<>();

    private PropertyGroupBuilder() {}

    public PropertyGroupBuilder id(String id) {
      this.id = id;
      return this;
    }

    public PropertyGroupBuilder label(String label) {
      this.label = label;
      return this;
    }

    public PropertyGroupBuilder properties(PropertyBuilder... properties) {
      requireIdSet();
      this.properties.addAll(
          Stream.of(properties)
              .map(builder -> builder.group(id))
              .map(PropertyBuilder::build)
              .toList());
      return this;
    }

    public PropertyGroupBuilder properties(List<Property> properties) {
      requireIdSet();
      properties.forEach(
          property -> {
            if (!id.equals(property.group)) {
              throw new IllegalArgumentException(
                  "Property "
                      + property.id
                      + " defines a different group "
                      + property.group
                      + " than the group "
                      + id
                      + " it is added to");
            }
          });
      this.properties.addAll(properties);
      return this;
    }

    public PropertyGroupBuilder properties(Property... properties) {
      return properties(List.of(properties));
    }

    public PropertyGroup build() {
      return new PropertyGroup(id, label, properties);
    }

    private void requireIdSet() {
      if (id == null) {
        throw new IllegalStateException("id is required before properties can be set");
      }
    }
  }
}
