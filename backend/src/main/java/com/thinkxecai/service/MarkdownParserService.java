package com.thinkxecai.service;

import com.thinkxecai.model.MarkdownSection;

import java.util.List;

/**
 * Splits Markdown into sections: every top-level heading of any level starts one, and its content
 * runs until the next top-level heading. Headings with no text are skipped together with their
 * content, and text before the first heading belongs to no section.
 */
public interface MarkdownParserService {

    /** Sections in document order; empty when the document has no heading with text. */
    List<MarkdownSection> parse(String markdown);

}
