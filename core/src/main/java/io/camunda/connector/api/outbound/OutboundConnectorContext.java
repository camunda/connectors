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
package io.camunda.connector.api.outbound;

import java.util.Map;

/**
 * The context object provided to a connector function. The context allows to fetch information
 * injected by the environment runtime.
 */
public interface OutboundConnectorContext {

  /**
   * Custom headers found under zeebe:taskHeaders.
   *
   * @return job headers.
   */
  Map<String, String> getCustomerHeaders();

  /**
   * @return the raw variables input as JSON String
   */
  String getVariables();

  <T> T bindVariables(Class<T> cls);

  void validate(Object input);
}
