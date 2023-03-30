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
package io.camunda.connector.api.inbound;

import io.camunda.connector.impl.inbound.result.CorrelationErrorData;
import java.util.Optional;

/**
 * Contains general information about the inbound correlation results.
 *
 * <p>This information is specific to the process correlation point type, e.g. subscription key and
 * message name in case of an IntermediateEvent target, or process definition key in case of a
 * StartEvent target.
 */
public interface InboundConnectorResult<T> {

  /** Type of process correlation point, e.g. StartEvent or Message */
  String getType();

  /** ID of a process correlation point (unique within its type, see {@link #getType()} */
  String getCorrelationPointId();

  /** Whether connector was activated */
  boolean isActivated();

  /**
   * Additional information related to Inbound Connector correlation result. Only present when
   * {@link #isActivated()} returns true.
   */
  Optional<T> getResponseData();

  /**
   * Additional information about correlation failure reasons. Only present when {@link
   * #isActivated()} returns false.
   */
  Optional<CorrelationErrorData> getErrorData();
}
