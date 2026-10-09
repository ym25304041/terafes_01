package com.terafes.ant.api;

/**
 * クライアントから届いたリクエストが不正だったときの例外です。
 *
 * errorCode はクライアントへ返す固定のコード文字列です。
 * 入力された値をそのままエラーメッセージに含めないようにして、
 * 攻撃者が入力した文字列が画面に反射する（XSS の入口になる）ことを防ぎます。
 */
public class BadRequestException extends RuntimeException {

    /** シリアル化(保存・転送)用のバージョン番号。警告を避けるために定義している。 */
    private static final long serialVersionUID = 1L;

    /** クライアントへ返すエラーコード（例: INVALID_BLOCK） */
    private final String errorCode;

    public BadRequestException(String errorCode) {
        super(errorCode);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
