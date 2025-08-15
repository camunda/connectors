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
package io.camunda.connector.http.client;

import io.camunda.connector.api.document.DocumentFactory;

public sealed interface ExecutionEnvironment
    permits ExecutionEnvironment.SaaSCluster,
        ExecutionEnvironment.SaaSCloudFunction,
        ExecutionEnvironment.SelfManaged {

  /**
   * The connector is executed in the context of the cloud function. This is where the
   * HttpCommonRequest will be executed.
   */
  record SaaSCloudFunction() implements ExecutionEnvironment {}

  /**
   * The connector is executed in the context of the caller, i.e. in the C8 Cluster. When executed
   * here, the initial HttpCommonRequest will be serialized as JSON and passed to the Cloud
   * Function.
   */
  record SaaSCluster(DocumentFactory documentFactory)
      implements ExecutionEnvironment, StoresDocument {}

  record SelfManaged(DocumentFactory documentFactory)
      implements ExecutionEnvironment, StoresDocument {}

  /**
   * Factory method to create an ExecutionEnvironment based on the given parameters.
   *
   * @param cloudFunctionEnabled whether the connector is executed in the context of a cloud
   *     function
   * @param isRunningInCloudFunction whether the connector is executed in the cloud function
   */
  static ExecutionEnvironment from(
      boolean cloudFunctionEnabled,
      boolean isRunningInCloudFunction,
      DocumentFactory documentFactory) {
    if (cloudFunctionEnabled) {
      return new SaaSCluster(documentFactory);
    }
    if (isRunningInCloudFunction) {
      return new SaaSCloudFunction();
    }
    return new SelfManaged(documentFactory);
  }

  interface StoresDocument {
    DocumentFactory documentFactory();
  }
}
