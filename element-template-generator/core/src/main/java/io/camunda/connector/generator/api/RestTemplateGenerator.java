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
package io.camunda.connector.generator.api;

import java.util.List;

public interface RestTemplateGenerator<IN> extends CliCompatibleTemplateGenerator<IN> {

  /** Scan the source and returns with operations (both supported and not supported). */
  List<Operation> operations(IN input);

  /**
   * Preview of the scanned operations result.
   *
   * @param id ID of the operation
   * @param path Url path of the operation
   * @param method Method(Get, Post) of the operation
   * @param tags Tags of the operation
   * @param supported Indicate whether the operation is supported or not
   * @param description Description of the operation
   */
  record Operation(
      String id,
      String path,
      String method,
      List<String> tags,
      boolean supported,
      String description) {
    public static class Builder {
      String id;
      String path;
      String method;
      List<String> tags;
      boolean supported;
      String description;

      public Builder id(String id) {
        this.id = id;
        return this;
      }

      public Builder path(String path) {
        this.path = path;
        return this;
      }

      public Builder method(String method) {
        this.method = method;
        return this;
      }

      public Builder tags(List<String> tags) {
        this.tags = tags;
        return this;
      }

      public Builder supported(boolean supported) {
        this.supported = supported;
        return this;
      }

      public Builder description(String description) {
        this.description = description;
        return this;
      }

      public Operation build() {
        return new Operation(id, path, method, tags, supported, description);
      }
    }
  }
}
