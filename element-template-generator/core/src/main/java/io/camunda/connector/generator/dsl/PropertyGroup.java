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
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskDefinition;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeTaskHeader;
import io.camunda.connector.generator.dsl.PropertyCondition.Equals;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

@JsonInclude(Include.NON_NULL)
public record PropertyGroup(
    String id,
    String label,
    @JsonIgnore List<Property> properties,
    String tooltip,
    Boolean openByDefault) {

  public static PropertyGroup OUTPUT_GROUP_OUTBOUND =
      PropertyGroup.builder()
          .id("output")
          .label("Output mapping")
          .properties(
              CommonProperties.resultVariable()
                  .binding(new ZeebeTaskHeader("resultVariable"))
                  .build(),
              CommonProperties.resultExpression()
                  .binding(new ZeebeTaskHeader("resultExpression"))
                  .build())
          .build();

  public static PropertyGroup OUTPUT_GROUP_INBOUND =
      PropertyGroup.builder()
          .id("output")
          .label("Output mapping")
          .properties(
              CommonProperties.resultVariable()
                  .binding(new ZeebeProperty("resultVariable"))
                  .build(),
              CommonProperties.resultExpression()
                  .binding(new ZeebeProperty("resultExpression"))
                  .build())
          .build();

  public static PropertyGroup ERROR_GROUP =
      PropertyGroup.builder()
          .id("error")
          .label("Error handling")
          .properties(
              CommonProperties.errorExpression()
                  .binding(new ZeebeTaskHeader("errorExpression"))
                  .build())
          .build();

  public static PropertyGroup RETRIES_GROUP =
      PropertyGroup.builder()
          .id("retries")
          .label("Retries")
          .properties(
              CommonProperties.retryCount().binding(ZeebeTaskDefinition.RETRIES).build(),
              CommonProperties.retryBackoff().binding(new ZeebeTaskHeader("retryBackoff")).build())
          .build();

  public static PropertyGroup ACTIVATION_GROUP =
      PropertyGroup.builder()
          .id("activation")
          .label("Activation")
          .properties(
              CommonProperties.activationCondition()
                  .binding(new ZeebeProperty("activationCondition"))
                  .build())
          .build();

  public static PropertyGroup CORRELATION_GROUP_MESSAGE_START_EVENT =
      PropertyGroup.builder()
          .id("correlation")
          .label("Correlation")
          .properties(
              CommonProperties.correlationRequiredDropdown().build(),
              CommonProperties.correlationKeyProcess()
                  .condition(
                      new Equals(CommonProperties.correlationRequiredDropdown().id, "required"))
                  .build(),
              CommonProperties.correlationKeyPayload()
                  .condition(
                      new Equals(CommonProperties.correlationRequiredDropdown().id, "required"))
                  .build(),
              CommonProperties.messageIdExpression().build(),
              CommonProperties.messageNameUuidHidden().build())
          .build();

  public static PropertyGroup CORRELATION_GROUP_INTERMEDIATE_CATCH_EVENT_OR_BOUNDARY =
      PropertyGroup.builder()
          .id("correlation")
          .label("Correlation")
          .properties(
              CommonProperties.correlationKeyProcess().build(),
              CommonProperties.correlationKeyPayload().build(),
              CommonProperties.messageIdExpression().build(),
              CommonProperties.messageNameUuidHidden().build())
          .build();

  public static PropertyGroup DEDUPLICATION_GROUP =
      PropertyGroup.builder()
          .id("deduplication")
          .label("Deduplication")
          .tooltip(
              "Deduplication allows you to configure multiple inbound connector elements to reuse the same backend (consumer/thread/endpoint) by sharing the same deduplication ID.")
          .properties(
              CommonProperties.deduplicationModeManualFlag().build(),
              CommonProperties.deduplicationId().build(),
              CommonProperties.deduplicationModeManual().build(),
              CommonProperties.deduplicationModeAuto().build())
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
    private String tooltip;
    private Boolean openByDefault;
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

    public PropertyGroupBuilder tooltip(String tooltip) {
      this.tooltip = tooltip;
      return this;
    }

    public PropertyGroupBuilder openByDefault(Boolean openByDefault) {
      this.openByDefault = openByDefault;
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
      return new PropertyGroup(id, label, properties, tooltip, openByDefault);
    }

    private void requireIdSet() {
      if (id == null) {
        throw new IllegalStateException("id is required before properties can be set");
      }
    }
  }
}
