package com.terafes.ant.game;

/**
 * アリの1回の行動（移動またはジャンプ）の結果です。
 *
 * true / false だけでなく「なぜそうなったか」を返すことで、
 * 画面側がメッセージやアニメーションを出し分けられます。
 */
public enum ActionResult {

    /** 1マス移動できた */
    MOVED,
    /** ジャンプで落とし穴を飛び越えた */
    JUMPED,
    /** 移動先が壁だったので動かなかった */
    BLOCKED_WALL,
    /** 移動先がマップ外だったので動かなかった */
    BLOCKED_OUT_OF_MAP,
    /** ジャンプに失敗した: 前方1マスが落とし穴ではなかった */
    JUMP_FAILED_NO_PIT,
    /** ジャンプに失敗した: 着地点が壁またはマップ外だった */
    JUMP_FAILED_BLOCKED,
    /** 落とし穴に落ちた（ゲームオーバー） */
    FELL_INTO_PIT,
    /** ゴールに到達した（クリア） */
    REACHED_GOAL,
    /** すでにゲームが終わっているため、何もしなかった */
    GAME_ALREADY_OVER
}
