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

import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.connector.generator.ConnectorConfig.FileNameById;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.GeneratorConfiguration.GenerationFeature;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.java.ClassBasedTemplateGenerator;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
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
  private ConnectorConfig[] connectors;

  @Parameter(property = "includeDependencies")
  private String[] includeDependencies;

  @Parameter(property = "outputDirectory", defaultValue = "${project.basedir}/element-templates")
  private String outputDirectory;

  private static final ObjectWriter objectWriter =
      new ObjectMapper()
          .writer(
              new DefaultPrettyPrinter()
                  .withObjectIndenter(new DefaultIndenter().withLinefeed("\n")));

  private static final String COMPILED_CLASSES_DIR = "target" + File.separator + "classes";
  private static final String HYBRID_TEMPLATES_DIR = "hybrid";

  @Override
  public void execute() throws MojoFailureException {
    if (connectors.length == 0) {
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

      for (ConnectorConfig connector : connectors) {
        getLog().info("Generating element template for " + connector.getConnectorClass());
        for (var file : connector.getFiles()) {
          if (!file.getTemplateFileName().endsWith(".json")) {
            throw new IllegalArgumentException(
                "File name must end with .json, but was " + file.getTemplateFileName());
          }
        }
        generateElementTemplates(connector, classLoader);
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

  private void generateElementTemplates(ConnectorConfig config, ClassLoader classLoader)
      throws ClassNotFoundException {
    var clazz = classLoader.loadClass(config.getConnectorClass());
    var features =
        config.getFeatures().entrySet().stream()
            .map(
                e -> {
                  try {
                    var feature = GenerationFeature.valueOf(e.getKey());
                    return Map.entry(feature, e.getValue());
                  } catch (IllegalArgumentException ex) {
                    throw new IllegalArgumentException(
                        "Unknown feature: "
                            + e.getKey()
                            + ". Known features are: "
                            + Arrays.toString(GenerationFeature.values()));
                  }
                })
            .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

    var generatorConfig =
        new GeneratorConfiguration(ConnectorMode.NORMAL, null, null, null, null, features);
    var generator = new ClassBasedTemplateGenerator(classLoader);
    var templates = generator.generate(clazz, generatorConfig);
    writeElementTemplates(templates, false, config.getFiles());

    if (config.isGenerateHybridTemplates()) {
      var hybridGeneratorConfig =
          new GeneratorConfiguration(ConnectorMode.HYBRID, null, null, null, null, features);
      var hybridTemplates = generator.generate(clazz, hybridGeneratorConfig);
      writeElementTemplates(hybridTemplates, true, config.getFiles());
    }
  }

  private void writeElementTemplates(
      List<ElementTemplate> templates, boolean hybrid, List<FileNameById> fileNames) {
    if (templates.size() == 1) {
      var fileName =
          fileNames.stream()
              .filter(f -> f.getTemplateId().equals(templates.getFirst().id()))
              .findFirst()
              .map(FileNameById::getTemplateFileName)
              .orElseGet(
                  () -> {
                    getLog()
                        .warn(
                            "No file name specified for "
                                + templates.getFirst().id()
                                + ". Using default.");
                    return transformConnectorNameToTemplateFileName(templates.getFirst().name());
                  });
      if (hybrid) {
        fileName = fileName.replace(".json", "-hybrid.json");
      }
      writeElementTemplate(templates.getFirst(), hybrid, fileName);
    } else {
      for (var template : templates) {
        var fileName =
            fileNames.stream()
                .filter(f -> f.getTemplateId().equals(template.id()))
                .findFirst()
                .map(FileNameById::getTemplateFileName)
                .orElseGet(
                    () -> {
                      getLog()
                          .warn("No file name specified for " + template.id() + ". Using default.");
                      return transformConnectorNameToTemplateFileName(template.name());
                    });
        if (hybrid) {
          fileName = fileName.replace(".json", "-hybrid.json");
        }
        writeElementTemplate(template, hybrid, fileName);
      }
    }
  }

  private void writeElementTemplate(ElementTemplate template, boolean hybrid, String fileName) {
    try {
      getLog().info("Writing element template to " + fileName);
      File file = new File(outputDirectory, fileName);
      file.getParentFile().mkdirs();
      if (hybrid) {
        file = new File(outputDirectory + File.separator + HYBRID_TEMPLATES_DIR, fileName);
        file.getParentFile().mkdirs();
      }
      objectWriter.writeValue(file, template);
    } catch (Exception e) {
      throw new RuntimeException("Failed to write element template", e);
    }
  }

  private String transformConnectorNameToTemplateFileName(String connectorName) {
    // convert human-oriented name to kebab-case
    connectorName = connectorName.replaceAll(" ", "-");
    connectorName = connectorName.replaceAll("([a-z])([A-Z]+)", "$1-$2");
    connectorName = connectorName.replaceAll("[^a-zA-Z0-9-]", "");
    connectorName = connectorName.toLowerCase();
    return connectorName + ".json";
  }

  private URL getResourcesDirectory() throws MalformedURLException {
    if (!project.getBuild().getResources().isEmpty()) {
      return Path.of(project.getBuild().getResources().get(0).getDirectory()).toUri().toURL();
    }
    return null;
  }

  private URL getTestResourcesDirectory() throws MalformedURLException {
    if (!project.getBuild().getTestResources().isEmpty()) {
      return Path.of(project.getBuild().getTestResources().get(0).getDirectory()).toUri().toURL();
    }
    return null;
  }
}
