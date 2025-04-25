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
package io.camunda.connector.runtime.core.http;

import java.util.List;

/** Interface for building URLs for multiple Connectors runtime instances. */
public interface InstancesUrlBuilder {

  /**
   * Builds a list of URLs for the given path. The URLs are constructed using the base URLs of the
   * Connectors runtime instances and the provided path.
   *
   * @param path the path to append to the base URLs
   * @return a list of constructed URLs
   */
  List<String> buildUrls(String path);
}
