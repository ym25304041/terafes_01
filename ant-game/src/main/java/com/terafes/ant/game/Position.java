package com.terafes.ant.game;

/**
 * マップ上の座標 (x, y) を表す不変（immutable）のデータクラスです。
 *
 * Java の record を使うと、コンストラクタ・getter（x(), y()）・equals・hashCode・toString
 * が自動で作られます。「作った後に値を変えられない」ので、
 * 意図せず座標が書き換わるバグを防げます。
 *
 * @param x 横位置（0 が左端）
 * @param y 縦位置（0 が上端）
 */
public record Position(int x, int y) {
}
