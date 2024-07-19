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

public interface CliCompatibleTemplateGenerator<IN> extends ElementTemplateGenerator<IN> {

  /** ID of the generator. This ID is used to identify the generator in the CLI. */
  String getGeneratorId();

  /**
   * Prepare the input for the generation process. This method receives a list of CLI parameters
   * passed to the generator and should return a data structure consumable by the other methods.
   */
  IN prepareInput(List<String> parameters);

  /** Provides a usage description for the generator. This description is used in the CLI help. */
  String getUsage();

  /** Scan the source and returns with operations (both supported and not supported). */
  List<Operation> operations(IN input);

  /** Scan the source and do a dry run of the generation process. */
  ScanResult scan(IN input);

  /**
   * Preview of the generation result.
   *
   * @param templateId ID of the resulting template
   * @param templateName Name of the resulting template
   * @param templateVersion Version of the resulting template
   * @param templateType Type of the resulting template
   * @param additionalData Any additional information provided by the generator
   */
  record ScanResult(
      String templateId,
      String templateName,
      Integer templateVersion,
      String templateType,
      Object additionalData) {}

  /**
   * Preview of the scanned operations result.
   *
   * @param id ID of the operation
   * @param path Url path of the operation
   * @param method Method(Get, Post) of the operation
   * @param tags Tags of the operation
   * @param supported Indicate whether the operation is supported or not
   */
  record Operation(String id, String path, String method, List<String> tags, boolean supported) {
    public static class Builder {
      String id;
      String path;
      String method;
      List<String> tags;
      boolean supported;

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

      public Operation build() {
        return new Operation(id, path, method, tags, supported);
      }
    }
  }
}
