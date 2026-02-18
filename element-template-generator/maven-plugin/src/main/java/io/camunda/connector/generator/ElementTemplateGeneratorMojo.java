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
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.camunda.connector.api.inbound.InboundConnectorExecutable;
import io.camunda.connector.api.outbound.OutboundConnectorFunction;
import io.camunda.connector.api.outbound.OutboundConnectorProvider;
import io.camunda.connector.generator.ConnectorConfig.FileNameById;
import io.camunda.connector.generator.api.DocsGenerator;
import io.camunda.connector.generator.api.DocsGeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration;
import io.camunda.connector.generator.api.GeneratorConfiguration.ConnectorMode;
import io.camunda.connector.generator.api.GeneratorConfiguration.GenerationFeature;
import io.camunda.connector.generator.dsl.ElementTemplate;
import io.camunda.connector.generator.java.ClassBasedDocsGenerator;
import io.camunda.connector.generator.java.ClassBasedTemplateGenerator;
import io.camunda.connector.generator.java.json.ElementTemplateModule;
import io.camunda.connector.generator.java.util.VersionedElementTemplate;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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

  private static final ObjectMapper objectMapper =
      new ObjectMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
  private static final ObjectWriter objectWriter =
      new ObjectMapper()
          .registerModule(new ElementTemplateModule())
          .writer(
              new DefaultPrettyPrinter()
                  .withObjectIndenter(new DefaultIndenter().withLinefeed("\n")));
  private static final String COMPILED_CLASSES_DIR = "target" + File.separator + "classes";
  private static final String HYBRID_TEMPLATES_DIR = "hybrid";

  @Parameter(defaultValue = "${project}", readonly = true, required = true)
  private MavenProject project;

  @Parameter(property = "connectorClasses", required = true)
  private ConnectorConfig[] connectors;

  @Parameter(property = "includeDependencies")
  private String[] includeDependencies;

  @Parameter(property = "outputDirectory", defaultValue = "${project.basedir}/element-templates")
  private String outputDirectory;

  @Parameter(
      property = "versionedDirectory",
      defaultValue = "${project.basedir}/element-templates/versioned")
  private String versionedDirectory;

  @Parameter(property = "versionHistoryEnabled", defaultValue = "false")
  private Boolean versionHistoryEnabled;

  private static VersionedElementTemplate getBasicElementTemplate(File file) {
    try {
      return objectMapper.readValue(file, VersionedElementTemplate.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

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

        if (!connector.getFiles().stream()
            .filter(fileNameById -> fileNameById.getDocTemplatePath() != null)
            .toList()
            .isEmpty()) {
          generateDocs(connector, classLoader);
        }
      }
      writeMetaInfFiles();
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

  private void writeMetaInfFiles() throws MojoFailureException {
    try {
      List<ConnectorConfig> filteredConnectors =
          Arrays.stream(connectors).filter(ConnectorConfig::isWriteMetaInfFileGeneration).toList();
      if (filteredConnectors.isEmpty()) {
        return;
      }
      Path path = Paths.get(getResourcesDirectory().toURI()).resolve("META-INF/services");
      Files.createDirectories(path);

      Map<Class<?>, String> connectorFileMap =
          Map.of(
              InboundConnectorExecutable.class,
              "io.camunda.connector.api.inbound.InboundConnectorExecutable",
              OutboundConnectorFunction.class,
              "io.camunda.connector.api.outbound.OutboundConnectorFunction",
              OutboundConnectorProvider.class,
              "io.camunda.connector.api.outbound.OutboundConnectorProvider");

      ClassLoader projectClassLoader = getProjectClassLoader();
      for (ConnectorConfig connector : filteredConnectors) {
        Class<?> connectorClass =
            Class.forName(connector.getConnectorClass(), false, projectClassLoader);

        String fileName =
            connectorFileMap.entrySet().stream()
                .filter(entry -> entry.getKey().isAssignableFrom(connectorClass))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow(
                    () ->
                        new IllegalArgumentException(
                            "Connector class must be either 'InboundConnectorExecutable' or 'OutboundConnectorFunction': "
                                + connector.getConnectorClass()));
        Path filePath = path.resolve(fileName);
        if (!Files.exists(filePath)) {
          Files.writeString(
              filePath,
              connector.getConnectorClass(),
              StandardOpenOption.CREATE,
              StandardOpenOption.TRUNCATE_EXISTING);
        }
      }
    } catch (IOException | ClassNotFoundException | URISyntaxException e) {
      throw new MojoFailureException(
          "Failed to create META-INF.service files: " + e.getMessage(), e);
    }
  }

  private ClassLoader getProjectClassLoader() throws MalformedURLException {
    List<URL> urls = new ArrayList<>();
    for (Object obj : project.getArtifacts()) {
      Artifact artifact = (Artifact) obj;
      urls.add(artifact.getFile().toURI().toURL());
    }
    return new URLClassLoader(
        urls.toArray(new URL[0]), Thread.currentThread().getContextClassLoader());
  }

  private void generateDocs(ConnectorConfig connectorConfig, ClassLoader classLoader)
      throws ClassNotFoundException {
    DocsGenerator generator = new ClassBasedDocsGenerator(classLoader);
    var clazz = classLoader.loadClass(connectorConfig.getConnectorClass());
    connectorConfig
        .getFiles()
        .forEach(
            fileNameById -> {
              var templatePath =
                  new File(project.getBasedir(), fileNameById.getDocTemplatePath())
                      .getAbsolutePath();
              var templateOutputPath =
                  new File(project.getBasedir(), fileNameById.getDocOutputPath()).getAbsolutePath();
              var config = new DocsGeneratorConfiguration(templatePath, templateOutputPath);

              var doc = generator.generate(clazz, config);

              try (PrintWriter out = new PrintWriter(config.outputPath())) {
                out.println(doc.content());
              } catch (FileNotFoundException e) {
                throw new RuntimeException(e);
              }
            });
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
    List<VersionedElementTemplate> versionedElementTemplates = retrieveBasicElementTemplates();
    for (var template : templates) {
      var fileName = determineFileName(template, fileNames, hybrid);
      if (versionHistoryEnabled && !hybrid)
        manageElementTemplatesVersioning(template, versionedElementTemplates, fileName);
      writeElementTemplate(template, hybrid, fileName);
    }
  }

  private void manageElementTemplatesVersioning(
      ElementTemplate template,
      List<VersionedElementTemplate> versionedElementTemplates,
      String fileName) {
    VersionedElementTemplate latestVersionedElementTemplate =
        new VersionedElementTemplate(template.id(), template.version());
    if (versionedElementTemplates.stream().noneMatch(latestVersionedElementTemplate::equals)) {
      copyLatestBasicElementTemplate(fileName);
    }
  }

  private void copyLatestBasicElementTemplate(String fileName) {
    File latestBasicElementTemplateFile = new File(this.outputDirectory, fileName);
    if (!latestBasicElementTemplateFile.exists()) {
      return;
    }
    try {
      VersionedElementTemplate latestVersionedElementTemplate =
          objectMapper.readValue(latestBasicElementTemplateFile, VersionedElementTemplate.class);

      if (Files.notExists(Path.of(this.versionedDirectory))) {
        Files.createDirectories(Path.of(this.versionedDirectory));
      }
      Files.copy(
          latestBasicElementTemplateFile.toPath(),
          Path.of(
              this.versionedDirectory
                  + File.separator
                  + fileName.replaceFirst(".json", "")
                  + "-"
                  + latestVersionedElementTemplate.version()
                  + ".json"));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private List<VersionedElementTemplate> retrieveBasicElementTemplates() {
    File latestTemplatesFolder = new File(this.outputDirectory);
    File versionedFolder = new File(this.versionedDirectory);
    Optional<File[]> listClassicFiles =
        Optional.ofNullable(
            latestTemplatesFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json")));
    Optional<File[]> listVersionedFiles =
        Optional.ofNullable(
            versionedFolder.listFiles((dir, name) -> name.toLowerCase().endsWith(".json")));
    return Stream.concat(
            Arrays.stream(listClassicFiles.orElse(new File[0])),
            Arrays.stream(listVersionedFiles.orElse(new File[0])))
        .filter(File::isFile)
        .map(ElementTemplateGeneratorMojo::getBasicElementTemplate)
        .toList();
  }

  private String determineFileName(
      ElementTemplate template, List<FileNameById> fileNames, boolean hybrid) {
    String expectedId =
        hybrid && template.id() != null
            ? template.id().replace(GeneratorConfiguration.HYBRID_TEMPLATE_ID_SUFFIX, "")
            : template.id();
    String fileName =
        fileNames.stream()
            .filter(f -> f.getTemplateId().equals(expectedId))
            .findFirst()
            .map(FileNameById::getTemplateFileName)
            .orElseGet(
                () -> {
                  getLog().warn("No file name specified for " + expectedId + ". Using default.");
                  return transformConnectorNameToTemplateFileName(template.name());
                });
    if (hybrid) {
      fileName = fileName.replace(".json", "-hybrid.json");
    }
    return fileName;
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
