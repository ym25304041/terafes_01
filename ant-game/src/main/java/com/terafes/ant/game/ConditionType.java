package com.terafes.ant.game;

/**
 * 「もし〜なら」「〜の間」で使う条件の種類です。
 *
 * どの条件も、アリの「目の前」のマスについて調べます。
 * 「目の前」とは、アリが向いている方向（最後に移動・ジャンプを試みた方向。最初は右）の1マス先です。
 * 条件を調べるだけでは、時間（ステップ）は進みません。
 */
public enum ConditionType {

    /** 目の前に壁がある（マップの外も壁として扱う） */
    WALL_AHEAD,
    /** 目の前のマスに、今、敵がいる */
    ENEMY_AHEAD,
    /** 目の前に落とし穴がある */
    PIT_AHEAD,
    /** 敵が目の前のマスに、今いる、または次のステップで入ってくる（安全確認用） */
    ENEMY_COMING;

    /**
     * 文字列から ConditionType を取り出します（外部入力用）。
     * 完全一致するものだけを受け付けます（ホワイトリスト方式）。
     *
     * @throws IllegalArgumentException 該当する種類がない場合
     */
    public static ConditionType parse(String text) {
        if (text != null) {
            for (ConditionType t : values()) {
                if (t.name().equals(text)) {
                    return t;
                }
            }
        }
        throw new IllegalArgumentException("不正な条件です");
    }
}
