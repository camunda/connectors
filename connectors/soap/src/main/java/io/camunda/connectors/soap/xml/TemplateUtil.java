/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connectors.soap.xml;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;
import java.io.IOException;
import java.io.InputStream;

public class TemplateUtil {
  private TemplateUtil() {}

  public static Template loadTemplate(String filename, boolean escapeHtml) {
    try (InputStream in = XmlUtilities.class.getClassLoader().getResourceAsStream(filename)) {
      return compileTemplate(new String(in.readAllBytes()), escapeHtml);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static Template compileTemplate(String template, boolean escapeHtml) {
    return Mustache.compiler().escapeHTML(escapeHtml).compile(template);
  }
}
