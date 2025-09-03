/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.http.polling.model;

import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import io.camunda.connector.feel.annotation.FEEL;
import io.camunda.connector.generator.dsl.Property;
import io.camunda.connector.generator.java.annotation.TemplateProperty;
import io.camunda.connector.http.base.model.HttpCommonInboundRequest;
import java.time.Duration;

public class PollingRequest extends HttpCommonInboundRequest {
  @JsonSetter(nulls = Nulls.SKIP)
  @TemplateProperty(
      id = "httpRequestInterval",
      group = "interval",
      defaultValue = "PT50S",
      binding = @TemplateProperty.PropertyBinding(name = "httpRequestInterval"),
      description =
          "The delay between HTTP requests, defined as ISO 8601 durations format. <a href='https://docs.camunda.io/docs/components/modeler/bpmn/timer-events/#time-duration' target='_blank'>How to configure a time duration</a>",
      feel = Property.FeelMode.optional)
  @FEEL
  private Duration httpRequestInterval = Duration.parse("PT50S");

  @JsonSetter(nulls = Nulls.SKIP)
  @TemplateProperty(
      id = "processPollingInterval",
      group = "interval",
      defaultValue = "PT5S",
      type = TemplateProperty.PropertyType.Hidden,
      binding = @TemplateProperty.PropertyBinding(name = "processPollingInterval"))
  @FEEL
  private Duration processPollingInterval = Duration.parse("PT5S");

  public Duration getHttpRequestInterval() {
    return httpRequestInterval;
  }

  public void setHttpRequestInterval(Duration httpRequestInterval) {
    this.httpRequestInterval = httpRequestInterval;
  }

  public Duration getProcessPollingInterval() {
    return processPollingInterval;
  }

  public void setProcessPollingInterval(Duration processPollingInterval) {
    this.processPollingInterval = processPollingInterval;
  }
}
