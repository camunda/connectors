/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.appintegrations.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Represents one entry from the job's {@code linkedResources} custom header JSON array. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LinkedResource(String resourceKey, String resourceType, String linkName) {}
