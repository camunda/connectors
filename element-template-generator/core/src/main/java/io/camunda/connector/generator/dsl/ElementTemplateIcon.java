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

import java.io.IOException;
import java.util.Base64;

public record ElementTemplateIcon(String contents) {

  public static ElementTemplateIcon from(String location, ClassLoader classLoader) {
    if (location.startsWith("https://")) {
      return new ElementTemplateIcon(location);
    }
    try {
      return new ElementTemplateIcon(resolveIconFile(location, classLoader));
    } catch (Exception e) {
      throw new IllegalArgumentException(
          "Invalid icon location: " + location + ", " + e.getMessage(), e);
    }
  }

  private static String resolveIconFile(String path, ClassLoader classLoader) throws IOException {
    var resource = classLoader.getResource(path);
    if (resource == null) {
      throw new IllegalArgumentException("Icon file not found: " + path);
    }
    String base64Data;
    try (var stream = resource.openStream()) {
      var bytes = stream.readAllBytes();
      base64Data = Base64.getEncoder().encodeToString(bytes);
    }

    if (path.endsWith(".svg")) {
      return "data:image/svg+xml;base64," + base64Data;
    } else if (path.endsWith(".png")) {
      return "data:image/png;base64," + base64Data;
    } else {
      throw new IllegalArgumentException("Unsupported icon file: " + path);
    }
  }
}
