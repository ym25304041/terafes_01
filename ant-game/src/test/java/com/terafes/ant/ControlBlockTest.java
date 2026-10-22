package com.terafes.ant;

import com.terafes.ant.api.BadRequestException;
import com.terafes.ant.api.RunRequest;
import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.ConditionType;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;
import com.terafes.ant.program.Block;
import com.terafes.ant.program.BlockType;
import com.terafes.ant.program.ConditionCheck;
import com.terafes.ant.program.ProgramRunner;
import com.terafes.ant.program.RunResult;

import java.util.List;

/** 制御ブロック（もし・の間・回くり返す）と「待つ」ブロックのテストです。 */
final class ControlBlockTest {

    private static Block move(String id, Direction d) {
        return new Block(id, BlockType.MOVE, d);
    }

    private static Block right(String id) {
        return move(id, Direction.RIGHT);
    }

    private static void badRequest(String name, String expectedCode, String json) {
        try {
            RunRequest.parse(json);
            T.check(name + "（例外が出なかった）", false);
        } catch (BadRequestException e) {
            T.eq(name, expectedCode, e.getErrorCode());
        }
    }

    /** 制御ブロック(REPEAT)を depth 段、入れ子にした JSON を作る。いちばん内側は「待つ」。 */
    private static String nestedRepeat(int depth) {
        String inner = "{\"id\":\"w\",\"type\":\"WAIT\"}";
        for (int i = 0; i < depth; i++) {
            inner = "{\"id\":\"r" + i + "\",\"type\":\"REPEAT\",\"count\":1,\"body\":[" + inner + "]}";
        }
        return "{\"stageId\":1,\"program\":[" + inner + "]}";
    }

    static void run() {
        Stage stage1 = TestStages.stage(1);
        Stage stage2 = TestStages.stage(2);
        Stage stage6 = TestStages.stage(6);

        T.group("「待つ」ブロック");
        RunResult r = ProgramRunner.run(stage1, List.of(Block.waitBlock("w1"), Block.waitBlock("w2")));
        T.eq("待つは1ステップずつ数える", 2, r.stepCount());
        T.eq("待った結果が記録される", ActionResult.WAITED, r.trace().get(0).result());
        T.eq("待ってもアリは動かない", 0, r.trace().get(1).x());
        T.eq("ゴールしていないので失敗", GameStatus.FAILED, r.status());

        T.group("くり返し（回数）");
        r = ProgramRunner.run(stage1, List.of(Block.repeatBlock("rep", 3, List.of(right("m")))));
        T.eq("3回くり返すと3ステップ", 3, r.stepCount());
        T.eq("3歩進んでいる", 3, r.trace().get(2).x());
        T.eq("中のブロックのIDがトレースに入る", "m", r.trace().get(0).blockId());

        r = ProgramRunner.run(stage1, List.of(Block.repeatBlock("outer", 2,
                List.of(Block.repeatBlock("inner", 2, List.of(right("m")))))));
        T.eq("入れ子のくり返し(2×2)は4ステップ", 4, r.stepCount());

        r = ProgramRunner.run(stage1, List.of(Block.repeatBlock("rep", 8, List.of(right("m")))));
        T.eq("8回くり返してゴール", GameStatus.CLEARED, r.status());

        r = ProgramRunner.run(stage1, List.of(Block.repeatBlock("rep", 20, List.of(right("m")))));
        T.eq("ゴールしたら、残りのくり返しは実行されない", 8, r.stepCount());

        T.group("もし〜なら〜そうでなければ");
        r = ProgramRunner.run(stage1, List.of(Block.ifBlock("if", ConditionType.WALL_AHEAD, false,
                List.of(Block.turnBlock("up", Direction.UP)), List.of(right("go")))));
        T.eq("壁がないので『そうでなければ』が実行される", "go", r.trace().get(0).blockId());
        T.eq("条件の結果が記録される(偽)", List.of(new ConditionCheck("if", false)), r.trace().get(0).conditions());
        T.eq("条件を調べても1ステップは増えない", 1, r.stepCount());

        r = ProgramRunner.run(stage1, List.of(Block.ifBlock("if", ConditionType.WALL_AHEAD, true,
                List.of(right("go")), List.of(Block.turnBlock("up", Direction.UP)))));
        T.eq("『〜でない』を指定すると反転する", "go", r.trace().get(0).blockId());
        T.eq("反転後の結果が記録される(真)", List.of(new ConditionCheck("if", true)), r.trace().get(0).conditions());

        r = ProgramRunner.run(stage1, List.of(Block.ifBlock("if", ConditionType.WALL_AHEAD, false,
                List.of(right("go")), List.of())));
        T.eq("条件が偽で『そうでなければ』が空なら、何も起きない", 0, r.stepCount());
        T.eq("ゴールしていないので失敗", ProgramRunner.FAIL_NOT_REACHED_GOAL, r.failReason());

        T.group("〜の間し続ける");
        r = ProgramRunner.run(stage1, List.of(
                Block.whileBlock("wh", ConditionType.WALL_AHEAD, true, List.of(right("m")))));
        T.eq("壁がない間進み続けて、ゴールに着く", GameStatus.CLEARED, r.status());
        T.eq("8歩でゴール", 8, r.stepCount());

        r = ProgramRunner.run(stage2, List.of(
                Block.whileBlock("wh", ConditionType.PIT_AHEAD, true, List.of(right("m"))),
                new Block("j", BlockType.JUMP, Direction.RIGHT),
                Block.repeatBlock("rep", 3, List.of(right("m2")))));
        T.eq("穴の手前まで進み、ジャンプして、ゴールまで進む", GameStatus.CLEARED, r.status());
        T.eq("3歩 + ジャンプ + 3歩 = 7ステップ", 7, r.stepCount());
        T.eq("ジャンプが成功している", ActionResult.JUMPED, r.trace().get(3).result());

        T.group("ステージ6: 敵の前を横切る");
        List<Block> solution = List.of(
                right("m1"),
                Block.whileBlock("wh", ConditionType.ENEMY_COMING, false, List.of(Block.waitBlock("w"))),
                Block.repeatBlock("rep", 5, List.of(right("m2"))));
        r = ProgramRunner.run(stage6, solution);
        T.eq("敵が通り過ぎるのを待ってからなら、クリア", GameStatus.CLEARED, r.status());
        T.eq("1歩 + 待つ2回 + 5歩 = 8ステップ", 8, r.stepCount());
        T.eq("2ステップ目は待つ", ActionResult.WAITED, r.trace().get(1).result());
        T.eq("3ステップ目も待つ", ActionResult.WAITED, r.trace().get(2).result());
        T.eq("4ステップ目で進む", "m2", r.trace().get(3).blockId());
        T.eq("2ステップ目の前に条件を調べて真", List.of(new ConditionCheck("wh", true)), r.trace().get(1).conditions());
        T.eq("4ステップ目の前に条件を調べて偽", List.of(new ConditionCheck("wh", false)), r.trace().get(3).conditions());
        T.eq("1ステップ後の敵は(4,1)", "z1", r.trace().get(0).enemies().get(0).id());
        T.eq("敵のx座標", 4, r.trace().get(0).enemies().get(0).x());
        T.eq("敵のy座標", 1, r.trace().get(0).enemies().get(0).y());
        T.eq("2ステップ後の敵は(4,2)", 2, r.trace().get(1).enemies().get(0).y());

        r = ProgramRunner.run(stage6, List.of(Block.repeatBlock("rep", 8, List.of(right("m")))));
        T.eq("待たずに進むと敵とぶつかって失敗", GameStatus.FAILED, r.status());
        T.eq("理由は敵に敗北", ProgramRunner.FAIL_DEFEATED_BY_ENEMY, r.failReason());
        T.eq("2ステップ目で敗北", 2, r.stepCount());
        T.eq("敗北の結果が記録される", ActionResult.DEFEATED_BY_ENEMY, r.trace().get(1).result());

        T.group("上限（無限ループ・フリーズ対策）");
        r = ProgramRunner.run(stage1, List.of(
                Block.whileBlock("wh", ConditionType.WALL_AHEAD, true, List.of())));
        T.eq("中身が空の無限ループは処理回数の上限で止まる", ProgramRunner.FAIL_LOOP_LIMIT, r.failReason());
        T.eq("行動していないのでステップは0", 0, r.stepCount());
        T.eq("失敗状態", GameStatus.FAILED, r.status());

        r = ProgramRunner.run(stage1, List.of(
                Block.whileBlock("wh", ConditionType.WALL_AHEAD, true, List.of(Block.waitBlock("w")))));
        T.eq("待ち続けるループはステップ数の上限(50)で止まる", ProgramRunner.FAIL_STEP_LIMIT, r.failReason());
        T.eq("ステップ数は50", 50, r.stepCount());
        T.eq("トレースも50件", 50, r.trace().size());

        r = ProgramRunner.run(stage1, List.of(Block.repeatBlock("a", 100, List.of(
                Block.repeatBlock("b", 100, List.of(Block.repeatBlock("c", 100, List.of())))))));
        T.eq("空のくり返しを深く重ねても、処理回数の上限で止まる", ProgramRunner.FAIL_LOOP_LIMIT, r.failReason());

        T.group("RunRequest: 入れ子のブロックの検査");
        RunRequest req = RunRequest.parse("{\"stageId\":6,\"program\":["
                + "{\"id\":\"a\",\"type\":\"MOVE\",\"dir\":\"RIGHT\"},"
                + "{\"id\":\"b\",\"type\":\"WAIT\"},"
                + "{\"id\":\"c\",\"type\":\"WHILE\",\"condition\":\"ENEMY_COMING\",\"not\":true,"
                + "\"body\":[{\"id\":\"d\",\"type\":\"WAIT\"}]},"
                + "{\"id\":\"e\",\"type\":\"IF\",\"condition\":\"WALL_AHEAD\","
                + "\"then\":[{\"id\":\"f\",\"type\":\"TURN\",\"dir\":\"UP\"}],"
                + "\"else\":[{\"id\":\"g\",\"type\":\"JUMP\",\"dir\":\"DOWN\"}]},"
                + "{\"id\":\"h\",\"type\":\"REPEAT\",\"count\":3,"
                + "\"body\":[{\"id\":\"i\",\"type\":\"MOVE\",\"dir\":\"LEFT\"}]}]}");
        T.eq("トップレベルのブロック数は5", 5, req.program().size());
        T.eq("待つブロック", BlockType.WAIT, req.program().get(1).type());
        Block wh = req.program().get(2);
        T.eq("WHILE の条件", ConditionType.ENEMY_COMING, wh.condition());
        T.check("WHILE の「〜でない」", wh.negate());
        T.eq("WHILE の中身", "d", wh.body().get(0).id());
        Block iff = req.program().get(3);
        T.check("IF の「〜でない」の省略は false", !iff.negate());
        T.eq("IF の「なら」の中身(向く)", BlockType.TURN, iff.body().get(0).type());
        T.eq("IF の「なら」の中身の方向", Direction.UP, iff.body().get(0).dir());
        T.eq("IF の「そうでなければ」の中身", BlockType.JUMP, iff.elseBody().get(0).type());
        T.eq("REPEAT の回数", 3, req.program().get(4).count());

        req = RunRequest.parse("{\"stageId\":1,\"program\":[{\"id\":\"e\",\"type\":\"IF\","
                + "\"condition\":\"PIT_AHEAD\",\"then\":[]}]}");
        T.eq("「そうでなければ」は省略できる", 0, req.program().get(0).elseBody().size());

        badRequest("未知の条件", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"IF\",\"condition\":\"ZONE\",\"then\":[]}]}");
        badRequest("条件がない", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"WHILE\",\"body\":[]}]}");
        badRequest("WHILE の中身がない", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"WHILE\",\"condition\":\"PIT_AHEAD\"}]}");
        badRequest("IF の「なら」の側がない", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"IF\",\"condition\":\"PIT_AHEAD\"}]}");
        badRequest("not が真偽値でない", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"WHILE\",\"condition\":\"PIT_AHEAD\","
                        + "\"not\":\"yes\",\"body\":[]}]}");
        badRequest("REPEAT の回数が0", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"REPEAT\",\"count\":0,\"body\":[]}]}");
        badRequest("REPEAT の回数が上限(100)超え", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"REPEAT\",\"count\":101,\"body\":[]}]}");
        badRequest("REPEAT の回数が文字列", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"REPEAT\",\"count\":\"3\",\"body\":[]}]}");
        badRequest("入れ子の中のブロックIDも検査される(XSS対策)", "INVALID_BLOCK_ID",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"REPEAT\",\"count\":1,"
                        + "\"body\":[{\"id\":\"<img>\",\"type\":\"WAIT\"}]}]}");
        badRequest("入れ子の中の不正な方向", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"a\",\"type\":\"REPEAT\",\"count\":1,"
                        + "\"body\":[{\"id\":\"b\",\"type\":\"TURN\",\"dir\":\"NORTH\"}]}]}");

        T.eq("6段までの入れ子は受け付ける", 1, RunRequest.parse(nestedRepeat(RunRequest.MAX_DEPTH)).program().size());
        badRequest("7段の入れ子は拒否", "PROGRAM_TOO_DEEP", nestedRepeat(RunRequest.MAX_DEPTH + 1));

        StringBuilder many = new StringBuilder("{\"stageId\":1,\"program\":[{\"id\":\"r\",\"type\":\"REPEAT\","
                + "\"count\":1,\"body\":[");
        for (int i = 0; i < RunRequest.MAX_BLOCKS; i++) {
            if (i > 0) {
                many.append(',');
            }
            many.append("{\"id\":\"w").append(i).append("\",\"type\":\"WAIT\"}");
        }
        many.append("]}]}");
        badRequest("入れ子の中も含めた合計がブロック数の上限(200)を超えると拒否", "PROGRAM_TOO_LONG", many.toString());
    }
}
