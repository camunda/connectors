/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.xml;

import static org.assertj.core.api.Assertions.assertThat;

import com.samskivert.mustache.Template;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateUtilTest {

  @Test
  void shouldEscapeXml() {
    Template template = TemplateUtil.compileTemplate("<element>{{content}}</element>", true);
    Map<String, Object> context = Map.of("content", "<inner>xml</inner>");
    String result = template.execute(context);
    assertThat(result).isEqualTo("<element>&lt;inner&gt;xml&lt;/inner&gt;</element>");
  }
}
