/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information regarding copyright
 * ownership. Camunda licenses this file to you under the Apache License,
 * Version 2.0; you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.camunda.connector.gdrive;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.Files.readString;

import com.google.api.client.json.JsonParser;
import com.google.gson.Gson;
import io.camunda.connector.gdrive.supliers.GsonComponentSupplier;
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
  protected static final String FOLDER_NAME = "MyNewFolder";
  protected static final String FILE_NAME = "MyNewFile";
  protected static final String PARENT_ID = "optional my idFolderParent";
  protected static final String FILE_ID = "123456789654321564897";
  protected static final String FILE_URL = "https://docs.google.com/document/d/123456644";

  @SuppressWarnings("unchecked")
  protected static Stream<String> loadTestCasesFromResourceFile(final String fileWithTestCasesUri)
      throws IOException {
    final String cases = readString(new File(fileWithTestCasesUri).toPath(), UTF_8);
    final Gson testingGson = new Gson();
    var array = testingGson.fromJson(cases, ArrayList.class);
    return array.stream().map(testingGson::toJson).map(Arguments::of);
  }

  protected static <T> T parseInput(final String input, final Class<T> clazz) {
    JsonParser jsonParser = GsonComponentSupplier.gsonFactoryInstance().createJsonParser(input);
    try {
      return jsonParser.parseAndClose(clazz);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
