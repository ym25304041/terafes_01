package com.terafes.ant.program;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.Position;
import com.terafes.ant.game.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * ブロックのリスト（プログラム）を上から順に実行し、結果を記録するクラスです。
 * 「もし」「の間」「回くり返す」のような、中にブロックを入れられる制御ブロックも実行できます。
 *
 * 実行は1回ごとに新しい GameEngine を作るので、状態が他のリクエストと混ざりません。
 * （サーバー側にゲームの途中経過を保存しない「ステートレス」な作りです）
 *
 * 【2つの上限（無限ループ・フリーズ対策）】
 *   ・最大ステップ数（MAX_STEPS = 100）: アリの行動（移動・ジャンプ・待つ）の回数の上限。
 *     101回目の行動をしようとした時点で強制停止します（仕様）。
 *   ・最大処理回数（MAX_OPERATIONS = 1000）: ブロックを実行した回数や、条件を調べた回数の上限。
 *     「中身が空の無限ループ」のように、アリが行動しないまま延々と回り続けるプログラムを止めるためです。
 */
public final class ProgramRunner {

    /** 1回のプログラムで実行できる最大ステップ数（アリの行動の回数。仕様: 100回） */
    public static final int MAX_STEPS = 100;
    /** 1回のプログラムで許可する最大処理回数（ブロックの実行・条件の評価・くり返しの回数の合計） */
    public static final int MAX_OPERATIONS = 1000;

    /** 失敗理由コード: 落とし穴に落ちた */
    public static final String FAIL_FELL_INTO_PIT = "FELL_INTO_PIT";
    /** 失敗理由コード: 武器を持たずに敵と遭遇し、敗北した */
    public static final String FAIL_DEFEATED_BY_ENEMY = "DEFEATED_BY_ENEMY";
    /** 失敗理由コード: 最後まで実行したがゴールに着かなかった */
    public static final String FAIL_NOT_REACHED_GOAL = "NOT_REACHED_GOAL";
    /** 失敗理由コード: 最大ステップ数を超えた */
    public static final String FAIL_STEP_LIMIT = "STEP_LIMIT_EXCEEDED";
    /** 失敗理由コード: 最大処理回数を超えた（アリが行動しないループなど） */
    public static final String FAIL_LOOP_LIMIT = "LOOP_LIMIT_EXCEEDED";

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
        GameEngine engine = new GameEngine(stage.createMap(), stage.startFacing());
        Execution execution = new Execution(engine);
        execution.runBlocks(program);

        List<TraceStep> trace = execution.trace;
        int stepCount = execution.steps;

        // 上限に達して止めた場合
        if (execution.stopReason != null) {
            engine.markFailed();
            return new RunResult(GameStatus.FAILED, execution.stopReason, stepCount, trace);
        }
        // 最終的な状態を確定する
        if (engine.getStatus() == GameStatus.CLEARED) {
            return new RunResult(GameStatus.CLEARED, null, stepCount, trace);
        }
        if (engine.getStatus() == GameStatus.FAILED) {
            // 失敗の理由は、最後の行動の結果から分かる（穴に落ちた／敵に敗北した）
            String reason = (execution.lastResult == ActionResult.DEFEATED_BY_ENEMY)
                    ? FAIL_DEFEATED_BY_ENEMY
                    : FAIL_FELL_INTO_PIT;
            return new RunResult(GameStatus.FAILED, reason, stepCount, trace);
        }
        // まだプレイ中のまま最後まで来た = ゴールに着かなかった
        engine.markFailed();
        return new RunResult(GameStatus.FAILED, FAIL_NOT_REACHED_GOAL, stepCount, trace);
    }

    /**
     * プログラム1回ぶんの実行状態をまとめたクラス（このクラスの中だけで使う）。
     * ブロックを再帰的にたどりながら、ステップ数・処理回数・トレースを記録します。
     */
    private static final class Execution {

        /** ゲームを進めるエンジン */
        private final GameEngine engine;
        /** 1ステップごとの記録 */
        private final List<TraceStep> trace = new ArrayList<>();
        /** 次の行動の記録に添える、調べた条件の一覧 */
        private final List<ConditionCheck> pendingConditions = new ArrayList<>();
        /** ここまでに実行した行動の回数（ステップ数） */
        private int steps = 0;
        /** ここまでの処理回数 */
        private int operations = 0;
        /** 上限に達して強制停止したときの理由コード（止まっていなければ null） */
        private String stopReason = null;
        /** 最後に実行した行動の結果 */
        private ActionResult lastResult = null;

        private Execution(GameEngine engine) {
            this.engine = engine;
        }

        /**
         * ブロックのリストを上から順に実行します。
         *
         * @return 続けて実行してよければ true。クリア・失敗・上限超過で止まるときは false
         */
        private boolean runBlocks(List<Block> blocks) {
            for (Block block : blocks) {
                if (!runBlock(block)) {
                    return false;
                }
            }
            return true;
        }

        /** ブロック1つを実行します。戻り値の意味は runBlocks と同じです。 */
        private boolean runBlock(Block block) {
            if (!countOperation()) {
                return false;
            }
            switch (block.type()) {
                case TURN, MOVE, JUMP, WAIT, DROP -> {
                    return runAction(block);
                }
                case MOVE_FUNCTION -> {
                    return runMoveFunction(block);
                }
                case IF -> {
                    // 条件が成り立てば「なら」の側、成り立たなければ「そうでなければ」の側を実行する
                    boolean condition = evaluate(block);
                    return runBlocks(condition ? block.body() : block.elseBody());
                }
                case WHILE -> {
                    // 条件が成り立つ間、中のブロックをくり返す。毎回、先に条件を調べる
                    while (evaluate(block)) {
                        if (!runBlocks(block.body())) {
                            return false;
                        }
                        // 中身が空でも回数を数えるので、無限ループは必ず止まる
                        if (!countOperation()) {
                            return false;
                        }
                    }
                    return true;
                }
                case REPEAT -> {
                    for (int i = 0; i < block.count(); i++) {
                        if (!runBlocks(block.body())) {
                            return false;
                        }
                        if (!countOperation()) {
                            return false;
                        }
                    }
                    return true;
                }
                default -> throw new IllegalStateException("未対応のブロックです");
            }
        }

        /**
         * コマンド（向く・移動・ジャンプ・待つ）を1回実行し、トレースに記録します。
         *
         * @return 続けて実行してよければ true（クリアか失敗が決まったら false）
         */
        private boolean runAction(Block block) {
            return perform(block.id(), () -> switch (block.type()) {
                case TURN -> engine.turn(block.dir());
                case MOVE -> engine.moveForward();
                case JUMP -> engine.jumpForward();
                case DROP -> engine.dropItem();
                default -> engine.waitOneTurn();
            });
        }

        /**
         * 移動関数を実行します。「方向を向く」→「回数ぶん前へ移動」を、コマンドを並べたのと同じ形で行います。
         * 途中で壁にぶつかって動けなくても、残りの移動は続けます（動けなかった行動も1ステップに数えます）。
         * トレースには、すべてのステップに、この関数ブロックのIDを記録します。
         *
         * @return 続けて実行してよければ true（クリア・失敗・上限超過なら false）
         */
        private boolean runMoveFunction(Block block) {
            if (!perform(block.id(), () -> engine.turn(block.dir()))) {
                return false;
            }
            for (int i = 0; i < block.count(); i++) {
                if (!perform(block.id(), engine::moveForward)) {
                    return false;
                }
            }
            return true;
        }

        /**
         * アリの行動を1回実行し（時間が1ステップ進む）、トレースに記録します。
         *
         * @param blockId 記録するブロックのID（画面のハイライト用）
         * @param action  実行する行動
         * @return 続けて実行してよければ true（クリアか失敗が決まったら false）
         */
        private boolean perform(String blockId, Supplier<ActionResult> action) {
            // 上限を超えて行動しようとした時点で強制停止（無限ループ・フリーズ対策）
            if (steps >= MAX_STEPS) {
                stopReason = FAIL_STEP_LIMIT;
                return false;
            }

            ActionResult result = action.get();

            lastResult = result;
            steps++;
            Position pos = engine.getPosition();
            trace.add(new TraceStep(steps, blockId, result, pos.x(), pos.y(), engine.getFacing(),
                    engine.getLastEvents(), engine.getHeldItem(),
                    engine.getEnemyStates(), List.copyOf(pendingConditions)));
            pendingConditions.clear();

            // クリアか失敗が決まったら、残りのブロックは実行しない
            return engine.getStatus() == GameStatus.PLAYING;
        }

        /**
         * 制御ブロック（IF / WHILE）の条件を調べます。調べても時間は進みません。
         * 「〜でない」(negate)が指定されていれば、結果を反転します。
         */
        private boolean evaluate(Block block) {
            boolean value = engine.check(block.condition()) != block.negate();
            pendingConditions.add(new ConditionCheck(block.id(), value));
            return value;
        }

        /**
         * 処理回数を1つ数えます。上限を超えたら強制停止の理由を記録します。
         *
         * @return まだ続けてよければ true
         */
        private boolean countOperation() {
            operations++;
            if (operations > MAX_OPERATIONS) {
                stopReason = FAIL_LOOP_LIMIT;
                return false;
            }
            return true;
        }
    }
}
