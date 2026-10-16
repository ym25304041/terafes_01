package com.terafes.ant.program;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.EnemyState;
import com.terafes.ant.game.GameEvent;
import com.terafes.ant.game.ItemType;

import java.util.List;

/**
 * 実行の1ステップ（アリの行動1回）ぶんの記録（トレース）です。
 * 画面側はこのリストを順に再生して、アリと敵の動き・ブロックのハイライト・
 * アイテムやドアの変化を表現します。
 *
 * @param step       何ステップ目か（1から）
 * @param blockId    実行した行動ブロックのID
 * @param result     その行動の結果
 * @param x          行動後のアリのx座標
 * @param y          行動後のアリのy座標
 * @param facing     行動後のアリの向き
 * @param events     この行動で自動的に起きた出来事（アイテム取得・捨てる・スイッチ作動・ドアが開く・敵の討伐）
 * @param heldItem   行動後に持っているアイテム（SUPPLY / WEAPON。何も持っていなければ null）
 * @param enemies    行動後の、生きている全ての敵の位置（敵の動きは、アリの行動のあとに反映済み）
 * @param conditions この行動の直前に調べた条件（前の行動から、この行動までの間に評価した制御ブロック）
 */
public record TraceStep(int step, String blockId, ActionResult result, int x, int y, Direction facing,
                        List<GameEvent> events, ItemType heldItem,
                        List<EnemyState> enemies, List<ConditionCheck> conditions) {
}
