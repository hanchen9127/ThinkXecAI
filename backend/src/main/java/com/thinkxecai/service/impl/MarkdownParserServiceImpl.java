package com.thinkxecai.service.impl;

import com.thinkxecai.model.MarkdownSection;
import com.thinkxecai.service.MarkdownParserService;
import com.vladsch.flexmark.ast.Heading;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.ast.TextCollectingVisitor;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Section content is cut from the source text by node offsets, so tables, code blocks, links and
 * lists reach the model exactly as written (ADR 023). Stateless: the flexmark parser is immutable
 * and each call works only on its own text.
 */
@Service
public class MarkdownParserServiceImpl implements MarkdownParserService {

    private static final char BYTE_ORDER_MARK = '﻿';

    private final Parser parser = Parser.builder(new MutableDataSet()).build();

    @Override
    public List<MarkdownSection> parse(String markdown) {
        String text = normalize(markdown);
        List<Heading> headings = new ArrayList<>();
        for (Node node = parser.parse(text).getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof Heading heading) {
                headings.add(heading);
            }
        }

        List<MarkdownSection> sections = new ArrayList<>();
        for (int i = 0; i < headings.size(); i++) {
            Heading heading = headings.get(i);
            String title = new TextCollectingVisitor().collectAndGetText(heading).strip();
            if (title.isEmpty()) {
                continue;
            }
            int contentEnd = i + 1 < headings.size() ? headings.get(i + 1).getStartOffset() : text.length();
            String content = text.substring(heading.getEndOffset(), contentEnd).strip();
            sections.add(new MarkdownSection(title, content));
        }
        return sections;
    }

    /** One line-ending style in stored content, and no byte order mark hiding a heading on the first line. */
    private static String normalize(String markdown) {
        String text = markdown.replace("\r\n", "\n").replace('\r', '\n');
        return !text.isEmpty() && text.charAt(0) == BYTE_ORDER_MARK ? text.substring(1) : text;
    }

}
