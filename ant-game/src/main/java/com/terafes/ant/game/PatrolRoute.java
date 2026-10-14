package com.terafes.ant.game;

import java.util.List;

/**
 * 巡回する敵の経路（接敵ゾーン）です。
 * 敵は経路の端から端まで、1ステップに1マスずつ進み、端に着いたらすぐ折り返して往復します。
 *
 * @param zoneId ゾーン番号（マップ上の数字 '1'〜'5'）
 * @param path   経路のマス。左端（縦ならば上端）から順に並ぶ。敵は先頭のマスから反対側の端へ向かって動き始める
 */
public record PatrolRoute(char zoneId, List<Position> path) {
}
