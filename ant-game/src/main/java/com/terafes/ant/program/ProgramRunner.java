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

            stepCount++;
            Position pos = engine.getPosition();
            trace.add(new TraceStep(stepCount, block.id(), result, pos.x(), pos.y(), engine.getFacing()));

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
            return new RunResult(GameStatus.FAILED, FAIL_FELL_INTO_PIT, stepCount, trace);
        }
        // まだプレイ中のまま最後まで来た = ゴールに着かなかった
        engine.markFailed();
        return new RunResult(GameStatus.FAILED, FAIL_NOT_REACHED_GOAL, stepCount, trace);
    }
}
