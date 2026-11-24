package com.jingliu.chat;

import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

final class CodeHighlight {
    private enum Language {
        PYTHON("and as assert async await break class continue def del elif else except False finally for from global if import in is lambda None nonlocal not or pass raise return True try while with yield"),
        SHELL("case do done elif else esac export fi for function if in local readonly return select set then trap until while"),
        BRACE("as async await break case catch class const continue data default do else enum export extends false final finally for from fun goto if implements import in interface internal let new null object override package private protected public return static struct super switch this throw throws true try type val var void when while yield"),
        JSON("true false null"),
        SQL("alter and as asc between by case create delete desc distinct drop else end exists false from group having in insert into is join left like limit not null offset on or order right select set table then true union update values when where with");

        final Set<String> keywords;

        Language(String keywords) {
            this.keywords = new HashSet<>(Arrays.asList(keywords.split(" ")));
        }
    }

    static CharSequence render(String code, String language, boolean dark) {
        Language mode = language(language);
        if (mode == null) return code;
        SpannableString result = new SpannableString(code);
        int comment = dark ? 0xffaab8c9 : 0xff5d6a7c;
        int literal = dark ? 0xffb4d99b : 0xff226440;
        int keyword = dark ? 0xffd7acf3 : 0xff6738a7;
        int value = dark ? 0xffefc881 : 0xff8b4a24;
        int limit = Math.min(code.length(), 80_000);
        for (int i = 0; i < limit;) {
            char current = code.charAt(i);
            int end = i + 1;
            int color = 0;
            if ((current == '#' && (mode == Language.PYTHON || mode == Language.SHELL))
                    || (mode == Language.BRACE && code.startsWith("//", i))
                    || (mode == Language.SQL && code.startsWith("--", i))) {
                int nextLine = code.indexOf('\n', end);
                end = nextLine == -1 ? limit : Math.min(nextLine, limit);
                color = comment;
            } else if ((mode == Language.BRACE || mode == Language.SQL) && code.startsWith("/*", i)) {
                int close = code.indexOf("*/", i + 2);
                end = close < 0 ? limit : Math.min(close + 2, limit);
                color = comment;
            } else if (current == '\'' || current == '"' || (current == '\u0060' && mode == Language.BRACE)) {
                end = stringEnd(code, i, limit, mode);
                color = literal;
            } else if (current >= '0' && current <= '9') {
                while (end < limit) {
                    char next = code.charAt(end);
                    if (!Character.isLetterOrDigit(next) && next != '.' && next != '_') break;
                    end++;
                }
                color = value;
            } else if (Character.isJavaIdentifierStart(current)) {
                while (end < limit && Character.isJavaIdentifierPart(code.charAt(end))) end++;
                String word = code.substring(i, end);
                if (mode == Language.SQL) word = word.toLowerCase(Locale.ROOT);
                if (mode.keywords.contains(word)) color = keyword;
                else if (mode == Language.PYTHON || mode == Language.BRACE) {
                    int next = end;
                    while (next < limit && (code.charAt(next) == ' ' || code.charAt(next) == '\t')) next++;
                    if (next < limit && code.charAt(next) == '(') color = value;
                }
            }
            if (color != 0) result.setSpan(new ForegroundColorSpan(color), i, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            i = end;
        }
        return result;
    }

    private static int stringEnd(String code, int start, int limit, Language mode) {
        char quote = code.charAt(start);
        boolean triple = mode == Language.PYTHON && start + 2 < limit
            && code.charAt(start + 1) == quote && code.charAt(start + 2) == quote;
        int end = start + (triple ? 3 : 1);
        while (end < limit) {
            char c = code.charAt(end++);
            if (c == '\\' && end < limit) end++;
            else if (c == quote) {
                if (!triple) return end;
                if (end + 1 < limit && code.charAt(end) == quote && code.charAt(end + 1) == quote) return end + 2;
            } else if (c == '\n' && !triple && quote != '\u0060') return end - 1;
        }
        return end;
    }

    private static Language language(String input) {
        switch (input.toLowerCase(Locale.ROOT)) {
            case "py": case "py3": case "python": case "python3": return Language.PYTHON;
            case "sh": case "shell": case "bash": case "zsh": case "shellscript": return Language.SHELL;
            case "js": case "javascript": case "jsx": case "ts": case "typescript": case "tsx":
            case "c": case "cpp": case "c++": case "csharp": case "cs": case "java": case "kotlin": case "kt":
            case "go": case "rust": case "rs": case "swift": case "php": return Language.BRACE;
            case "json": case "jsonc": return Language.JSON;
            case "sql": case "postgresql": return Language.SQL;
            default: return null;
        }
    }
}
