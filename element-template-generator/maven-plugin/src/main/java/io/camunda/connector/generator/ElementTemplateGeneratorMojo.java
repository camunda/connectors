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
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.dsl.OutboundElementTemplate;
import io.camunda.connector.generator.java.OutboundClassBasedTemplateGenerator;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
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

  @Parameter(property = "includeDependencies")
  private String[] includeDependencies;

  @Parameter(property = "outputDirectory", defaultValue = "${project.basedir}/element-templates")
  private String outputDirectory;

  @Parameter(property = "templateFileName")
  private String templateFileName;

  @Parameter(property = "generateHybridTemplates", defaultValue = "false")
  private boolean generateHybridTemplates;

  private static final ObjectMapper mapper = new ObjectMapper();
  private OutboundClassBasedTemplateGenerator generator;

  private static final String COMPILED_CLASSES_DIR = "target" + File.separator + "classes";

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

      var resourcesDirectory = getResourcesDirectory();
      var testResourcesDirectory = getTestResourcesDirectory();
      if (resourcesDirectory != null) {
        classpathUrls.add(resourcesDirectory);
      }
      if (testResourcesDirectory != null) {
        classpathUrls.add(testResourcesDirectory);
      }

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

      // ensures that resources and classes from the project are loaded by the classloader
      Thread.currentThread().setContextClassLoader(classLoader);
      generator = new OutboundClassBasedTemplateGenerator();

      for (String className : connectorClasses) {
        getLog().info("Generating element template for " + className);
        Class<?> clazz = classLoader.loadClass(className);
        var templates = generator.generate(clazz);
        writeElementTemplates(templates);

        if (generateHybridTemplates) {
          getLog().info("Generating hybrid element template for " + className);
          var hybridTemplates =
              generator.generate(
                  clazz, new GeneratorConfiguration(ConnectorMode.HYBRID, null, null, null, null));
          writeElementTemplates(hybridTemplates);
        }
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

  private void writeElementTemplates(List<OutboundElementTemplate> templates) {
    if (templates.size() == 1) {
      var fileName =
          templateFileName == null
              ? transformConnectorNameToTemplateFileName(templates.get(0).name())
              : templateFileName + ".json";
      writeElementTemplate(templates.get(0), fileName);
    } else {
      for (var template : templates) {
        var fileName =
            templateFileName == null
                ? transformConnectorNameToTemplateFileName(template.name())
                : templateFileName + "-" + template.elementType() + ".json";

        writeElementTemplate(template, fileName);
      }
    }
  }

  private void writeElementTemplate(OutboundElementTemplate template, String fileName) {
    try {
      File file = new File(outputDirectory, fileName);
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

  private URL getResourcesDirectory() throws MalformedURLException {
    if (project.getBuild().getResources().size() > 0) {
      return Path.of(project.getBuild().getResources().get(0).getDirectory()).toUri().toURL();
    }
    return null;
  }

  private URL getTestResourcesDirectory() throws MalformedURLException {
    if (project.getBuild().getTestResources().size() > 0) {
      return Path.of(project.getBuild().getTestResources().get(0).getDirectory()).toUri().toURL();
    }
    return null;
  }
}
