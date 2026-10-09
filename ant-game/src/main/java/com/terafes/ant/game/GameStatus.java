package com.terafes.ant.game;

/**
 * ゲーム全体の状態です。
 */
public enum GameStatus {

    /** プレイ中（まだクリアも失敗もしていない） */
    PLAYING,
    /** クリア（ゴールに到達した） */
    CLEARED,
    /** 失敗（落とし穴に落ちた、ゴールに着けなかった、など） */
    FAILED
}
