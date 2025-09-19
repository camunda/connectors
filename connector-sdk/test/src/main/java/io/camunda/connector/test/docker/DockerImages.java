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
package io.camunda.connector.test.docker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.stream.Collectors;

public class DockerImages {
  private static final Map<String, String> images = loadImages();

  public static final String KAFKA = "kafka";
  public static final String SCHEMA_REGISTRY = "schema-registry";
  public static final String MARIADB = "mariadb";
  public static final String POSTGRES = "postgres";
  public static final String MYSQL = "mysql";
  public static final String MSSQL = "mssql";
  public static final String ORACLE = "oracle";
  public static final String RABBITMQ = "rabbitmq";
  public static final String LOCALSTACK = "localstack";
  public static final String SQUID = "squid";

  private static Map<String, String> loadImages() {
    var dockerImagesUrl = DockerImages.class.getClassLoader().getResource("docker-images.txt");
    Path path = Paths.get(dockerImagesUrl.getPath());
    try (var lines = Files.lines(path)) {
      return lines
          .filter(line -> !line.isBlank() && !line.startsWith("#"))
          .map(line -> line.split("=", 2))
          .collect(Collectors.toMap(parts -> parts[0].trim(), parts -> parts[1].trim()));
    } catch (IOException e) {
      throw new RuntimeException("Failed to load docker-images.txt", e);
    }
  }

  public static String get(String key) {
    return images.get(key);
  }
}
