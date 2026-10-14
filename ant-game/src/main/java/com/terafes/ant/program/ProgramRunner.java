package com.terafes.ant.program;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.Position;
import com.terafes.ant.game.Stage;

import java.util.ArrayList;
import java.util.List;

/**
 * ブロックのリスト（プログラム）を上から順に実行し、結果を記録するクラスです。
 *
 * 実行は1回ごとに新しい GameEngine を作るので、状態が他のリクエストと混ざりません。
 * （サーバー側にゲームの途中経過を保存しない「ステートレス」な作りです）
 */
public final class ProgramRunner {

    /** 1回のプログラムで実行できる最大ステップ数（仕様: 100回） */
    public static final int MAX_STEPS = 100;

    /** 失敗理由コード: 落とし穴に落ちた */
    public static final String FAIL_FELL_INTO_PIT = "FELL_INTO_PIT";
    /** 失敗理由コード: 武器を持たずに敵のいるマスに進み、敗北した */
    public static final String FAIL_DEFEATED_BY_ENEMY = "DEFEATED_BY_ENEMY";
    /** 失敗理由コード: 最後まで実行したがゴールに着かなかった */
    public static final String FAIL_NOT_REACHED_GOAL = "NOT_REACHED_GOAL";
    /** 失敗理由コード: 最大ステップ数を超えた */
    public static final String FAIL_STEP_LIMIT = "STEP_LIMIT_EXCEEDED";

    private ProgramRunner() {
    }

    /**
     * プログラムを実行します。
     *
     * @param stage   挑戦するステージ
     * @param program 実行するブロックのリスト
     * @return 実行結果（トレース付き）
     */
    public static RunResult run(Stage stage, List<Block> program) {
        GameEngine engine = new GameEngine(stage.createMap());
        List<TraceStep> trace = new ArrayList<>();
        int stepCount = 0;
        ActionResult lastResult = null; // 最後に実行した行動の結果（失敗の理由を判断するため）

        for (Block block : program) {
            // 上限を超えて実行しようとした時点で強制停止（無限ループ・フリーズ対策）
            if (stepCount >= MAX_STEPS) {
                engine.markFailed();
                return new RunResult(GameStatus.FAILED, FAIL_STEP_LIMIT, stepCount, trace);
            }

            // ブロックの種類に応じてエンジンのメソッドを呼ぶ
            ActionResult result = switch (block.type()) {
                case MOVE -> engine.move(block.dir());
                case JUMP -> engine.jump(block.dir());
            };

            lastResult = result;
            stepCount++;
            Position pos = engine.getPosition();
            trace.add(new TraceStep(stepCount, block.id(), result, pos.x(), pos.y(), engine.getFacing(),
                    engine.getLastEvents(), engine.hasSupply(), engine.hasWeapon()));

            // クリアか失敗が決まったら、残りのブロックは実行しない
            if (engine.getStatus() != GameStatus.PLAYING) {
                break;
            }
        }

        // 最終的な状態を確定する
        if (engine.getStatus() == GameStatus.CLEARED) {
            return new RunResult(GameStatus.CLEARED, null, stepCount, trace);
        }
        if (engine.getStatus() == GameStatus.FAILED) {
            // 失敗の理由は、最後の行動の結果から分かる（穴に落ちた／敵に敗北した）
            String reason = (lastResult == ActionResult.DEFEATED_BY_ENEMY)
                    ? FAIL_DEFEATED_BY_ENEMY
                    : FAIL_FELL_INTO_PIT;
            return new RunResult(GameStatus.FAILED, reason, stepCount, trace);
        }
        // まだプレイ中のまま最後まで来た = ゴールに着かなかった
        engine.markFailed();
        return new RunResult(GameStatus.FAILED, FAIL_NOT_REACHED_GOAL, stepCount, trace);
    }
}
