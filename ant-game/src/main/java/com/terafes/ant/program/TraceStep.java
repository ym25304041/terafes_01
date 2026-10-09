package com.terafes.ant.program;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.GameEvent;

import java.util.List;

/**
 * 実行の1ステップぶんの記録（トレース）です。
 * 画面側はこのリストを順に再生して、アリの動き・ブロックのハイライト・
 * アイテムやドアの変化を表現します。
 *
 * @param step      何ステップ目か（1から）
 * @param blockId   実行したブロックのID
 * @param result    その行動の結果
 * @param x         行動後のアリのx座標
 * @param y         行動後のアリのy座標
 * @param facing    行動後のアリの向き
 * @param events    この行動で自動的に起きた出来事（アイテム取得・スイッチ作動・ドアが開く）
 * @param hasSupply 行動後に物資を持っているか
 * @param hasWeapon 行動後に武器を持っているか
 */
public record TraceStep(int step, String blockId, ActionResult result, int x, int y, Direction facing,
                        List<GameEvent> events, boolean hasSupply, boolean hasWeapon) {
}
