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
package io.camunda.connector.api.document;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Map;

public record BasicDocument(Map<String, Object> metadata) implements Document {

  @Override
  public Map<String, Object> getMetadata() {
    return metadata;
  }

  @Override
  public byte[] loadAsByteArray(DocumentStore store) {
    return store.load((String) metadata.get("docRef"));
  }

  @Override
  public String loadAsBase64(DocumentStore store) {
    return new String(store.load((String) metadata.get("docRef")));
  }

  @Override
  public InputStream loadAsStream(DocumentStore store) {
    Object url = metadata.get("url");
    if (url == null) {
      var content = loadAsByteArray(store);
      return new ByteArrayInputStream(content);
    }
    try {
      return new URL((String) url).openStream();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
