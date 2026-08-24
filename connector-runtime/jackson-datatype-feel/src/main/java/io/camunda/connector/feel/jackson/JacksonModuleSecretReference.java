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
package io.camunda.connector.feel.jackson;

import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.databind.BeanDescription;
import com.fasterxml.jackson.databind.DeserializationConfig;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.deser.BeanDeserializerModifier;
import com.fasterxml.jackson.databind.module.SimpleModule;

/**
 * Registers {@link SecretReferenceDeserializer} for {@code String}, so that a {@code
 * camunda.secrets.<name>} reference resolves wherever a connector property can hold one — including
 * the string values of {@code Map} and {@code List} properties, and values reached through a field
 * declared as {@code Object}.
 *
 * <p>Kept out of {@link JacksonModuleFeelFunction} on purpose. That module is also registered on
 * the outbound connector mapper and on the Camunda client's mapper, neither of which should resolve
 * secret references: the engine substitutes them into job variables itself. Registering this
 * separately keeps the deserializer on the inbound property-binding mapper alone.
 *
 * <p>The deserializer takes no evaluator: it acts only on a reader that carries one, which the
 * runtime supplies per connector context.
 */
public class JacksonModuleSecretReference extends SimpleModule {

  @Override
  public String getModuleName() {
    return "JacksonModuleSecretReference";
  }

  @Override
  public Version version() {
    return new Version(0, 1, 0, null, "io.camunda", "jackson-datatype-feel");
  }

  @Override
  public void setupModule(SetupContext context) {
    // A modifier rather than a registration, so that whatever else deserializes a String is wrapped
    // instead of replaced. The document module registers its own String deserializer, which turns a
    // document reference into base64 and runs an intrinsic function; registering a second one after
    // it would take precedence and lose both.
    context.addBeanDeserializerModifier(
        new BeanDeserializerModifier() {
          @Override
          public JsonDeserializer<?> modifyDeserializer(
              DeserializationConfig config,
              BeanDescription description,
              JsonDeserializer<?> deserializer) {
            if (description.getBeanClass() == String.class) {
              return new SecretReferenceDeserializer(deserializer);
            }
            return deserializer;
          }
        });
    super.setupModule(context);
  }
}
