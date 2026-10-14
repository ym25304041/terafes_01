package com.terafes.ant.game;

/**
 * ある時点での敵1体の状態（画面に表示するための情報）です。
 *
 * @param id 敵のID。動かない敵は "x0", "x1"…、巡回する敵は "z1"〜"z5"（ゾーン番号）
 * @param x  現在のx座標
 * @param y  現在のy座標
 */
public record EnemyState(String id, int x, int y) {
}
