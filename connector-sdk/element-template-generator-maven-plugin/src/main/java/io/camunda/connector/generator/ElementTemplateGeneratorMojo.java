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
package io.camunda.connector.generator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.camunda.connector.generator.core.OutboundElementTemplateGenerator;
import io.camunda.connector.generator.dsl.OutboundElementTemplate;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

@Mojo(
    name = "generate-templates",
    defaultPhase = LifecyclePhase.PROCESS_CLASSES,
    requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME)
public class ElementTemplateGeneratorMojo extends AbstractMojo {

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(property = "connectorClasses", required = true)
  private String[] connectorClasses;

  @Parameter(property = "includeDependencies", required = false)
  private String[] includeDependencies;

  private static final ObjectMapper mapper = new ObjectMapper();
  private final OutboundElementTemplateGenerator generator = new OutboundElementTemplateGenerator();

  private static final String COMPILED_CLASSES_DIR = "target" + File.separator + "classes";
  private static final String ELEMENT_TEMPLATES_DIR = "element-templates";

  @Override
  public void execute() throws MojoFailureException {
    if (connectorClasses.length == 0) {
      getLog().warn("No connector classes specified. Skipping generation of element templates.");
      return;
    }

    List<URL> classpathUrls = new ArrayList<>();

    try {
      String compiledClassesPath =
          project.getFile().getParent() + File.separator + COMPILED_CLASSES_DIR;
      classpathUrls.add(new File(compiledClassesPath).toURI().toURL());

      for (String dependency : includeDependencies) {
        Artifact dependencyArtifact = (Artifact) project.getArtifactMap().get(dependency);
        if (dependencyArtifact == null) {
          throw new IllegalArgumentException(
              "Failed to find dependency " + dependency + " in project " + project.getName());
        }
        classpathUrls.add(dependencyArtifact.getFile().toURI().toURL());
      }
    } catch (Exception e) {
      throw new MojoFailureException("Failed to load classpath: " + e.getMessage(), e);
    }

    try (URLClassLoader classLoader =
        new URLClassLoader(
            classpathUrls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader())) {

      for (String className : connectorClasses) {
        getLog().info("Generating element template for " + className);
        Class<?> clazz = classLoader.loadClass(className);
        OutboundElementTemplate template = generateElementTemplate(clazz);
        writeElementTemplate(template);
      }

    } catch (ClassNotFoundException e) {
      throw new MojoFailureException("Failed to find connector class: " + e.getMessage(), e);
    } catch (TypeNotPresentException e) {
      throw new MojoFailureException(
          e.getMessage()
              + "\nIf your connector references other packages, include them using the 'includeDependencies' parameter",
          e);
    } catch (Exception e) {
      throw new MojoFailureException("Failed to generate element templates: " + e.getMessage(), e);
    }
  }

  private OutboundElementTemplate generateElementTemplate(Class<?> clazz) {
    return generator.generate(clazz);
  }

  private void writeElementTemplate(OutboundElementTemplate template) {
    try {
      String fileName = transformConnectorNameToTemplateFileName(template.name());
      File file =
          new File(
              project.getFile().getParent() + File.separator + ELEMENT_TEMPLATES_DIR, fileName);
      file.getParentFile().mkdirs();
      mapper.writerWithDefaultPrettyPrinter().writeValue(file, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write element template", e);
    }
  }

  private String transformConnectorNameToTemplateFileName(String connectorName) {
    // convert human-oriented name to kebab-case
    connectorName = connectorName.replaceAll(" ", "-");
    connectorName = connectorName.replaceAll("([a-z])([A-Z]+)", "$1-$2");
    connectorName = connectorName.toLowerCase();
    return connectorName + ".json";
  }
}
