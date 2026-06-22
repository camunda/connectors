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
 * A credential chooser property. The Camunda Modeler renders this as a picker filtered to
 * credential instances compatible with {@link #schemaRef} / {@link #version}. On selection, the
 * Modeler writes the whole-credential FEEL expression to the single bound {@code zeebe:input}.
 */
public final class CredentialProperty extends Property {

  public static final String TYPE = "Credential";

  private final String schemaRef;
  private final long version;

  public CredentialProperty(
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
      String schemaRef,
      long version) {
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
        TYPE);
    this.schemaRef = schemaRef;
    this.version = version;
  }

  public String getSchemaRef() {
    return schemaRef;
  }

  public long getVersion() {
    return version;
  }

  public static CredentialPropertyBuilder builder() {
    return new CredentialPropertyBuilder();
  }

  public static class CredentialPropertyBuilder extends PropertyBuilder {

    private String schemaRef;
    private long version = 1;

    private CredentialPropertyBuilder() {}

    public CredentialPropertyBuilder schemaRef(String schemaRef) {
      this.schemaRef = schemaRef;
      return this;
    }

    public CredentialPropertyBuilder version(long version) {
      this.version = version;
      return this;
    }

    @Override
    public CredentialProperty build() {
      if (value != null && !(value instanceof String)) {
        throw new IllegalStateException("Value of a credential property must be a string");
      }
      return new CredentialProperty(
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
          schemaRef,
          version);
    }
  }
}
