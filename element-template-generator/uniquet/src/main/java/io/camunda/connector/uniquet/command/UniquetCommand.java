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
package io.camunda.connector.uniquet.command;

import io.camunda.connector.uniquet.core.GitCrawler;
import java.util.concurrent.Callable;
import picocli.CommandLine;

public class UniquetCommand implements Callable<Integer> {

  @CommandLine.Option(
      names = {"-b", "--branch"},
      defaultValue = "main")
  private String branch;

  @CommandLine.Option(
      names = {"-d", "--destination"},
      required = true)
  private String pathDestination;

  @CommandLine.Option(
      names = {"-g", "--git-directory"},
      defaultValue = "")
  private String gitDirectory;

  @Override
  public Integer call() {
    try {
      GitCrawler gitCrawler = GitCrawler.create(gitDirectory);
      gitCrawler.crawl(branch).persist(pathDestination).close();
    } catch (RuntimeException e) {
      System.err.println(e.getMessage());
      return 1;
    }
    return 0;
  }
}
