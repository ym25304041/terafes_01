package com.terafes.ant.program;

/**
 * プログラムブロックの種類です。
 *
 * 現在は「移動」と「ジャンプ」だけです。
 * 「物を拾う」「攻撃する」「もし〜なら」などは、ここに追加していきます。
 */
public enum BlockType {

    /** 1マス移動する */
    MOVE,
    /** 落とし穴を飛び越える */
    JUMP;

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
