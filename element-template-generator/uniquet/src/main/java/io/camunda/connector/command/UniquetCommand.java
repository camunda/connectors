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
package io.camunda.connector.command;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Callable;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import picocli.CommandLine;

public class UniquetCommand implements Callable<Integer> {

  @CommandLine.Option(names = {"-b", "--branch"})
  private String branch;

  @Override
  public Integer call() {
    File gitFile = new File(".git");
    try (Repository repository = FileRepositoryBuilder.create(gitFile)) {
      RevWalk walk = new RevWalk(repository);
      ObjectId mainBranch = repository.resolve("refs/heads/main");
      walk.markStart(walk.parseCommit(mainBranch));
      for (RevCommit commit : walk) {
        RevTree revTree = commit.getTree();
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
          treeWalk.addTree(revTree);
          treeWalk.setRecursive(false);
          treeWalk.setFilter(PathFilter.create("connectors"));
          while (treeWalk.next()) {
            if (treeWalk.isSubtree() && !treeWalk.getPathString().endsWith("element-templates")) {
              treeWalk.enterSubtree();
            } else if (treeWalk.getPathString().endsWith("element-templates")) {
              try (TreeWalk elementTemplatesWalk = new TreeWalk(repository)) {
                elementTemplatesWalk.addTree(revTree);
                elementTemplatesWalk.setRecursive(true);
                elementTemplatesWalk.setFilter(PathFilter.create(treeWalk.getPathString()));
                while (elementTemplatesWalk.next()) {
                  System.out.println(elementTemplatesWalk.getPathString());
                  ObjectId objectId = treeWalk.getObjectId(0);
                  ObjectLoader loader = repository.open(objectId);
                  loader.copyTo(System.out);
                }
                System.out.println("\n");
              }
            }
          }
        }
      }

    } catch (IOException e) {

      return -1;
    }

    return 0;
  }
}
