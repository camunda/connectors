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
package io.camunda.connector.generator.java.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.camunda.connector.generator.java.annotation.FeelMode;
import java.io.IOException;

public class FeelModelSerializer extends JsonSerializer<FeelMode> {
  @Override
  public void serialize(
      FeelMode feelMode, JsonGenerator jsonGenerator, SerializerProvider serializerProvider)
      throws IOException {
    if (feelMode == FeelMode.staticFeel) {
      // The Element Template specification expects "static" instead of "staticFeel" for the static
      // FEEL mode.
      // https://github.com/camunda/element-templates-json-schema/blob/ff13a03abd02bfb45a6fe495dd291c1d9ee61dbd/packages/zeebe-element-templates-json-schema/src/defs/properties.json#L1008
      jsonGenerator.writeString("static");
    } else {
      jsonGenerator.writeString(feelMode.name());
    }
  }

  @Override
  public Class<FeelMode> handledType() {
    return FeelMode.class;
  }
}
