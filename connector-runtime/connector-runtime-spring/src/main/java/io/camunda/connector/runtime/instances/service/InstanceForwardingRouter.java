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

  public InstanceForwardingRouter(InstanceForwardingService instanceForwardingService) {
    this.instanceForwardingService = instanceForwardingService;
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
    if (instanceForwardingService != null && StringUtils.isBlank(forwardedFor)) {
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
