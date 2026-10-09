package com.terafes.ant.json;

import java.util.List;
import java.util.Map;

/**
 * Java のオブジェクトを JSON 文字列に変換する、小さなライターです（外部ライブラリ不使用）。
 *
 * 対応する型: Map / List / String / Number / Boolean / Enum / null
 *
 * 【XSS 対策】
 *   文字列は必ずエスケープして出力します。特に
 *     < > & ' （HTMLの特殊文字）は \\uXXXX 形式に置き換えます。
 *   こうしておくと、万一 JSON が HTML として解釈されても、
 *   スクリプトタグとして実行されることがありません。
 */
public final class JsonWriter {

    private JsonWriter() {
    }

    /**
     * オブジェクトを JSON 文字列にします。
     *
     * @param value 変換する値
     * @return JSON 文字列
     * @throws IllegalArgumentException 対応していない型が含まれる場合
     */
    public static String write(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value);
        return sb.toString();
    }

    private static void writeValue(StringBuilder sb, Object value) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Enum<?> e) {
            writeString(sb, e.name());
        } else if (value instanceof Boolean b) {
            sb.append(b.booleanValue());
        } else if (value instanceof Number n) {
            sb.append(n.toString());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(':');
                writeValue(sb, entry.getValue());
            }
            sb.append('}');
        } else if (value instanceof List<?> list) {
            sb.append('[');
            boolean first = true;
            for (Object item : list) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeValue(sb, item);
            }
            sb.append(']');
        } else {
            throw new IllegalArgumentException("JSONに変換できない型です: " + value.getClass().getName());
        }
    }

    /** 文字列を、エスケープしながら二重引用符つきで書き出します。 */
    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '<', '>', '&', '\'', ' ', ' ' -> appendUnicodeEscape(sb, c);
                default -> {
                    if (c < 0x20) {
                        appendUnicodeEscape(sb, c); // その他の制御文字
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    /** 1文字を \\uXXXX 形式で書き出します。 */
    private static void appendUnicodeEscape(StringBuilder sb, char c) {
        sb.append(String.format("\\u%04x", (int) c));
    }
}
