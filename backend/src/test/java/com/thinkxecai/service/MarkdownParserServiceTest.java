package com.thinkxecai.service;

import com.thinkxecai.model.MarkdownSection;
import com.thinkxecai.service.impl.MarkdownParserServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MarkdownParserServiceTest {

    private final MarkdownParserService parser = new MarkdownParserServiceImpl();

    @Test
    void keepsTheSectionSourceExactlyAsWritten() {
        String body = """
                Run `mvn test` and see [docs](https://example.com/docs).

                - **one**
                - two

                | Name | Port |
                |------|------|
                | db   | 5433 |
                | api  | 8080 |

                ```java
                int x = 1;
                ```""";

        List<MarkdownSection> sections = parser.parse("# Setup\n\n" + body + "\n\n## Next\ntext\n");

        assertThat(sections).containsExactly(
                new MarkdownSection("Setup", body),
                new MarkdownSection("Next", "text"));
    }

    @Test
    void everyHeadingLevelStartsASectionThatEndsAtTheNextHeading() {
        List<MarkdownSection> sections = parser.parse("""
                # A
                a
                ### C
                c
                ## B
                b
                """);

        assertThat(sections).containsExactly(
                new MarkdownSection("A", "a"),
                new MarkdownSection("C", "c"),
                new MarkdownSection("B", "b"));
    }

    @Test
    void titlesArePlainTextWithoutInlineMarkup() {
        List<MarkdownSection> sections = parser.parse("## **Bold** [link](https://example.com) `code` #\nx");

        assertThat(sections).extracting(MarkdownSection::title).containsExactly("Bold link code");
    }

    @Test
    void setextHeadingsStartSections() {
        List<MarkdownSection> sections = parser.parse("Title\n=====\nbody\n\nSub\n---\nmore");

        assertThat(sections).containsExactly(
                new MarkdownSection("Title", "body"),
                new MarkdownSection("Sub", "more"));
    }

    @Test
    void headingsWithoutTextAreSkippedTogetherWithTheirContent() {
        List<MarkdownSection> sections = parser.parse("# A\na\n#\norphan\n##   \nalso orphan\n# B\nb");

        assertThat(sections).containsExactly(
                new MarkdownSection("A", "a"),
                new MarkdownSection("B", "b"));
    }

    @Test
    void aHeadingWithoutContentHasEmptyContent() {
        assertThat(parser.parse("# A\n\n# B")).containsExactly(
                new MarkdownSection("A", ""),
                new MarkdownSection("B", ""));
    }

    @Test
    void textBeforeTheFirstHeadingBelongsToNoSection() {
        assertThat(parser.parse("intro\n\n# A\na")).containsExactly(new MarkdownSection("A", "a"));
    }

    @Test
    void aDocumentWithoutHeadingsHasNoSections() {
        assertThat(parser.parse("just text\n\n- and a list")).isEmpty();
        assertThat(parser.parse("")).isEmpty();
    }

    @Test
    void headingSyntaxInsideCodeBlocksAndQuotesIsContentNotABoundary() {
        String body = "```\n# not a heading\n```\n\n> # quoted heading";

        assertThat(parser.parse("# A\n" + body + "\n# B\nb")).containsExactly(
                new MarkdownSection("A", body),
                new MarkdownSection("B", "b"));
    }

    @Test
    void windowsAndOldMacLineEndingsBecomeLineFeeds() {
        assertThat(parser.parse("# A\r\nline 1\r\nline 2\r\n# B\rx\ry")).containsExactly(
                new MarkdownSection("A", "line 1\nline 2"),
                new MarkdownSection("B", "x\ny"));
    }

    @Test
    void aByteOrderMarkDoesNotHideTheFirstHeading() {
        assertThat(parser.parse("﻿# A\na")).containsExactly(new MarkdownSection("A", "a"));
    }

}
