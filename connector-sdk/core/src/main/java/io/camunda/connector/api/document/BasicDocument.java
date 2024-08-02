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

import java.util.Map;

public record BasicDocument(DocumentMetadata metadata, DocumentSource source) implements Document {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private Map<String, Object> metadata;
    private DocumentSource source;

    public Builder metadata(Map<String, Object> metadata) {
      this.metadata = metadata;
      return this;
    }

    public Builder source(DocumentSource source) {
      this.source = null;
      return this;
    }

    public BasicDocument build() {

      return new BasicDocument(metadata, source);
    }
  }
}
