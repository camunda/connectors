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
package io.camunda.connector.optimizer.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.dsl.PropertyBinding;
import io.camunda.connector.generator.dsl.PropertyCondition;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Loads and serializes element templates with the same Jackson config as the generator, plus
 * polymorphism-aware deserializers for the {@link Property}, {@link PropertyBinding}, and {@link
 * PropertyCondition} sealed hierarchies (which the generator never has to deserialize).
 */
public final class TemplateLoader {

  private static final ObjectMapper MAPPER = buildMapper();

  private TemplateLoader() {}

  private static ObjectMapper buildMapper() {
    SimpleModule optimizerDeserializers = new SimpleModule("optimizer-deserializers");
    optimizerDeserializers.addDeserializer(Property.class, new PropertyDeserializer());
    optimizerDeserializers.addDeserializer(
        PropertyBinding.class, new PropertyBindingDeserializer());
    optimizerDeserializers.addDeserializer(
        PropertyCondition.class, new PropertyConditionDeserializer());
    return new ObjectMapper()
        .registerModule(new ElementTemplateModule())
        .registerModule(optimizerDeserializers)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .enable(SerializationFeature.INDENT_OUTPUT);
  }

  public static ElementTemplate load(Path path) throws IOException {
    return MAPPER.readValue(Files.readAllBytes(path), ElementTemplate.class);
  }

  public static void save(ElementTemplate template, Path path) throws IOException {
    Files.write(path, MAPPER.writeValueAsBytes(template));
  }

  public static String toString(ElementTemplate template) {
    try {
      return MAPPER.writeValueAsString(template);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to serialize template", e);
    }
  }
}
