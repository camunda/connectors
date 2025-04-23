package io.camunda.connector.runtime.instances;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;

public sealed interface InstanceAwareModel
    permits InstanceAwareModel.InstanceAwareActivity, InstanceAwareModel.InstanceAwareHealth {
  record InstanceAwareActivity(@JsonUnwrapped Activity model, String runtimeId)
      implements InstanceAwareModel {}

  record InstanceAwareHealth(@JsonUnwrapped Health model, String runtimeId)
      implements InstanceAwareModel {}
}
