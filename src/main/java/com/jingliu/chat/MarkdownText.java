package com.jingliu.chat;

import android.graphics.Typeface;
import android.net.Uri;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextPaint;
import android.text.style.*;
import android.view.View;
import java.util.Arrays;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.regex.Matcher;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.*;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.*;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

final class MarkdownText {
    private static final Parser PARSER = Parser.builder()
        .extensions(Arrays.asList(TablesExtension.create(), StrikethroughExtension.create(),
            TaskListItemsExtension.create(), AutolinkExtension.create()))
        .includeSourceSpans(IncludeSourceSpans.BLOCKS).maxOpenBlockParsers(32).build();

    static Node parse(String source) { return PARSER.parse(source); }

    static String forSpeech(String source) {
        return render(parse(source), 0, 0, ignored -> {}).toString();
    }

    private static CharSequence render(Node node, int muted, int inlineCode, Consumer<String> open) {
        MarkdownText renderer = new MarkdownText(muted, inlineCode, open);
        renderer.append(node, 0);
        int end = renderer.text.length();
        while (end > 0 && renderer.text.charAt(end - 1) == '\n') end--;
        if (end < renderer.text.length()) renderer.text.delete(end, renderer.text.length());
        return renderer.text;
    }

    static CharSequence renderCitations(Node node, int muted, int inlineCode, int tint,
            Consumer<String> open, IntConsumer showCitation) {
        CharSequence rendered = render(node, muted, inlineCode, open);
        Matcher matches = WebSources.CITATION.matcher(rendered.toString());
        if (!matches.find()) return rendered;
        SpannableStringBuilder text = new SpannableStringBuilder(rendered);
        int offset = 0;
        do {
            int id = Integer.parseInt(matches.group(1));
            int start = matches.start() + offset;
            int end = matches.end() + offset;
            String badge = " " + id + " ";
            text.replace(start, end, badge);
            offset += badge.length() - (end - start);
            int stop = start + badge.length();
            text.setSpan(new ClickableSpan() {
                @Override public void onClick(View view) { showCitation.accept(id); }
                @Override public void updateDrawState(TextPaint paint) {
                    paint.setColor(paint.linkColor);
                    paint.setUnderlineText(false);
                }
            }, start, stop, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new RelativeSizeSpan(.76f), start, stop, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            text.setSpan(new BackgroundColorSpan(tint), start, stop, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        } while (matches.find());
        return text;
    }

    private final SpannableStringBuilder text = new SpannableStringBuilder();
    private final int muted;
    private final int inlineCode;
    private final Consumer<String> open;

    private MarkdownText(int muted, int inlineCode, Consumer<String> open) {
        this.muted = muted;
        this.inlineCode = inlineCode;
        this.open = open;
    }

    private void children(Node node, int depth) {
        if (depth > 64) return;
        for (Node child = node.getFirstChild(); child != null; child = child.getNext()) append(child, depth + 1);
    }

    private void span(Object style, int start) {
        if (text.length() > start) text.setSpan(style, start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private void append(Node node, int depth) {
        int start = text.length();
        if (node instanceof Text) text.append(((Text) node).getLiteral());
        else if (node instanceof SoftLineBreak || node instanceof HardLineBreak) text.append('\n');
        else if (node instanceof HtmlInline) text.append(((HtmlInline) node).getLiteral());
        else if (node instanceof HtmlBlock) text.append(((HtmlBlock) node).getLiteral()).append('\n');
        else if (node instanceof Code) code(((Code) node).getLiteral(), false);
        else if (node instanceof FencedCodeBlock) code(((FencedCodeBlock) node).getLiteral(), true);
        else if (node instanceof IndentedCodeBlock) code(((IndentedCodeBlock) node).getLiteral(), true);
        else if (node instanceof ThematicBreak) text.append("────────\n\n");
        else if (node instanceof Heading) {
            children(node, depth);
            span(new RelativeSizeSpan(((Heading) node).getLevel() < 3 ? 1.24f : 1.12f), start);
            span(new StyleSpan(Typeface.BOLD), start);
            text.append("\n\n");
        } else if (node instanceof StrongEmphasis || node instanceof Emphasis) {
            children(node, depth);
            span(new StyleSpan(node instanceof StrongEmphasis ? Typeface.BOLD : Typeface.ITALIC), start);
        } else if (node instanceof Strikethrough) {
            children(node, depth);
            span(new StrikethroughSpan(), start);
        } else if (node instanceof Link || node instanceof Image) {
            children(node, depth);
            String url = node instanceof Link ? ((Link) node).getDestination() : ((Image) node).getDestination();
            if (isWebLink(url)) span(new ClickableSpan() {
                @Override public void onClick(View view) { open.accept(url); }
            }, start);
        } else if (node instanceof Paragraph) {
            children(node, depth);
            text.append(node.getParent() instanceof ListItem ? "\n" : "\n\n");
        } else if (node instanceof TaskListItemMarker) {
            text.append(((TaskListItemMarker) node).isChecked() ? "☑ " : "☐ ");
        } else if (node instanceof ListBlock) {
            int number = node instanceof OrderedList ? ((OrderedList) node).getMarkerStartNumber() : 0;
            for (Node child = node.getFirstChild(); child != null; child = child.getNext()) {
                if (!(child.getFirstChild() instanceof TaskListItemMarker)) {
                    text.append(node instanceof OrderedList ? number + ". " : "• ");
                }
                number++;
                children(child, depth + 1);
            }
            if (!(node.getParent() instanceof ListItem)) text.append('\n');
        } else if (node instanceof BlockQuote) {
            children(node, depth);
            span(new QuoteSpan(muted), start);
        } else if (node instanceof TableRow) {
            for (Node cell = node.getFirstChild(); cell != null; cell = cell.getNext()) {
                if (cell != node.getFirstChild()) text.append("  │  ");
                append(cell, depth + 1);
            }
            text.append('\n');
        } else if (node instanceof TableCell) {
            children(node, depth);
            if (((TableCell) node).isHeader()) span(new StyleSpan(Typeface.BOLD), start);
        } else {
            children(node, depth);
            if (node instanceof TableBlock) text.append('\n');
        }
    }

    private void code(String content, boolean block) {
        int start = text.length();
        text.append(block ? content.replaceFirst("\\s+$", "") : content);
        span(new TypefaceSpan("monospace"), start);
        if (block) {
            text.append("\n\n");
        } else {
            span(new ForegroundColorSpan(inlineCode), start);
            span(new RelativeSizeSpan(.94f), start);
        }
    }

    static boolean isWebLink(String target) {
        String scheme = Uri.parse(target).getScheme();
        return "https".equalsIgnoreCase(scheme) || "http".equalsIgnoreCase(scheme);
    }
}
