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
package io.camunda.document;

import java.util.Map;

public class DocumentMetadata {

  public static final String CONTENT_TYPE = "contentType";
  public static final String FILE_NAME = "fileName";
  public static final String DESCRIPTION = "description";

  private final Map<String, Object> keys;

  public DocumentMetadata(Map<String, Object> keys) {
    this.keys = keys;
  }

  public Map<String, Object> getKeys() {
    return keys;
  }

  public Object getKey(String key) {
    return keys.get(key);
  }

  public String getContentType() {
    return (String) keys.get(CONTENT_TYPE);
  }

  public String getFileName() {
    return (String) keys.get(FILE_NAME);
  }

  public String getDescription() {
    return (String) keys.get(DESCRIPTION);
  }
}
