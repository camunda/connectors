/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets.util;

public class LetterNumericSystemConverter {

  private static final int BASE = 26;
  private static final int SHIFT = 64;

  private LetterNumericSystemConverter() {}

  /**
   * Spreadsheet letter to numeric index int. Parse number format of column id to number (A -> 0, B
   * -> 1,...)
   *
   * @param number the letters number of a column
   * @return the int number of a column
   */
  public static int spreadsheetLetterToNumericIndex(String number) {
    int result = 0;
    char[] columnName = number.toUpperCase().toCharArray();

    for (char c : columnName) {
      result = result * BASE + c - SHIFT;
    }

    result -= 1; // As column's count starts with 0;

    return result;
  }
}
