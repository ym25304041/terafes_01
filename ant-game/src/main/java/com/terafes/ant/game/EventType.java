package com.terafes.ant.game;

/**
 * 1回の行動の中で「自動的に起きたこと」の種類です。
 * 画面側はこれを見て、アイテムが消える・ドアが開くなどの演出をします。
 */
public enum EventType {

    /** アイテムを取得した */
    PICKUP,
    /** 物資を消費してスイッチを作動させた */
    SWITCH_ON,
    /** ドアが開いた */
    DOOR_OPEN
}
