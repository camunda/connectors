/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.util;

import static io.camunda.connector.gsheets.util.LetterNumericSystemConverter.spreadsheetLetterToNumericIndex;
import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LetterNumericSystemConverterTest {

  @DisplayName("Should convert column letter's index to number")
  @Test
  void stringToInt() {
    assertEquals(0, spreadsheetLetterToNumericIndex("A"));
    assertEquals(0, spreadsheetLetterToNumericIndex("a"));
    assertEquals(1, spreadsheetLetterToNumericIndex("B"));
    assertEquals(25, spreadsheetLetterToNumericIndex("Z"));
    assertEquals(26, spreadsheetLetterToNumericIndex("AA"));
    assertEquals(51, spreadsheetLetterToNumericIndex("AZ"));
    assertEquals(52, spreadsheetLetterToNumericIndex("BA"));
    assertEquals(701, spreadsheetLetterToNumericIndex("ZZ"));
    assertEquals(702, spreadsheetLetterToNumericIndex("AAA"));
  }
}
