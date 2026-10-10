package com.thinkxecai.model;

/**
 * One section of a Markdown document: the heading as plain text (embedded for search) and the
 * Markdown source between this heading and the next one ("" when there is none).
 */
public record MarkdownSection(String title, String content) {
}
