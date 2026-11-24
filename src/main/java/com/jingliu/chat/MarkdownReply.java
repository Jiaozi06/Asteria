package com.jingliu.chat;

import android.content.Context;
import android.animation.ValueAnimator;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TableRow;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.node.*;

final class MarkdownReply extends LinearLayout {
    private final int ink, muted, blue, surface, edge, tint, inlineCode;
    private final boolean dark;
    private final float fontScale;
    private final Consumer<String> open, copy;
    private final IntConsumer citation;
    private List<String> visibleBlocks = new ArrayList<>();
    private String referenceDefinitions = "";

    MarkdownReply(Context context, int ink, int muted, int blue, int surface, int edge, int tint, boolean dark, float fontScale,
            Consumer<String> open, Consumer<String> copy, IntConsumer citation) {
        super(context);
        this.ink = ink;
        this.muted = muted;
        this.blue = blue;
        this.surface = surface;
        this.edge = edge;
        this.tint = tint;
        this.inlineCode = dark ? 0xffb9babc : 0xff4f535b;
        this.dark = dark;
        this.fontScale = fontScale;
        this.open = open;
        this.copy = copy;
        this.citation = citation;
        setOrientation(VERTICAL);
    }

    void setMarkdown(String source) {
        setMarkdown(source, false);
    }

    void setMarkdown(String source, boolean animate) {
        setVisibility(source.isEmpty() ? GONE : VISIBLE);
        Node document = MarkdownText.parse(source);
        List<Node> blocks = new ArrayList<>();
        List<String> keys = new ArrayList<>();
        for (Node node = document.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof LinkReferenceDefinition) continue;
            blocks.add(node);
            keys.add(blockKey(node, source));
        }
        StringBuilder references = new StringBuilder();
        collectDefinitions(document, references);
        String fingerprint = references.toString();
        int prefix = 0;
        if (referenceDefinitions.equals(fingerprint)) {
            int shared = Math.min(visibleBlocks.size(), keys.size());
            while (prefix < shared && visibleBlocks.get(prefix).equals(keys.get(prefix))) prefix++;
        }
        boolean fade = animate && ValueAnimator.areAnimatorsEnabled();
        List<List<StreamingText>> previous = new ArrayList<>();
        if (fade) for (int i = prefix; i < getChildCount(); i++) previous.add(streamingText(getChildAt(i)));
        if (getChildCount() > prefix) removeViews(prefix, getChildCount() - prefix);
        for (int i = prefix; i < blocks.size(); i++) {
            addBlock(this, blocks.get(i), i == 0 ? 0 : 11);
            if (fade) {
                List<StreamingText> fresh = streamingText(getChildAt(i));
                List<StreamingText> old = i - prefix < previous.size() ? previous.get(i - prefix) : java.util.Collections.emptyList();
                for (int index = 0; index < fresh.size(); index++) {
                    StreamingText view = fresh.get(index);
                    view.showNewText(index < old.size() ? old.get(index).getText() : "", view.getText());
                }
            }
        }
        visibleBlocks = keys;
        referenceDefinitions = fingerprint;
    }

    private List<StreamingText> streamingText(View view) {
        List<StreamingText> result = new ArrayList<>();
        collectStreamingText(view, result);
        return result;
    }

    private void collectStreamingText(View view, List<StreamingText> result) {
        if (view instanceof StreamingText) result.add((StreamingText) view);
        else if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) collectStreamingText(group.getChildAt(i), result);
        }
    }

    private static String blockKey(Node node, String source) {
        List<SourceSpan> spans = node.getSourceSpans();
        if (spans.isEmpty()) return source;
        SourceSpan last = spans.get(spans.size() - 1);
        return node.getClass().getName() + source.substring(spans.get(0).getInputIndex(), last.getInputIndex() + last.getLength());
    }

    private static void collectDefinitions(Node parent, StringBuilder into) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) {
            if (node instanceof LinkReferenceDefinition) {
                LinkReferenceDefinition definition = (LinkReferenceDefinition) node;
                into.append(definition.getLabel()).append('\n').append(definition.getDestination())
                    .append('\n').append(definition.getTitle()).append('\n');
            } else if (node instanceof Block) collectDefinitions(node, into);
        }
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout box = new LinearLayout(getContext());
        box.setOrientation(VERTICAL);
        return box;
    }

    private TextView text(CharSequence value, int size, int color) {
        return prepareText(new TextView(getContext()), value, size, color);
    }

    private TextView prepareText(TextView view, CharSequence value, int size, int color) {
        view.setText(value);
        ChatFont.apply(view, size, fontScale);
        view.setTextColor(color);
        return view;
    }

    private TextView prose(Node node, int size) {
        TextView view = prepareText(new StreamingText(getContext()),
            MarkdownText.renderCitations(node, muted, inlineCode, tint, open, citation), size, ink);
        view.setTextIsSelectable(true);
        view.setLinkTextColor(blue);
        view.setMovementMethod(LinkMovementMethod.getInstance());
        view.setLineSpacing(dp(2), 1.1f);
        return view;
    }

    private GradientDrawable background(int color, int border, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        if (border != 0) shape.setStroke(dp(1), border);
        return shape;
    }

    private void addBlock(LinearLayout parent, Node node, int topSpace) {
        if (node instanceof LinkReferenceDefinition || node instanceof TaskListItemMarker) return;
        View view = block(node);
        LayoutParams frame = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        frame.topMargin = dp(topSpace);
        parent.addView(view, frame);
    }

    private View block(Node node) {
        if (node instanceof FencedCodeBlock) {
            FencedCodeBlock code = (FencedCodeBlock) node;
            String label = code.getInfo() == null ? "" : code.getInfo().trim().split("\\s+", 2)[0];
            return codeCard(label, code.getLiteral());
        }
        if (node instanceof IndentedCodeBlock) return codeCard("", ((IndentedCodeBlock) node).getLiteral());
        if (node instanceof TableBlock) return table((TableBlock) node);
        if (node instanceof ListBlock) return list((ListBlock) node);
        if (node instanceof BlockQuote) return quote((BlockQuote) node);
        if (node instanceof ThematicBreak) return divider();
        return prose(node, 16);
    }

    private View divider() {
        View line = new View(getContext());
        line.setBackgroundColor(edge);
        line.setMinimumHeight(dp(1));
        return line;
    }

    private View codeCard(String language, String literal) {
        LinearLayout card = column();
        card.setBackground(background(surface, edge, 14));
        card.setClipToOutline(true);
        LinearLayout header = new LinearLayout(getContext());
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), 0, dp(6), 0);
        TextView kind = text(language.isEmpty() ? "代码" : language, 12, muted);
        kind.setSingleLine();
        kind.setEllipsize(TextUtils.TruncateAt.END);
        header.addView(kind, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        TextView copyButton = text("复制", 13, blue);
        copyButton.setContentDescription("复制代码");
        copyButton.setGravity(Gravity.CENTER);
        copyButton.setMinHeight(dp(38));
        copyButton.setPadding(dp(8), 0, dp(8), 0);
        Drawable copyIcon = getContext().getDrawable(R.drawable.ic_copy);
        copyIcon.setBounds(0, 0, dp(15), dp(15));
        copyButton.setCompoundDrawablesRelative(copyIcon, null, null, null);
        copyButton.setCompoundDrawableTintList(ColorStateList.valueOf(blue));
        copyButton.setCompoundDrawablePadding(dp(4));
        copyButton.setBackground(new RippleDrawable(ColorStateList.valueOf(dark ? 0x28aecaff : 0x203268df),
            null, background(0xffffffff, 0, 9)));
        copyButton.setOnClickListener(view -> copy.accept(literal));
        header.addView(copyButton, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        card.addView(header, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        card.addView(divider(), LayoutParams.MATCH_PARENT, dp(1));

        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setHorizontalScrollBarEnabled(true);
        String show = literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
        TextView code = prepareText(new StreamingText(getContext()), CodeHighlight.render(show, language, dark), 14, ink);
        code.setTypeface(Typeface.MONOSPACE);
        code.setHorizontallyScrolling(true);
        code.setTextIsSelectable(true);
        code.setPadding(dp(12), dp(10), dp(12), dp(11));
        code.setLineSpacing(dp(2), 1.08f);
        scroll.addView(code, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        card.addView(scroll, LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        return card;
    }

    private View quote(BlockQuote quote) {
        LinearLayout wrap = new LinearLayout(getContext());
        wrap.setGravity(Gravity.TOP);
        View rail = new View(getContext());
        rail.setBackground(background(dark ? 0xff506587 : 0xffb7c9ec, 0, 2));
        LayoutParams rule = new LayoutParams(dp(3), LayoutParams.MATCH_PARENT);
        rule.setMarginEnd(dp(10));
        wrap.addView(rail, rule);
        LinearLayout body = column();
        renderChildren(body, quote, 7);
        wrap.addView(body, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        return wrap;
    }

    private void renderChildren(LinearLayout parent, Node container, int gap) {
        boolean first = true;
        for (Node child = container.getFirstChild(); child != null; child = child.getNext()) {
            if (child instanceof LinkReferenceDefinition || child instanceof TaskListItemMarker) continue;
            addBlock(parent, child, first ? 0 : gap);
            first = false;
        }
    }

    private View list(ListBlock list) {
        LinearLayout items = column();
        int number = list instanceof OrderedList ? ((OrderedList) list).getMarkerStartNumber() : 0;
        int last = number;
        if (list instanceof OrderedList) {
            for (Node child = list.getFirstChild(); child != null; child = child.getNext()) last++;
        }
        int markerWidth = Math.max(22, (String.valueOf(last).length() + 1) * 9);
        for (Node child = list.getFirstChild(); child != null; child = child.getNext()) {
            LinearLayout row = new LinearLayout(getContext());
            row.setGravity(Gravity.TOP);
            Node first = child.getFirstChild();
            boolean task = first instanceof TaskListItemMarker;
            boolean checked = task && ((TaskListItemMarker) first).isChecked();
            String label = task ? (checked ? "☑" : "☐") : list instanceof OrderedList ? number + "." : "•";
            number++;
            TextView marker = text(label, 15, task && checked ? blue : muted);
            if (task) marker.setContentDescription(checked ? "已完成" : "未完成");
            row.addView(marker, dp(markerWidth), LayoutParams.WRAP_CONTENT);
            LinearLayout body = column();
            renderChildren(body, child, list.isTight() ? 4 : 8);
            row.addView(body, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
            LayoutParams spacing = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            if (items.getChildCount() > 0) spacing.topMargin = dp(list.isTight() ? 3 : 8);
            items.addView(row, spacing);
        }
        return items;
    }

    private View table(TableBlock block) {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setFillViewport(true);
        scroll.setBackground(background(edge, edge, 10));
        scroll.setClipToOutline(true);
        TableLayout table = new TableLayout(getContext());
        table.setPadding(dp(1), dp(1), 0, 0);
        for (Node section = block.getFirstChild(); section != null; section = section.getNext()) {
            for (Node line = section.getFirstChild(); line != null; line = line.getNext()) {
                TableRow row = new TableRow(getContext());
                for (Node item = line.getFirstChild(); item != null; item = item.getNext()) {
                    if (!(item instanceof TableCell)) continue;
                    TableCell cell = (TableCell) item;
                    TextView value = prose(cell, 14);
                    value.setMinWidth(dp(72));
                    value.setMaxWidth(dp(230));
                    value.setPadding(dp(10), dp(7), dp(10), dp(7));
                    value.setBackgroundColor(cell.isHeader() ? tint : surface);
                    int alignment = cell.getAlignment() == TableCell.Alignment.RIGHT ? Gravity.END
                        : cell.getAlignment() == TableCell.Alignment.CENTER ? Gravity.CENTER_HORIZONTAL : Gravity.START;
                    value.setGravity(alignment | Gravity.TOP);
                    TableRow.LayoutParams spacing = new TableRow.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT);
                    spacing.rightMargin = dp(1);
                    spacing.bottomMargin = dp(1);
                    row.addView(value, spacing);
                }
                table.addView(row, new TableLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
        }
        scroll.addView(table, LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        return scroll;
    }
}
