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
package io.camunda.connector.e2e;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.apache.commons.io.IOUtils;

public class ElementTemplate {

  private DocumentContext documentContext;

  public ElementTemplate(File input) {
    try {
      documentContext = JsonPath.parse(input);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static ElementTemplate from(String file) {
    return new ElementTemplate(new File(file));
  }

  public ElementTemplate property(String propertyId, String value) {
    try {
      documentContext =
          documentContext.put("$..properties[?(@.id=='" + propertyId + "')]", "value", value);
    } catch (com.jayway.jsonpath.PathNotFoundException e) {
      throw new RuntimeException("Property path not found for property ID: " + propertyId, e);
    }
    return this;
  }

  public File writeTo(File output) {
    var updatedElementTemplate = documentContext.jsonString();
    try {
      IOUtils.write(updatedElementTemplate, new FileOutputStream(output), StandardCharsets.UTF_8);
      return output;
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
