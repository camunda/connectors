/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

import java.util.List;
import org.springframework.boot.test.context.assertj.AssertableApplicationContext;

class ApplicationContextAssertions {

  static void assertHasAllBeansOf(AssertableApplicationContext context, List<Class<?>> beans) {
    assertAll(beans.stream().map(beanClass -> () -> assertThat(context).hasSingleBean(beanClass)));
  }

  static void assertHasAllBeansOfAtLeastOnce(AssertableApplicationContext context, List<Class<?>> beans) {
    assertAll(beans.stream().map(beanClass -> () -> assertThat(context).getBeans(beanClass).hasSizeGreaterThanOrEqualTo(1)));
  }

  static void assertDoesNotHaveAnyBeansOf(
      AssertableApplicationContext context, List<Class<?>> beans) {
    assertAll(
        beans.stream().map(beanClass -> () -> assertThat(context).doesNotHaveBean(beanClass)));
  }
}
