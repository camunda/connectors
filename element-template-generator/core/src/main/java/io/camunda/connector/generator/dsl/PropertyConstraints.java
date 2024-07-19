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

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;

@JsonInclude(Include.NON_NULL)
public record PropertyConstraints(
    Boolean notEmpty, Integer minLength, Integer maxLength, Pattern pattern) {

  public static PropertyConstraintsBuilder builder(PropertyConstraints propertyConstraints) {
    if (propertyConstraints == null) {
      return new PropertyConstraintsBuilder();
    }
    return new PropertyConstraintsBuilder()
        .notEmpty(propertyConstraints.notEmpty())
        .minLength(propertyConstraints.minLength())
        .maxLength(propertyConstraints.maxLength())
        .pattern(propertyConstraints.pattern());
  }

  public static PropertyConstraintsBuilder builder() {
    return new PropertyConstraintsBuilder();
  }

  @JsonInclude(Include.NON_NULL)
  public record Pattern(String value, String message) {}

  public static class PropertyConstraintsBuilder {
    private Boolean notEmpty;
    private Integer minLength;
    private Integer maxLength;
    private Pattern pattern;

    private PropertyConstraintsBuilder() {}

    public static PropertyConstraintsBuilder create() {
      return new PropertyConstraintsBuilder();
    }

    public PropertyConstraintsBuilder notEmpty(Boolean notEmpty) {
      this.notEmpty = notEmpty;
      return this;
    }

    public PropertyConstraintsBuilder minLength(Integer minLength) {
      this.minLength = minLength;
      return this;
    }

    public PropertyConstraintsBuilder maxLength(Integer maxLength) {
      this.maxLength = maxLength;
      return this;
    }

    public PropertyConstraintsBuilder pattern(Pattern pattern) {
      this.pattern = pattern;
      return this;
    }

    public PropertyConstraints build() {
      if (notEmpty == null && pattern == null && maxLength == null && minLength == null) {
        return null;
      }
      return new PropertyConstraints(notEmpty, minLength, maxLength, pattern);
    }
  }
}
