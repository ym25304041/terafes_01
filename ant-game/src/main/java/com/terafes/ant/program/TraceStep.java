package com.terafes.ant.program;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.Direction;

/**
 * 実行の1ステップぶんの記録（トレース）です。
 * 画面側はこのリストを順に再生して、アリの動きとブロックのハイライトを表現します。
 *
 * @param step    何ステップ目か（1から）
 * @param blockId 実行したブロックのID
 * @param result  その行動の結果
 * @param x       行動後のアリのx座標
 * @param y       行動後のアリのy座標
 * @param facing  行動後のアリの向き
 */
public record TraceStep(int step, String blockId, ActionResult result, int x, int y, Direction facing) {
}
