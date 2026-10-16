/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.common.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import io.camunda.connector.agenticai.common.http.AgenticAiHttpProxySupport;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AgenticAiCommonAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner =
      new ApplicationContextRunner()
          .withConfiguration(AutoConfigurations.of(AgenticAiCommonAutoConfiguration.class));

  @Test
  void createsProxySupport() {
    contextRunner.run(
        context -> assertThat(context).hasSingleBean(AgenticAiHttpProxySupport.class));
  }

  @Test
  void createsNoProxySupportWhenAgenticAiIsDisabled() {
    contextRunner
        .withPropertyValues("camunda.connector.agenticai.enabled=false")
        .run(context -> assertThat(context).doesNotHaveBean(AgenticAiHttpProxySupport.class));
  }

  @Test
  void startsWithoutProxySupportWhenTheHttpClientIsMissing() {
    // applications depending only on the model or the API exclude the HTTP client
    contextRunner
        .withClassLoader(new FilteredClassLoader("io.camunda.connector.http.client"))
        .run(
            context -> {
              assertThat(context).hasNotFailed();
              assertThat(context).doesNotHaveBean(AgenticAiHttpProxySupport.class);
            });
  }
}
