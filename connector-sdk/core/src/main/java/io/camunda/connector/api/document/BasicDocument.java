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

import java.util.Objects;

public class BasicDocument implements Document {

  private final Object metadata;
  private final DocumentContent content;

  private BasicDocument(Object metadata, DocumentContent content) {
    this.metadata = metadata;
    this.content = content;
  }

  @Override
  public Object getMetadata() {
    return metadata;
  }

  @Override
  public DocumentContent getContent() {
    return content;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BasicDocument that = (BasicDocument) o;
    return Objects.equals(metadata, that.metadata) && Objects.equals(content, that.content);
  }

  @Override
  public int hashCode() {
    return Objects.hash(metadata, content);
  }
}
