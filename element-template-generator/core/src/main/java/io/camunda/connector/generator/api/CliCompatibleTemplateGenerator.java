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

import io.camunda.connector.generator.dsl.ElementTemplateBase;
import java.util.List;

public interface CliCompatibleTemplateGenerator<IN, OUT extends ElementTemplateBase>
    extends ElementTemplateGenerator<IN, OUT> {

  /** ID of the generator. This ID is used to identify the generator in the CLI. */
  String getGeneratorId();

  /**
   * Prepare the input for the generation process. This method receives a list of CLI parameters
   * passed to the generator and should return a data structure consumable by the other methods.
   */
  IN prepareInput(List<String> parameters);

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
}
