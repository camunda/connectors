/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.heading.anchor.IdGenerator;
import org.commonmark.node.AbstractVisitor;
import org.commonmark.node.Code;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.Node;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.Text;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Reads a Markdown document with CommonMark. */
final class MarkdownParser {

  private static final Parser PARSER =
      Parser.builder()
          .extensions(List.of(YamlFrontMatterExtension.create()))
          .includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
          .build();

  private static final Pattern HTML_ANCHOR =
      Pattern.compile("<a\\b[^>]*?\\s(?:id|name)=([\"'])(.+?)\\1");
  private static final Pattern ALLOW_NAMES =
      Pattern.compile("<!--\\s*docs-lint:\\s*allow-names\\s+([^:>]*?)\\s*(?::[^>]*)?-->");
  private static final Pattern HISTORY =
      Pattern.compile("<!--\\s*docs-lint:\\s*history\\s*(?::[^>]*)?-->");
  private static final Pattern ALLOW_JAVA_BLOCK =
      Pattern.compile("<!--\\s*docs-lint:\\s*allow-java-block\\s*(?::[^>]*)?-->");
  private static final Pattern COMMA = Pattern.compile(",");
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");
  private static final Pattern EXTERNAL = Pattern.compile("^(?:[a-zA-Z][a-zA-Z0-9+.-]*:|//).*");

  private MarkdownParser() {}

  static Doc parse(final Path path, final String markdown) {
    final Collector collector = new Collector();
    PARSER.parse(markdown).accept(collector);
    return new Doc(
        path,
        collector.headings,
        collector.links,
        collector.definitions,
        collector.codeSpans,
        collector.fences,
        collector.allowedNames,
        collector.history,
        collector.anchors);
  }

  private static int line(final Node node) {
    return node.getSourceSpans().isEmpty()
        ? 0
        : node.getSourceSpans().getFirst().getLineIndex() + 1;
  }

  private static final class Collector extends AbstractVisitor {

    private final Map<String, Integer> headingIds = new HashMap<>();
    private final List<Ref> headings = new ArrayList<>();
    private final List<Ref> links = new ArrayList<>();
    private final List<Ref> definitions = new ArrayList<>();
    private final List<CodeSpan> codeSpans = new ArrayList<>();
    private final List<Ref> fences = new ArrayList<>();
    private final List<Ref> allowedNames = new ArrayList<>();
    private boolean history;
    private final Set<Node> allowedJavaBlocks = new HashSet<>();
    private final Set<String> anchors = new LinkedHashSet<>();

    @Override
    public void visit(final Heading heading) {
      final StringBuilder text = new StringBuilder();
      heading.accept(
          new AbstractVisitor() {
            @Override
            public void visit(final Text node) {
              text.append(node.getLiteral());
            }

            @Override
            public void visit(final Code node) {
              text.append(node.getLiteral());
            }

            @Override
            public void visit(final SoftLineBreak node) {
              text.append(' ');
            }
          });
      headings.add(
          new Ref(line(heading), "#".repeat(heading.getLevel()) + " " + text.toString().strip()));
      anchors.add(headingId(text.toString()));
      visitChildren(heading);
    }

    @Override
    public void visit(final Link link) {
      addLink(links, link, link.getDestination());
      visitChildren(link);
    }

    @Override
    public void visit(final Image image) {
      addLink(links, image, image.getDestination());
      visitChildren(image);
    }

    @Override
    public void visit(final LinkReferenceDefinition definition) {
      if (!definition.getLabel().startsWith("^")) {
        addLink(definitions, definition, definition.getDestination());
      }
    }

    @Override
    public void visit(final Code code) {
      codeSpans.add(new CodeSpan(line(code), code.getLiteral().strip()));
    }

    @Override
    public void visit(final FencedCodeBlock block) {
      if (allowedJavaBlocks.contains(block)) {
        return;
      }
      final String info = block.getInfo().strip();
      fences.add(new Ref(line(block), WHITESPACE.splitAsStream(info).findFirst().orElse("")));
    }

    @Override
    public void visit(final HtmlBlock block) {
      addHtmlAnchors(block.getLiteral());
      addMarkers(block, block.getLiteral());
    }

    @Override
    public void visit(final HtmlInline html) {
      addHtmlAnchors(html.getLiteral());
      addMarkers(html, html.getLiteral());
    }

    private String headingId(final String text) {
      final String base = IdGenerator.builder().build().generateId(text);
      String id = base;
      while (headingIds.containsKey(id)) {
        id = base + "-" + headingIds.merge(base, 1, Integer::sum);
      }
      headingIds.put(id, 0);
      return id;
    }

    private void addLink(final List<Ref> target, final Node node, final String destination) {
      if (!EXTERNAL.matcher(destination).matches()) {
        target.add(new Ref(line(node), destination));
      }
    }

    private void addMarkers(final Node node, final String html) {
      final Matcher names = ALLOW_NAMES.matcher(html);
      while (names.find()) {
        COMMA
            .splitAsStream(names.group(1))
            .map(String::strip)
            .filter(name -> !name.isEmpty())
            .forEach(name -> allowedNames.add(new Ref(line(node), name)));
      }
      history |= HISTORY.matcher(html).find();
      if (ALLOW_JAVA_BLOCK.matcher(html).find() && node.getNext() != null) {
        allowedJavaBlocks.add(node.getNext());
      }
    }

    private void addHtmlAnchors(final String html) {
      final Matcher matcher = HTML_ANCHOR.matcher(html);
      while (matcher.find()) {
        anchors.add(matcher.group(2));
      }
    }
  }
}
