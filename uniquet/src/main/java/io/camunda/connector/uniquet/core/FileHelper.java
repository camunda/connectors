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
package io.camunda.connector.uniquet.core;

import static com.fasterxml.jackson.core.util.DefaultIndenter.SYSTEM_LINEFEED_INSTANCE;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.TreeMap;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class FileHelper {
  private static final ObjectMapper mapper = new ObjectMapper();
  private static final ObjectWriter objectWriter =
      new ObjectMapper()
          .writer(new DefaultPrettyPrinter().withObjectIndenter(SYSTEM_LINEFEED_INSTANCE));

  public static String getBaseName(File file) {
    String filename = file.getName();
    int index = filename.lastIndexOf('.');
    if (index != -1) {
      filename = filename.substring(0, index);
    }
    return filename;
  }

  public static JsonNode toJsonNode(File jsonFile) {
    try {
      return mapper.readTree(jsonFile);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void writeToFile(File file, JsonNode jsonNode) {
    try {
      file.getParentFile().mkdirs();
      if (!file.createNewFile() && !file.exists()) {
        throw new IOException("File already exists: " + file.getAbsolutePath());
      }
      objectWriter.writeValue(file, jsonNode);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  public static void writeToFile(File file, Map<String, ?> map) {
    TreeMap<String, ?> treeMap = new TreeMap<>(map);
    JsonNode jsonNode = mapper.valueToTree(treeMap);
    writeToFile(file, jsonNode);
  }

  public static String getCurrentGitSha256(final String gitDirectory) {
    try (Git git = Git.open(new File(gitDirectory))) {
      Repository repository = git.getRepository();
      ObjectId headId = repository.resolve("HEAD");
      try (RevWalk walk = new RevWalk(repository)) {
        RevCommit lastCommit = walk.parseCommit(headId);
        return lastCommit.getName();
      }
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }
}
