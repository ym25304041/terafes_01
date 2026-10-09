package com.terafes.ant.json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 外部ライブラリを使わずに JSON 文字列を読み取る、小さなパーサーです。
 *
 * 変換後の型は次の通りです。
 *   JSONオブジェクト → Map&lt;String, Object&gt;
 *   JSON配列        → List&lt;Object&gt;
 *   文字列          → String
 *   整数            → Long
 *   小数・指数      → Double
 *   true / false    → Boolean
 *   null            → null
 *
 * 【安全のための制限】
 *   ・入れ子の深さは MAX_DEPTH まで（深すぎる入力でスタックオーバーフローさせる攻撃を防ぐ）
 *   ・不正な JSON は JsonParseException を投げる
 */
public final class JsonParser {

    /** 許可する入れ子の最大の深さ */
    private static final int MAX_DEPTH = 20;

    /** 解析対象の文字列 */
    private final String text;
    /** 今、何文字目を読んでいるか */
    private int pos = 0;

    private JsonParser(String text) {
        this.text = text;
    }

    /**
     * JSON 文字列を解析して Java のオブジェクトに変換します。
     *
     * @param json JSON 文字列
     * @return 変換結果（型は上の表を参照）
     * @throws JsonParseException JSON として不正な場合
     */
    public static Object parse(String json) {
        if (json == null) {
            throw new JsonParseException("入力が空です");
        }
        JsonParser parser = new JsonParser(json);
        parser.skipWhitespace();
        Object value = parser.readValue(0);
        parser.skipWhitespace();
        // 値の後ろに余計な文字が残っていたら不正
        if (parser.pos != parser.text.length()) {
            throw new JsonParseException("JSONの末尾に余計な文字があります");
        }
        return value;
    }

    // ------------------------------------------------------------------
    // 値の読み取り
    // ------------------------------------------------------------------

    /** 次の値(オブジェクト・配列・文字列・数値・true/false/null)を1つ読みます。 */
    private Object readValue(int depth) {
        if (depth > MAX_DEPTH) {
            throw new JsonParseException("JSONの入れ子が深すぎます");
        }
        if (pos >= text.length()) {
            throw new JsonParseException("JSONが途中で終わっています");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return readObject(depth);
            case '[':
                return readArray(depth);
            case '"':
                return readString();
            case 't':
                expectWord("true");
                return Boolean.TRUE;
            case 'f':
                expectWord("false");
                return Boolean.FALSE;
            case 'n':
                expectWord("null");
                return null;
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return readNumber();
                }
                throw new JsonParseException("想定外の文字です(位置 " + pos + ")");
        }
    }

    /** { "キー": 値, ... } を読みます。 */
    private Map<String, Object> readObject(int depth) {
        Map<String, Object> map = new LinkedHashMap<>();
        pos++; // '{' を読み飛ばす
        skipWhitespace();
        if (peek() == '}') {
            pos++;
            return map;
        }
        while (true) {
            skipWhitespace();
            if (peek() != '"') {
                throw new JsonParseException("キーは文字列である必要があります");
            }
            String key = readString();
            skipWhitespace();
            expectChar(':');
            skipWhitespace();
            map.put(key, readValue(depth + 1));
            skipWhitespace();
            char next = peek();
            pos++;
            if (next == '}') {
                return map;
            }
            if (next != ',') {
                throw new JsonParseException("',' または '}' が必要です");
            }
        }
    }

    /** [ 値, 値, ... ] を読みます。 */
    private List<Object> readArray(int depth) {
        List<Object> list = new ArrayList<>();
        pos++; // '[' を読み飛ばす
        skipWhitespace();
        if (peek() == ']') {
            pos++;
            return list;
        }
        while (true) {
            skipWhitespace();
            list.add(readValue(depth + 1));
            skipWhitespace();
            char next = peek();
            pos++;
            if (next == ']') {
                return list;
            }
            if (next != ',') {
                throw new JsonParseException("',' または ']' が必要です");
            }
        }
    }

    /** "文字列" を読みます。エスケープ(改行やUnicode形式の指定など)も処理します。 */
    private String readString() {
        StringBuilder sb = new StringBuilder();
        pos++; // 開始の '"' を読み飛ばす
        while (true) {
            if (pos >= text.length()) {
                throw new JsonParseException("文字列が閉じられていません");
            }
            char c = text.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c < 0x20) {
                throw new JsonParseException("文字列に制御文字は使えません");
            }
            if (c != '\\') {
                sb.append(c);
                continue;
            }
            // バックスラッシュ(\)で始まるエスケープ
            if (pos >= text.length()) {
                throw new JsonParseException("エスケープが途中で終わっています");
            }
            char e = text.charAt(pos++);
            switch (e) {
                case '"' -> sb.append('"');
                case '\\' -> sb.append('\\');
                case '/' -> sb.append('/');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'u' -> {
                    if (pos + 4 > text.length()) {
                        throw new JsonParseException("\\u エスケープが不正です");
                    }
                    try {
                        sb.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    } catch (NumberFormatException ex) {
                        throw new JsonParseException("\\u エスケープが不正です");
                    }
                    pos += 4;
                }
                default -> throw new JsonParseException("不正なエスケープです");
            }
        }
    }

    /** 数値を読みます。小数点や指数がなければ Long、あれば Double にします。 */
    private Object readNumber() {
        int start = pos;
        boolean isDecimal = false;
        if (peek() == '-') {
            pos++;
        }
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                isDecimal = true;
                pos++;
            } else {
                break;
            }
        }
        String token = text.substring(start, pos);
        try {
            return isDecimal ? (Object) Double.valueOf(token) : (Object) Long.valueOf(token);
        } catch (NumberFormatException e) {
            throw new JsonParseException("数値が不正です");
        }
    }

    // ------------------------------------------------------------------
    // 補助メソッド
    // ------------------------------------------------------------------

    /** 空白・改行・タブを読み飛ばします。 */
    private void skipWhitespace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    /** 現在位置の文字を、位置を進めずに返します。末尾なら例外にします。 */
    private char peek() {
        if (pos >= text.length()) {
            throw new JsonParseException("JSONが途中で終わっています");
        }
        return text.charAt(pos);
    }

    /** 指定した1文字が来ることを確認して読み進めます。 */
    private void expectChar(char expected) {
        if (peek() != expected) {
            throw new JsonParseException("'" + expected + "' が必要です");
        }
        pos++;
    }

    /** true / false / null といった単語がそのまま来ることを確認して読み進めます。 */
    private void expectWord(String word) {
        if (!text.startsWith(word, pos)) {
            throw new JsonParseException("想定外の文字です(位置 " + pos + ")");
        }
        pos += word.length();
    }
}
