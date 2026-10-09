package com.terafes.ant.program;

import com.terafes.ant.game.GameStatus;

import java.util.List;

/**
 * プログラム実行全体の結果です。
 *
 * @param status     最終的なゲーム状態（CLEARED または FAILED）
 * @param failReason 失敗理由のコード（FELL_INTO_PIT など）。クリア時は null。
 *                   日本語メッセージはサーバーでは作らず、画面側がコードから引きます
 * @param stepCount  実行したステップ数（リザルト画面の「実行回数」に使えます）
 * @param trace      1ステップごとの記録
 */
public record RunResult(GameStatus status, String failReason, int stepCount, List<TraceStep> trace) {
}
