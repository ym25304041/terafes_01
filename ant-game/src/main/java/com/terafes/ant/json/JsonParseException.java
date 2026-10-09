package com.terafes.ant.json;

/**
 * JSON として不正な文字列を読み取ろうとしたときの例外です。
 *
 * メッセージは開発者向けです。クライアントへそのまま返さず、
 * 「リクエストが不正です」のような固定の文言に置き換えて返します。
 */
public class JsonParseException extends RuntimeException {

    /** シリアル化(保存・転送)用のバージョン番号。警告を避けるために定義している。 */
    private static final long serialVersionUID = 1L;

    public JsonParseException(String message) {
        super(message);
    }
}
