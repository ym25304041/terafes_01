package com.terafes.ant.game;

/**
 * 自動的に起きた出来事1件です。
 *
 * @param type 出来事の種類
 * @param item 取得したアイテムの種類（PICKUP のときだけ。それ以外は null）
 * @param x    出来事が起きたマスのx座標（取得した場所、スイッチの場所、開いたドアの場所）
 * @param y    出来事が起きたマスのy座標
 */
public record GameEvent(EventType type, ItemType item, int x, int y) {
}
