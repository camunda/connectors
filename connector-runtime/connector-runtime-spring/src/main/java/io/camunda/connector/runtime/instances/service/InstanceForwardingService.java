package io.camunda.connector.runtime.instances.service;

import com.fasterxml.jackson.core.type.TypeReference;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

public interface InstanceForwardingService {
  String X_CAMUNDA_FORWARDED_FOR = "X-CAMUNDA-FORWARDED-FOR";

  <T> List<T> forward(HttpServletRequest request, TypeReference<T> responseType);

  <T> T reduce(List<T> instances, TypeReference<T> responseType);

  default <T> T forwardAndReduce(HttpServletRequest request, TypeReference<T> responseType) {
    List<T> instances = forward(request, responseType);
    return reduce(instances, responseType);
  }
}
