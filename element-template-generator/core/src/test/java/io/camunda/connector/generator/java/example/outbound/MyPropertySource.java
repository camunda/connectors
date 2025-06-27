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
package io.camunda.connector.generator.java.example.outbound;

import io.camunda.connector.generator.dsl.BooleanProperty;
import io.camunda.connector.generator.dsl.HiddenProperty;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeInput;
import io.camunda.connector.generator.dsl.PropertyBinding.ZeebeProperty;
import io.camunda.connector.generator.dsl.PropertyBuilder;
import io.camunda.connector.generator.dsl.StringProperty;
import io.camunda.connector.generator.java.annotation.PropertySource;
import java.util.List;

public class MyPropertySource {

  @PropertySource
  public static List<PropertyBuilder> getFirstProperty() {
    return List.of(
        HiddenProperty.builder()
            .id("myPropertySourceZeebeProperty")
            .binding(new ZeebeProperty("myPropertySourceZeebeProperty"))
            .value("myZeebePropertyValue"));
  }

  @PropertySource
  public static List<PropertyBuilder> getOtherProperties() {
    return List.of(
        StringProperty.builder()
            .id("myPropertySourceStringProperty")
            .group("group1")
            .label("PropertySource String Property")
            .binding(new ZeebeInput("myPropertySourceStringProperty")),
        BooleanProperty.builder()
            .id("myPropertySourceBooleanProperty")
            .group("propertySourceCustomGroup")
            .label("PropertySource Boolean Property")
            .binding(new ZeebeProperty("myPropertySourceBooleanProperty")));
  }
}
