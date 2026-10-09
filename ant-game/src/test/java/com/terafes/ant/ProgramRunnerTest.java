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

/** ProgramRunner（ブロックの連続実行・100ステップ制限）のテストです。 */
final class ProgramRunnerTest {

    /** ブロックを簡単に作るための補助メソッド */
    private static Block move(String id, Direction d) {
        return new Block(id, BlockType.MOVE, d);
    }

    private static Block jump(String id, Direction d) {
        return new Block(id, BlockType.JUMP, d);
    }

    /** 同じブロックを n 個並べたリストを作る */
    private static List<Block> repeat(int n, Direction d) {
        List<Block> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(move("b" + i, d));
        }
        return list;
    }

    static void run() {
        Stage stage1 = StageRepository.findById(1).orElseThrow();
        Stage stage2 = StageRepository.findById(2).orElseThrow();

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
        T.eq("壁・マップ外にぶつかってもステップは数える", 3, r.stepCount());
        T.eq("マップ外にぶつかった結果が記録される", ActionResult.BLOCKED_OUT_OF_MAP, r.trace().get(0).result());

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

        T.group("ProgramRunner: 最大ステップ数(100回)の制限");
        // ステージ1で右・左を交互に繰り返す。ゴールには着かないまま100を超える
        List<Block> longProgram = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            longProgram.add(move("c" + i, i % 2 == 0 ? Direction.RIGHT : Direction.LEFT));
        }
        r = ProgramRunner.run(stage1, longProgram);
        T.eq("101個目以降は実行されず失敗", GameStatus.FAILED, r.status());
        T.eq("理由はステップ上限超過", ProgramRunner.FAIL_STEP_LIMIT, r.failReason());
        T.eq("ステップ数は100で止まる", 100, r.stepCount());
        T.eq("トレースも100件", 100, r.trace().size());

        // ちょうど100個ならば上限超過にはならない
        r = ProgramRunner.run(stage1, longProgram.subList(0, 100));
        T.eq("ちょうど100個は上限超過ではない", ProgramRunner.FAIL_NOT_REACHED_GOAL, r.failReason());
    }
}
