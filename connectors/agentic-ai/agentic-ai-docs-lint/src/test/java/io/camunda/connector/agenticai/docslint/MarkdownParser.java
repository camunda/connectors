/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH
 * under one or more contributor license agreements. Licensed under a proprietary license.
 * See the License.txt file for more information. You may not use this file
 * except in compliance with the proprietary license.
 */
package io.camunda.connector.agenticai.docslint;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
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

  private static final Pattern HTML_ANCHOR = Pattern.compile("<a\\s+(?:id|name)=([\"'])(.+?)\\1");
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
        collector.anchors);
  }

  private static int line(final Node node) {
    return node.getSourceSpans().isEmpty()
        ? 0
        : node.getSourceSpans().getFirst().getLineIndex() + 1;
  }

  private static final class Collector extends AbstractVisitor {

    private final IdGenerator ids = IdGenerator.builder().build();
    private final List<Ref> headings = new ArrayList<>();
    private final List<Ref> links = new ArrayList<>();
    private final List<Ref> definitions = new ArrayList<>();
    private final List<Ref> codeSpans = new ArrayList<>();
    private final List<Ref> fences = new ArrayList<>();
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
      anchors.add(ids.generateId(text.toString()));
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
      codeSpans.add(new Ref(line(code), code.getLiteral().strip()));
    }

    @Override
    public void visit(final FencedCodeBlock block) {
      final String info = block.getInfo().strip();
      fences.add(new Ref(line(block), info.split("\\s+")[0]));
    }

    @Override
    public void visit(final HtmlBlock block) {
      addHtmlAnchors(block.getLiteral());
    }

    @Override
    public void visit(final HtmlInline html) {
      addHtmlAnchors(html.getLiteral());
    }

    private void addLink(final List<Ref> target, final Node node, final String destination) {
      if (!EXTERNAL.matcher(destination).matches()) {
        target.add(new Ref(line(node), destination));
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
