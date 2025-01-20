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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.uniquet.dto.ElementTemplate;
import io.camunda.connector.uniquet.dto.ElementTemplateFile;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.Objects;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ElementTemplateIterator implements Iterator<ElementTemplateFile> {

  private static final Logger log = LoggerFactory.getLogger(ElementTemplateIterator.class);
  private final RevCommit commit;
  private final ObjectMapper objectMapper;
  private final Repository repository;
  private final TreeWalk initialWalk;
  private final String currentConnectorRuntime;
  private ElementTemplateFile elementTemplate;
  private TreeWalk currentWalk;
  private String currentFolderBeingAnalyzed;

  public ElementTemplateIterator(Repository repository, RevCommit commit) {
    this.commit = commit;
    this.repository = repository;
    this.objectMapper = new ObjectMapper();
    try {
      TreeWalk treeWalk = new TreeWalk(repository);
      treeWalk.addTree(this.commit.getTree());
      treeWalk.setRecursive(false);
      this.initialWalk = treeWalk;
      this.initialWalk.next();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    this.currentConnectorRuntime = findCurrentConnectorRuntime(repository, commit);
    this.elementTemplate = this.prepareNext();
  }

  private static String findCurrentConnectorRuntime(Repository repository, RevCommit commit) {
    TreeWalk treeWalk = new TreeWalk(repository);
    try {
      treeWalk.addTree(commit.getTree());
      treeWalk.setRecursive(false);
      treeWalk.setFilter(PathFilter.create("pom.xml"));
      treeWalk.next();
      ObjectId objectId = treeWalk.getObjectId(0);
      ObjectLoader loader = repository.open(objectId);
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      loader.copyTo(out);
      String pomContent = out.toString(StandardCharsets.UTF_8);
      try (StringReader reader = new StringReader(pomContent)) {
        MavenXpp3Reader mavenReader = new MavenXpp3Reader();
        Model model = mavenReader.read(reader);
        String[] version = model.getParent().getVersion().split("\\.");
        return version[0] + "." + version[1];
      }
    } catch (IOException
        | XmlPullParserException
        | NullPointerException
        | IndexOutOfBoundsException e) {
      log.error("Commit: {}. No connector runtime found", commit.getName());
      return "";
    }
  }

  @Override
  public boolean hasNext() {
    return this.elementTemplate != null;
  }

  @Override
  public ElementTemplateFile next() {
    ElementTemplateFile elementTemplate = this.elementTemplate;
    this.elementTemplate = this.prepareNext();
    return elementTemplate;
  }

  private ElementTemplateFile prepareNext() {
    try {
      do {
        if (this.initialWalk.isSubtree()
            && !this.initialWalk.getPathString().endsWith("element-templates")) {
          this.initialWalk.enterSubtree();
        } else if (this.initialWalk.getPathString().endsWith("element-templates")) {
          if (!Objects.equals(this.currentFolderBeingAnalyzed, this.initialWalk.getPathString())) {
            this.currentFolderBeingAnalyzed = this.initialWalk.getPathString();
            this.currentWalk = new TreeWalk(repository);
            this.currentWalk.addTree(this.commit.getTree());
            this.currentWalk.setRecursive(true);
            this.currentWalk.setFilter(PathFilter.create(this.currentFolderBeingAnalyzed));
          }
          while (this.currentWalk.next()) {
            if (!this.currentWalk.getPathString().endsWith(".json")) continue;
            if (this.currentWalk.getPathString().contains("/hybrid/")) continue;
            ObjectId objectId = this.currentWalk.getObjectId(0);
            ObjectLoader loader = this.repository.open(objectId);
            byte[] bytes = loader.getBytes();
            try {
              return new ElementTemplateFile(
                  objectMapper.readValue(bytes, ElementTemplate.class),
                  this.currentWalk.getPathString(),
                  this.currentConnectorRuntime);
            } catch (IOException e) {
              System.err.println(
                  "Error while reading element-template: "
                      + this.currentWalk.getPathString()
                      + ". Commit is: "
                      + commit.getName());
            }
          }
        }
      } while (this.initialWalk.next());
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return null;
  }
}
