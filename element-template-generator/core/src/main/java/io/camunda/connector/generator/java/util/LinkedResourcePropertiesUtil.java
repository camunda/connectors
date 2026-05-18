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
package io.camunda.connector.generator.java.util;

import io.camunda.connector.generator.dsl.DropdownProperty;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.FeelMode;
import io.camunda.connector.generator.java.annotation.TemplateLinkedResource;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class LinkedResourcePropertiesUtil {

  static final String SUFFIX_INCLUDE = ".include";
  static final String SUFFIX_BINDING_TYPE = ".bindingType";
  static final String SUFFIX_RESOURCE_ID = ".resourceId";
  static final String SUFFIX_VERSION_TAG = ".versionTag";

  static final String PROPERTY_RESOURCE_TYPE = "resourceType";
  static final String PROPERTY_BINDING_TYPE = "bindingType";
  static final String PROPERTY_RESOURCE_ID = "resourceId";
  static final String PROPERTY_VERSION_TAG = "versionTag";

  private static final List<DropdownProperty.DropdownChoice> BINDING_TYPE_CHOICES =
      List.of(
          new DropdownProperty.DropdownChoice("Latest", "latest"),
          new DropdownProperty.DropdownChoice("Deployment", "deployment"),
          new DropdownProperty.DropdownChoice("Version tag", "versionTag"));

  /** Entry point for class-based ({@code OutboundConnectorFunction}) connectors. */
  public static List<PropertyBuilder> buildClassBasedLinkedResourceProperties(Class<?> type) {
    TemplateLinkedResource[] annotations = type.getAnnotationsByType(TemplateLinkedResource.class);
    if (annotations.length == 0) {
      return List.of();
    }
    // defaultGroup is null: class-based connectors have no shared operation group to fall back to,
    // unlike the operation-based path which defaults blank groups to OPERATION_GROUP_ID.
    return buildLinkedResourcePropertiesCore(annotations, "", null, null);
  }

  static List<PropertyBuilder> buildLinkedResourcePropertiesCore(
      TemplateLinkedResource[] annotations,
      String idPrefix,
      PropertyCondition.Equals baseCondition,
      String defaultGroup) {

    Set<String> seenLinkNames = new HashSet<>();
    for (TemplateLinkedResource linkedResource : annotations) {
      if (linkedResource.linkName().isBlank()) {
        throw new IllegalArgumentException(
            "@TemplateLinkedResource has a blank linkName. Please provide a non-blank linkName.");
      }
      if (linkedResource.resourceType().isBlank()) {
        throw new IllegalArgumentException(
            "@TemplateLinkedResource(linkName='"
                + linkedResource.linkName()
                + "') has a blank resourceType. Please provide a non-blank resourceType.");
      }
      if (!seenLinkNames.add(linkedResource.linkName())) {
        throw new IllegalArgumentException(
            "Duplicate @TemplateLinkedResource linkName '"
                + linkedResource.linkName()
                + "'. Each linked resource on the same class must have a unique linkName.");
      }
    }

    List<PropertyBuilder> result = new ArrayList<>();
    for (TemplateLinkedResource linkedResource : annotations) {
      String group = linkedResource.group().isBlank() ? defaultGroup : linkedResource.group();
      String bindingTypeId = idPrefix + linkedResource.linkName() + SUFFIX_BINDING_TYPE;

      // When optional=true, prepend a Yes/No toggle (zeebe:taskHeader). All linked-resource
      // properties are then conditioned on the toggle so no linkedResource block is written when
      // the user leaves it at the default "No", avoiding a Zeebe deploy-time validation error.
      PropertyCondition propertyCondition;
      if (linkedResource.optional()) {
        String toggleId = idPrefix + linkedResource.linkName() + SUFFIX_INCLUDE;
        String toggleLabel =
            linkedResource.toggleLabel().isBlank()
                ? "Include " + linkedResource.linkName() + "?"
                : linkedResource.toggleLabel();
        result.add(
            DropdownProperty.builder()
                .choices(
                    List.of(
                        new DropdownProperty.DropdownChoice("No", "false"),
                        new DropdownProperty.DropdownChoice("Yes", "true")))
                .id(toggleId)
                .label(toggleLabel)
                .value("false")
                .group(group)
                .binding(
                    new PropertyBinding.ZeebeTaskHeader(
                        idPrefix + linkedResource.linkName() + SUFFIX_INCLUDE))
                .condition(
                    baseCondition == null
                        ? null
                        : new PropertyCondition.AllMatch(List.of(baseCondition))));

        PropertyCondition.Equals toggleEquals = new PropertyCondition.Equals(toggleId, "true");
        propertyCondition =
            baseCondition == null
                ? new PropertyCondition.AllMatch(List.of(toggleEquals))
                : new PropertyCondition.AllMatch(List.of(baseCondition, toggleEquals));
      } else {
        // baseCondition == null for class-based (no operation scope); non-null for operation-based.
        propertyCondition =
            baseCondition == null ? null : new PropertyCondition.AllMatch(List.of(baseCondition));
      }

      result.add(
          HiddenProperty.builder()
              .value(linkedResource.resourceType())
              .binding(
                  new PropertyBinding.ZeebeLinkedResource(
                      linkedResource.linkName(), PROPERTY_RESOURCE_TYPE))
              .group(group)
              .condition(propertyCondition));

      result.add(
          DropdownProperty.builder()
              .choices(BINDING_TYPE_CHOICES)
              .id(bindingTypeId)
              .label(
                  linkedResource.bindingTypeLabel().isBlank()
                      ? "Resource binding"
                      : linkedResource.bindingTypeLabel())
              .value("latest")
              .group(group)
              .binding(
                  new PropertyBinding.ZeebeLinkedResource(
                      linkedResource.linkName(), PROPERTY_BINDING_TYPE))
              .condition(propertyCondition));

      result.add(
          StringProperty.builder()
              .id(idPrefix + linkedResource.linkName() + SUFFIX_RESOURCE_ID)
              .label(
                  linkedResource.resourceIdLabel().isBlank()
                      ? "Resource ID"
                      : linkedResource.resourceIdLabel())
              .description(
                  linkedResource.resourceIdDescription().isBlank()
                      ? null
                      : linkedResource.resourceIdDescription())
              .group(group)
              .binding(
                  new PropertyBinding.ZeebeLinkedResource(
                      linkedResource.linkName(), PROPERTY_RESOURCE_ID))
              .condition(propertyCondition));

      PropertyCondition.Equals versionTagEquals =
          new PropertyCondition.Equals(bindingTypeId, "versionTag");
      // versionTag condition = propertyCondition + versionTagEquals.
      // When propertyCondition is null (non-optional class-based), just use versionTagEquals alone.
      // Otherwise extend the existing AllMatch with versionTagEquals.
      PropertyCondition versionTagCondition;
      if (propertyCondition == null) {
        versionTagCondition = versionTagEquals;
      } else {
        List<PropertyCondition> versionTagConditions =
            new ArrayList<>(((PropertyCondition.AllMatch) propertyCondition).allMatch());
        versionTagConditions.add(versionTagEquals);
        versionTagCondition = new PropertyCondition.AllMatch(versionTagConditions);
      }

      result.add(
          StringProperty.builder()
              .id(idPrefix + linkedResource.linkName() + SUFFIX_VERSION_TAG)
              .label("Version tag")
              .feel(FeelMode.disabled)
              .group(group)
              .binding(
                  new PropertyBinding.ZeebeLinkedResource(
                      linkedResource.linkName(), PROPERTY_VERSION_TAG))
              .condition(versionTagCondition));
    }
    return result;
  }
}
