/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.sandbox.daytona;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.agenticai.sandbox.skill.SkillMdParser;
import io.camunda.connector.agenticai.sandbox.skill.SkillResolver;
import io.camunda.connector.runtime.annotation.ConnectorsObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnBooleanProperty(
    value = "camunda.connector.agenticai.sandbox.daytona.enabled",
    matchIfMissing = true)
public class SandboxDaytonaConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public SandboxDaytonaFunction sandboxDaytonaFunction(
      @ConnectorsObjectMapper ObjectMapper objectMapper) {
    return new SandboxDaytonaFunction(
        new DaytonaClient(), new SkillResolver(), new SkillMdParser(), objectMapper);
  }
}
