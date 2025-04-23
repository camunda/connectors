package io.camunda.connector.runtime;

import io.camunda.connector.runtime.instances.InstanceForwardingConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnProperty(
    prefix = "camunda.connector.instance.forwarding",
    name = "enabled",
    havingValue = "true",
    matchIfMissing = true)
@Import(InstanceForwardingConfiguration.class)
public class InstanceForwardingAutoConfiguration {}
