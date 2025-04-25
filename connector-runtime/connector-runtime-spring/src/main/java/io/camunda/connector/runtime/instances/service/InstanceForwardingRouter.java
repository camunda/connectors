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
package io.camunda.connector.runtime.instances.service;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class InstanceForwardingRouter {
  private static final Logger LOGGER = LoggerFactory.getLogger(InstanceForwardingRouter.class);

  private final InstanceForwardingService instanceForwardingService;
  private final boolean isInstanceForwardingServiceConfigured;

  public InstanceForwardingRouter(InstanceForwardingService instanceForwardingService) {
    this.instanceForwardingService = instanceForwardingService;
    this.isInstanceForwardingServiceConfigured = instanceForwardingService != null;
  }

  /**
   * This method is used to forward the request to the instances or local service depending on the
   * configuration.
   *
   * @see io.camunda.connector.runtime.instances.InstanceForwardingConfiguration
   */
  public <T> T forwardToInstancesAndReduceOrLocal(
      HttpServletRequest request,
      String forwardedFor,
      Supplier<T> localImplementation,
      TypeReference<T> typeReference) {
    if (isInstanceForwardingServiceConfigured && StringUtils.isBlank(forwardedFor)) {
      LOGGER.debug(
          "Forwarding request to instances: {}",
          request.getRequestURL().toString() + "?" + request.getQueryString());
      return instanceForwardingService.forwardAndReduce(request, typeReference);
    }
    LOGGER.debug(
        "InstanceForwardingService not configured, performing local call for request: {}", request);
    return localImplementation.get();
  }
}
