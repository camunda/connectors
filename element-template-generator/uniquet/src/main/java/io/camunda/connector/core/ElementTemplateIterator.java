package io.camunda.connector.core;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.dto.ElementTemplate;
import io.camunda.connector.dto.ElementTemplateFile;
import java.io.IOException;
import java.util.Iterator;
import java.util.Objects;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

public class ElementTemplateIterator implements Iterator<ElementTemplateFile> {

  private final RevCommit commit;
  private final ObjectMapper objectMapper;
  private final Repository repository;
  private final TreeWalk initialWalk;
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
      treeWalk.setFilter(PathFilter.create("connectors"));
      this.initialWalk = treeWalk;
      this.initialWalk.next();
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    this.elementTemplate = this.prepareNext();
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
            ObjectId objectId = this.currentWalk.getObjectId(0);
            ObjectLoader loader = this.repository.open(objectId);
            byte[] bytes = loader.getBytes();
            try {
              return new ElementTemplateFile(
                  objectMapper.readValue(bytes, ElementTemplate.class),
                  this.currentWalk.getPathString());
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
