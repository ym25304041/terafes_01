package com.terafes.ant.program;

/**
 * プログラムブロックの種類です。
 *
 * 【行動ブロック】 実行するとアリが1回行動し、時間が1ステップ進む
 *   MOVE  … 1マス移動する
 *   JUMP  … 落とし穴を飛び越える
 *   WAIT  … その場で1ステップ待つ
 *
 * 【制御ブロック】 中に別のブロックを入れられる「枠」。実行しても時間は進まない
 *   IF     … もし(条件)なら(中のブロック)する。「そうでなければ」の側にもブロックを入れられる
 *   WHILE  … (条件)の間、(中のブロック)をし続ける
 *   REPEAT … (数値)回、(中のブロック)を実行する
 */
public enum BlockType {

    /** 1マス移動する */
    MOVE,
    /** 落とし穴を飛び越える */
    JUMP,
    /** その場で1ステップ待つ */
    WAIT,
    /** もし(条件)なら…する／そうでなければ…する */
    IF,
    /** (条件)の間、し続ける */
    WHILE,
    /** (数値)回実行する */
    REPEAT;

    /** 1回の実行でアリが行動する（時間が進む）ブロックか。 */
    public boolean isAction() {
        return this == MOVE || this == JUMP || this == WAIT;
    }

    /**
     * 文字列から BlockType を取り出します（外部入力用）。
     * 完全一致するものだけを受け付け、それ以外は例外にします（ホワイトリスト方式）。
     *
     * @throws IllegalArgumentException 該当する種類がない場合
     */
    public static BlockType parse(String text) {
        if (text != null) {
            for (BlockType t : values()) {
                if (t.name().equals(text)) {
                    return t;
                }
            }
        }
        throw new IllegalArgumentException("不正なブロック種別です");
    }
}
