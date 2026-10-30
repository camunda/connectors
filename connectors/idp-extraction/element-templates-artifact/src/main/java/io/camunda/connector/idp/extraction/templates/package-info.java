/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
/**
 * Element templates of the Camunda IDP extraction connectors.
 *
 * <p>This artifact ships every IDP element template, current and versioned, as classpath resources
 * under {@code META-INF/camunda/element-templates/idp/} (versioned snapshots under {@code
 * versioned/}). Consumers pick the highest template {@code version} per {@code id} whose {@code
 * engines.camunda} range matches the target cluster.
 *
 * <p>{@link IdpElementTemplates} holds the resource path. It also keeps the sources and javadoc
 * jars required by Maven Central from being empty.
 */
package io.camunda.connector.idp.extraction.templates;
