/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.idp.extraction.templates;

/** Where the IDP element templates live on the classpath. */
public final class IdpElementTemplates {

  /** Classpath folder with the current templates; versioned snapshots are in {@code versioned/}. */
  public static final String RESOURCE_ROOT = "META-INF/camunda/element-templates/idp";

  /** Pattern for Spring's {@code PathMatchingResourcePatternResolver} matching every template. */
  public static final String RESOURCE_PATTERN = "classpath*:" + RESOURCE_ROOT + "/**/*.json";

  private IdpElementTemplates() {}
}
