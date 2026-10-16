package com.terafes.ant.game;

/**
 * 1回の行動の中で「自動的に起きたこと」の種類です。
 * 画面側はこれを見て、アイテムが消える・ドアが開くなどの演出をします。
 */
public enum EventType {

    /** アイテムを取得した */
    PICKUP,
    /** 持っていたアイテムを、そのマスに捨てた */
    ITEM_DROPPED,
    /** 物資を消費してスイッチを作動させた */
    SWITCH_ON,
    /** ドアが開いた */
    DOOR_OPEN,
    /** 武器を持った状態で敵のいるマスに着き、敵を討伐した */
    ENEMY_DEFEATED
}
