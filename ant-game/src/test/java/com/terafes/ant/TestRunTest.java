package com.terafes.ant;

import com.terafes.ant.api.BadRequestException;
import com.terafes.ant.api.RunRequest;
import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.ConditionType;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameMap;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.Position;
import com.terafes.ant.program.Block;
import com.terafes.ant.program.ProgramRunner;
import com.terafes.ant.program.RunResult;

import java.util.ArrayList;
import java.util.List;

/** テスト実行（動きを記録するだけで、ゴールの判定をしない実行）のテストです。 */
final class TestRunTest {

    private static List<Block> moves(int n) {
        List<Block> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(Block.moveBlock("m" + i));
        }
        return list;
    }

    static void run() {
        T.group("テスト実行: エンジン（ゴールの判定をしない）");
        GameMap map = GameMap.fromRows(List.of(
                ".........", ".........", "S.G......", ".........", "........."));
        GameEngine e = new GameEngine(map, Direction.RIGHT, false);
        e.moveForward();
        T.eq("ゴールの1つ手前", new Position(1, 2), e.getPosition());
        T.eq("ゴールに着いても、クリアではなく、ただ移動しただけ", ActionResult.MOVED, e.moveForward());
        T.eq("状態はプレイ中のまま", GameStatus.PLAYING, e.getStatus());
        T.check("ゴールの上にいる", e.isAtGoal());
        T.eq("ゴールを通り過ぎて、さらに進める", ActionResult.MOVED, e.moveForward());
        T.eq("ゴールの先にいる", new Position(3, 2), e.getPosition());

        GameEngine normal = new GameEngine(map);
        normal.moveForward();
        T.eq("（通常の実行ではクリアになる）", ActionResult.REACHED_GOAL, normal.moveForward());
        T.eq("通常の実行ではクリア状態", GameStatus.CLEARED, normal.getStatus());

        GameMap pit = GameMap.fromRows(List.of(
                ".........", ".........", "S.P.....G", ".........", "........."));
        e = new GameEngine(pit, Direction.RIGHT, false);
        e.moveForward();
        T.eq("テスト実行でも、落とし穴には落ちる", ActionResult.FELL_INTO_PIT, e.moveForward());
        T.eq("落ちると失敗状態", GameStatus.FAILED, e.getStatus());

        T.group("テスト実行: ProgramRunner");
        RunResult r = ProgramRunner.run(TestStages.stage(1), moves(20), false);
        T.eq("最後まで実行して、成功・失敗を決めない(PLAYING)", GameStatus.PLAYING, r.status());
        T.eq("失敗理由はない", null, r.failReason());
        T.eq("20ステップすべて実行する", 20, r.stepCount());
        T.eq("8歩目でゴールに着いても、ただの移動", ActionResult.MOVED, r.trace().get(7).result());
        T.eq("ゴールの座標を通過している", 8, r.trace().get(7).x());
        T.eq("9歩目からは、マップの外にぶつかる", ActionResult.BLOCKED_OUT_OF_MAP, r.trace().get(8).result());

        r = ProgramRunner.run(TestStages.stage(1), moves(20), true);
        T.eq("（通常の実行は、ゴールでクリアして止まる）", GameStatus.CLEARED, r.status());
        T.eq("通常の実行は8ステップ", 8, r.stepCount());
        r = ProgramRunner.run(TestStages.stage(1), moves(20));
        T.eq("引数なしの実行は、通常の実行と同じ", GameStatus.CLEARED, r.status());

        r = ProgramRunner.run(TestStages.stage(1), List.of(), false);
        T.eq("空のプログラムでも、失敗にならない", GameStatus.PLAYING, r.status());
        T.eq("ステップは0", 0, r.stepCount());
        r = ProgramRunner.run(TestStages.stage(1), List.of(), true);
        T.eq("（通常の実行では、ゴールに着けず失敗）", ProgramRunner.FAIL_NOT_REACHED_GOAL, r.failReason());

        r = ProgramRunner.run(TestStages.stage(2), moves(8), false);
        T.eq("落とし穴に落ちたら、失敗として返る", GameStatus.FAILED, r.status());
        T.eq("理由は落下", ProgramRunner.FAIL_FELL_INTO_PIT, r.failReason());
        T.eq("落ちた時点で止まる(4ステップ)", 4, r.stepCount());

        r = ProgramRunner.run(TestStages.stage(6), moves(2), false);
        T.eq("敵に負けたら、失敗として返る", ProgramRunner.FAIL_DEFEATED_BY_ENEMY, r.failReason());

        r = ProgramRunner.run(TestStages.stage(1), List.of(
                Block.whileBlock("w", ConditionType.WALL_AHEAD, true, List.of(Block.waitBlock("x")))), false);
        T.eq("ステップ数の上限は、テスト実行でも有効", ProgramRunner.FAIL_STEP_LIMIT, r.failReason());
        T.eq("100ステップで止まる", 100, r.stepCount());

        T.group("テスト実行: RunRequest（testRun の指定）");
        T.check("省略すると通常の実行", !RunRequest.parse("{\"stageId\":1,\"program\":[]}").testRun());
        T.check("true でテスト実行", RunRequest.parse("{\"stageId\":1,\"program\":[],\"testRun\":true}").testRun());
        T.check("false は通常の実行", !RunRequest.parse("{\"stageId\":1,\"program\":[],\"testRun\":false}").testRun());
        T.check("null は省略と同じ", !RunRequest.parse("{\"stageId\":1,\"program\":[],\"testRun\":null}").testRun());
        try {
            RunRequest.parse("{\"stageId\":1,\"program\":[],\"testRun\":\"yes\"}");
            T.check("真偽値でない testRun は拒否される（例外が出なかった）", false);
        } catch (BadRequestException ex) {
            T.eq("真偽値でない testRun は拒否される", "INVALID_REQUEST", ex.getErrorCode());
        }
    }
}
