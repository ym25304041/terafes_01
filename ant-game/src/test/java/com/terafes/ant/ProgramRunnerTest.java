package com.terafes.ant;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;
import com.terafes.ant.program.Block;
import com.terafes.ant.program.BlockType;
import com.terafes.ant.program.ProgramRunner;
import com.terafes.ant.program.RunResult;

import java.util.ArrayList;
import java.util.List;

/** ProgramRunner（ブロックの連続実行・50ステップ制限）のテストです。 */
final class ProgramRunnerTest {

    /** ブロックを簡単に作るための補助メソッド */
    private static Block move(String id, Direction d) {
        return new Block(id, BlockType.MOVE, d);
    }

    private static Block jump(String id, Direction d) {
        return new Block(id, BlockType.JUMP, d);
    }

    /**
     * 同じ方向への移動を n 回並べたリストを作る。
     * アリの最初の向きは右なので、右以外のときは、最初に「向く」ブロックを入れる。
     */
    private static List<Block> repeat(int n, Direction d) {
        List<Block> list = new ArrayList<>();
        if (d != Direction.RIGHT) {
            list.add(Block.turnBlock("t", d));
        }
        for (int i = 0; i < n; i++) {
            list.add(move("b" + i, d));
        }
        return list;
    }

    static void run() {
        Stage stage1 = TestStages.stage(1);
        Stage stage2 = TestStages.stage(2);

        T.group("ProgramRunner: ステージ1（まっすぐ進む）");
        RunResult r = ProgramRunner.run(stage1, repeat(8, Direction.RIGHT));
        T.eq("右へ8回でクリア", GameStatus.CLEARED, r.status());
        T.eq("クリア時の失敗理由は null", null, r.failReason());
        T.eq("ステップ数は8", 8, r.stepCount());
        T.eq("トレースは8件", 8, r.trace().size());
        T.eq("最後のトレースはゴール到達", ActionResult.REACHED_GOAL, r.trace().get(7).result());
        T.eq("最後のx座標は8", 8, r.trace().get(7).x());
        T.eq("トレースにブロックIDが入る", "b0", r.trace().get(0).blockId());

        r = ProgramRunner.run(stage1, repeat(5, Direction.RIGHT));
        T.eq("途中までだと失敗", GameStatus.FAILED, r.status());
        T.eq("理由はゴール未到達", ProgramRunner.FAIL_NOT_REACHED_GOAL, r.failReason());

        r = ProgramRunner.run(stage1, List.of());
        T.eq("空のプログラムは失敗(ゴール未到達)", GameStatus.FAILED, r.status());
        T.eq("空のプログラムのステップ数は0", 0, r.stepCount());

        r = ProgramRunner.run(stage1, repeat(20, Direction.RIGHT));
        T.eq("ゴール到達後のブロックは実行されない", 8, r.stepCount());
        T.eq("ゴールで止まるのでクリア", GameStatus.CLEARED, r.status());

        r = ProgramRunner.run(stage1, repeat(3, Direction.LEFT));
        T.eq("向く1回 + 移動3回 = 4ステップ(マップ外にぶつかってもステップは数える)", 4, r.stepCount());
        T.eq("1ステップ目は向きを変えた結果", ActionResult.TURNED, r.trace().get(0).result());
        T.eq("向いた後の向きがトレースに入る", Direction.LEFT, r.trace().get(0).facing());
        T.eq("マップ外にぶつかった結果が記録される", ActionResult.BLOCKED_OUT_OF_MAP, r.trace().get(1).result());

        T.group("ProgramRunner: ステージ2（ジャンプ）");
        List<Block> solution = new ArrayList<>();
        solution.add(move("a1", Direction.RIGHT));
        solution.add(move("a2", Direction.RIGHT));
        solution.add(move("a3", Direction.RIGHT));
        solution.add(jump("a4", Direction.RIGHT));
        solution.add(move("a5", Direction.RIGHT));
        solution.add(move("a6", Direction.RIGHT));
        solution.add(move("a7", Direction.RIGHT));
        r = ProgramRunner.run(stage2, solution);
        T.eq("ジャンプを使った正解プログラムでクリア", GameStatus.CLEARED, r.status());
        T.eq("ステップ数は7", 7, r.stepCount());
        T.eq("4つ目はジャンプ成功", ActionResult.JUMPED, r.trace().get(3).result());
        T.eq("ジャンプ後のx座標は5", 5, r.trace().get(3).x());

        r = ProgramRunner.run(stage2, repeat(8, Direction.RIGHT));
        T.eq("ジャンプせず穴に入ると失敗", GameStatus.FAILED, r.status());
        T.eq("理由は穴への落下", ProgramRunner.FAIL_FELL_INTO_PIT, r.failReason());
        T.eq("落下した時点で止まる(4ステップ目)", 4, r.stepCount());
        T.eq("落下の結果が記録される", ActionResult.FELL_INTO_PIT, r.trace().get(3).result());

        T.group("ProgramRunner: 最大ステップ数(50回)の制限");
        // 待つを150回並べる。ゴールには着かないまま50を超える
        List<Block> longProgram = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            longProgram.add(Block.waitBlock("c" + i));
        }
        r = ProgramRunner.run(stage1, longProgram);
        T.eq("51個目以降は実行されず失敗", GameStatus.FAILED, r.status());
        T.eq("理由はステップ上限超過", ProgramRunner.FAIL_STEP_LIMIT, r.failReason());
        T.eq("ステップ数は50で止まる", 50, r.stepCount());
        T.eq("トレースも50件", 50, r.trace().size());

        // ちょうど50個ならば上限超過にはならない
        r = ProgramRunner.run(stage1, longProgram.subList(0, 50));
        T.eq("ちょうど50個は上限超過ではない", ProgramRunner.FAIL_NOT_REACHED_GOAL, r.failReason());
        T.eq("ちょうど50個ならステップ数は50", 50, r.stepCount());
    }
}
