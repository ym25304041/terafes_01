package com.terafes.ant.program;

/**
 * プログラムブロックの種類です。
 *
 * 【コマンド（行動ブロック）】 実行するとアリが1回行動し、時間が1ステップ進む
 *   TURN  … 指定した方向（上下左右）を向く
 *   MOVE  … 今向いている方向へ1マス移動する
 *   JUMP  … 今向いている方向へジャンプし、落とし穴を飛び越える
 *   WAIT  … その場で1ステップ待つ
 *   DROP  … 持っているアイテムを、今いるマスに捨てる
 *
 * 【関数】 コマンドをまとめた、パラメータ付きのブロック。コマンドと同じように置いて使える
 *   MOVE_FUNCTION … 移動関数。「方向」と「移動回数」を指定する。
 *                   その方向を向き（1ステップ）、その向きに回数ぶん移動する（1回につき1ステップ）。
 *                   実行は、TURN と MOVE を並べたものと同じ（合計 回数+1 ステップ）。
 *
 * 【制御ブロック】 中に別のブロックを入れられる「枠」。実行しても時間は進まない
 *   IF     … もし(条件)なら(中のブロック)する。「そうでなければ」の側にもブロックを入れられる
 *   WHILE  … (条件)の間、(中のブロック)をし続ける
 *   REPEAT … (数値)回、(中のブロック)を実行する
 */
public enum BlockType {

    /** 指定した方向を向く */
    TURN,
    /** 今向いている方向へ1マス移動する */
    MOVE,
    /** 今向いている方向へジャンプする */
    JUMP,
    /** その場で1ステップ待つ */
    WAIT,
    /** 持っているアイテムを、今いるマスに捨てる */
    DROP,
    /** もし(条件)なら…する／そうでなければ…する */
    IF,
    /** (条件)の間、し続ける */
    WHILE,
    /** (数値)回実行する */
    REPEAT,
    /** 移動関数: 方向を向いて、回数ぶん移動する */
    MOVE_FUNCTION;

    /** 1回の実行でアリが1回だけ行動する（時間が1ステップ進む）コマンドか。 */
    public boolean isAction() {
        return this == TURN || this == MOVE || this == JUMP || this == WAIT || this == DROP;
    }

    /** 関数ブロックか（複数回の行動をまとめて行う）。 */
    public boolean isFunction() {
        return this == MOVE_FUNCTION;
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
