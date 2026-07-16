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

import io.camunda.connector.generator.java.annotation.FeelMode;

/**
 * A configuration chooser property. The Camunda Modeler renders this as a picker filtered to
 * configuration instances compatible with the given {@link #configurationTemplate}. On selection,
 * the Modeler writes the whole-configuration FEEL expression ({@code =camunda.vars.env.<name>}) to
 * the single bound {@code zeebe:input} (outbound) / {@code zeebe:property} (inbound); the connector
 * reads that object as a whole.
 */
public final class ConfigurationProperty extends Property {

  public static final String TYPE = "Configuration";

  private final String configurationTemplate;

  public ConfigurationProperty(
      String name,
      String label,
      String description,
      Boolean required,
      String value,
      GeneratedValue generatedValue,
      PropertyConstraints constraints,
      FeelMode feel,
      String group,
      PropertyBinding binding,
      PropertyCondition condition,
      String tooltip,
      String configurationTemplate) {
    super(
        name,
        label,
        description,
        required,
        value,
        generatedValue,
        constraints,
        feel,
        group,
        binding,
        condition,
        tooltip,
        null,
        null,
        null,
        TYPE,
        null);
    this.configurationTemplate = configurationTemplate;
  }

  public String getConfigurationTemplate() {
    return configurationTemplate;
  }

  public static ConfigurationPropertyBuilder builder() {
    return new ConfigurationPropertyBuilder();
  }

  public static class ConfigurationPropertyBuilder extends PropertyBuilder {

    private String configurationTemplate;

    private ConfigurationPropertyBuilder() {}

    public ConfigurationPropertyBuilder configurationTemplate(String configurationTemplate) {
      this.configurationTemplate = configurationTemplate;
      return this;
    }

    @Override
    public ConfigurationProperty build() {
      if (value != null && !(value instanceof String)) {
        throw new IllegalStateException("Value of a configuration property must be a string");
      }
      return new ConfigurationProperty(
          id,
          label,
          description,
          optional,
          (String) value,
          generatedValue,
          constraints,
          feel,
          group,
          binding,
          condition,
          tooltip,
          configurationTemplate);
    }
  }
}
