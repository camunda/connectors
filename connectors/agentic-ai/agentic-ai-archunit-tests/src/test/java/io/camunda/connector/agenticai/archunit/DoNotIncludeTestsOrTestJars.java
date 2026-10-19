/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.archunit;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.Location;

/**
 * Restricts the analysis to production classes: excludes the test output directories and test jars
 * ({@code *-tests.jar}) of the analyzed modules.
 */
public final class DoNotIncludeTestsOrTestJars implements ImportOption {

  private static final ImportOption DO_NOT_INCLUDE_TESTS = new ImportOption.DoNotIncludeTests();

  @Override
  public boolean includes(final Location location) {
    return DO_NOT_INCLUDE_TESTS.includes(location) && !location.contains("-tests.jar");
  }
}
