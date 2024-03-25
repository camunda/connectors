package io.camunda.connector.runtime.inbound.executable;

import io.camunda.connector.api.inbound.Activity;
import io.camunda.connector.api.inbound.Health;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.runtime.core.inbound.InboundConnectorDefinitionImpl;
import java.util.Collection;

public record ActiveExecutableResponse(
    Class<? extends InboundConnectorExecutable> executableClass,
    InboundConnectorDefinitionImpl definition,
    Health health,
    Collection<Activity> logs
) {}
