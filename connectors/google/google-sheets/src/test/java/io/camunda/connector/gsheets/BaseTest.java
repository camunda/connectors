/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.gsheets;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.gson.Gson;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.stream.Stream;
import org.junit.jupiter.params.provider.Arguments;

public abstract class BaseTest {

  protected static final String SECRET_BEARER_TOKEN = "MyToken";
  protected static final String SECRET_REFRESH_TOKEN = "MyOauthRefresh";
  protected static final String ACTUAL_BEARER_TOKEN = "MyRealToken";
  protected static final String ACTUAL_REFRESH_TOKEN = "MyRealRefreshTokenValue";
  protected static final String SECRET_OAUTH_CLIENT_ID = "MyOauthClient";
  protected static final String ACTUAL_OAUTH_CLIENT_ID = "MyRealOauthClientValue";
  protected static final String SECRET_OAUTH_SECRET_ID = "MyOauthSecret";
  protected static final String ACTUAL_OAUTH_SECRET_ID = "MyRealOauthSecretValue";
  protected static final String SECRET_PARENT = "PARENT_ID";
  protected static final String ACTUAl_PARENT = "Real parent";
  protected static final String SECRET_SPREADSHEET_NAME = "SPREADSHEET_NAME";
  protected static final String SPREADSHEET_NAME = "spreadsheet name";
  protected static final String SECRET_SPREADSHEET_ID = "SPREADSHEET_ID";
  protected static final String SPREADSHEET_ID = "id";
  protected static final String SPREADSHEET_URL = "url";
  protected static final String PARENT = "parent";
  protected static final String SECRET_WORKSHEET_NAME = "WORKSHEET_NAME";
  protected static final String WORKSHEET_NAME = "worksheet";
  protected static final String SECRET_CELL_ID = "CELL_ID";
  protected static final String ACTUAL_CELL_ID = "actual cell id";
  protected static final String SECRET_CELL_VALUE = "VALUE";
  protected static final String ACTUAL_CELL_VALUE = "actual cell value";
  protected static final String SECRET_ROW = "ROW";
  protected static final String ACTUAL_ROW = "row";
  protected static final int WORKSHEET_INDEX = 2;
  protected static final int WORKSHEET_ID = 123;
  protected static final int ROW_INDEX = 3;

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final Gson testingGson = new Gson();
    var array = testingGson.fromJson(cases, ArrayList.class);
    return array.stream().map(testingGson::toJson).map(Arguments::of);
  }

  protected String getRange() {
    return ROW_INDEX + ":" + ROW_INDEX;
  }

  protected String getRangeWithWorksheetName() {
    return WORKSHEET_NAME + "!" + getRange();
  }
}
