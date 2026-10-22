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
import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;
import com.terafes.ant.program.Block;
import com.terafes.ant.program.BlockType;
import com.terafes.ant.program.ProgramRunner;
import com.terafes.ant.program.RunResult;

import java.util.List;

/** アリの向き、「向く」ブロック、移動関数のテストです。 */
final class FacingTest {

    private static void badRequest(String name, String json) {
        try {
            RunRequest.parse(json);
            T.check(name + "（例外が出なかった）", false);
        } catch (BadRequestException e) {
            T.eq(name, "INVALID_BLOCK", e.getErrorCode());
        }
    }

    private static String program(String blocks) {
        return "{\"stageId\":1,\"program\":[" + blocks + "]}";
    }

    static void run() {
        Stage straight = TestStages.stage(1); // まっすぐ進む
        Stage winding = TestStages.stage(7);  // 上へ回り込んでから右へ進む
        Stage jumpStage = TestStages.stage(2);
        Stage patrol = TestStages.stage(6);

        T.group("エンジン: 向きと前への移動");
        GameMap map = straight.createMap();
        GameEngine e = new GameEngine(map);
        T.eq("最初の向きは右（指定なし）", Direction.RIGHT, e.getFacing());
        T.eq("最初の向きを指定できる", Direction.DOWN, new GameEngine(map, Direction.DOWN).getFacing());

        T.eq("向くと TURNED が返る", ActionResult.TURNED, e.turn(Direction.DOWN));
        T.eq("向きが変わる", Direction.DOWN, e.getFacing());
        T.eq("向いただけでは動かない", new Position(0, 2), e.getPosition());
        T.eq("前へ移動は向いている方向(下)へ進む", ActionResult.MOVED, e.moveForward());
        T.eq("下へ1マス進んだ", new Position(0, 3), e.getPosition());
        e.turn(Direction.UP);
        e.moveForward();
        e.moveForward();
        e.moveForward();
        T.eq("上を向いて3回前へ移動", new Position(0, 0), e.getPosition());
        e.turn(Direction.RIGHT);
        e.moveForward();
        T.eq("右を向いて前へ移動すると右へ進む", new Position(1, 0), e.getPosition());
        T.eq("向きは進んだあとも保たれる", Direction.RIGHT, e.getFacing());

        e = new GameEngine(map);
        e.turn(Direction.LEFT);
        T.eq("左端で左へ進むとマップ外にぶつかる", ActionResult.BLOCKED_OUT_OF_MAP, e.moveForward());
        T.eq("ぶつかっても向きは保たれる", Direction.LEFT, e.getFacing());

        T.group("エンジン: 向きとジャンプ・条件");
        GameMap pitMap = GameMap.fromRows(List.of(
                ".........", ".........", "S...P...G", ".........", "........."));
        e = new GameEngine(pitMap);
        e.moveForward();
        e.moveForward();
        e.moveForward(); // (3,2) 右向き。目の前は穴
        T.check("向いている方向の目の前が穴", e.check(ConditionType.PIT_AHEAD));
        T.eq("前へジャンプで穴を越える", ActionResult.JUMPED, e.jumpForward());
        T.eq("着地は穴の2つ先", new Position(5, 2), e.getPosition());

        GameMap vertical = GameMap.fromRows(List.of(
                "....G....", "....P....", "....S....", ".........", "........."));
        e = new GameEngine(vertical);
        T.eq("右向きのままジャンプしても、前は穴ではないので失敗", ActionResult.JUMP_FAILED_NO_PIT, e.jumpForward());
        e.turn(Direction.UP);
        T.check("上を向くと、目の前が穴になる", e.check(ConditionType.PIT_AHEAD));
        T.eq("上を向いてジャンプすると成功してゴール", ActionResult.REACHED_GOAL, e.jumpForward());

        e = new GameEngine(map);
        T.check("右向きの目の前は壁ではない", !e.check(ConditionType.WALL_AHEAD));
        e.turn(Direction.LEFT);
        T.check("左を向くと、目の前はマップの端(壁扱い)", e.check(ConditionType.WALL_AHEAD));

        T.group("エンジン: 向くのも1回の行動（時間が進む）");
        GameEngine timed = new GameEngine(patrol.createMap(), patrol.startFacing());
        T.eq("最初の敵は(4,0)", 0, timed.getEnemyStates().get(0).y());
        timed.turn(Direction.RIGHT);
        T.eq("向くと敵も1マス動く", 1, timed.getEnemyStates().get(0).y());
        timed.moveForward();
        timed.turn(Direction.UP);
        T.eq("向く2回目でもさらに動く", 3, timed.getEnemyStates().get(0).y());
        e = new GameEngine(map);
        e.turn(Direction.UP);
        e.markFailed();
        T.eq("ゲーム終了後は向けない", ActionResult.GAME_ALREADY_OVER, e.turn(Direction.DOWN));
        T.eq("向きも変わらない", Direction.UP, e.getFacing());

        // 向いている間にも時間は進むので、ゾーンの中で向いていると、戻ってきた敵が入ってくる
        GameEngine hit = new GameEngine(patrol.createMap(), patrol.startFacing());
        hit.moveForward();   // t1 アリ(3,2) 敵(4,1)
        hit.waitOneTurn();   // t2 敵(4,2)
        hit.waitOneTurn();   // t3 敵(4,3)
        hit.moveForward();   // t4 アリ(4,2) 敵(4,4)
        T.eq("t5 に上を向く間は、まだ無事", ActionResult.TURNED, hit.turn(Direction.UP)); // 敵(4,3)
        T.eq("t6 に下を向く間に、敵が戻ってきて敗北", ActionResult.DEFEATED_BY_ENEMY, hit.turn(Direction.DOWN));

        T.group("ProgramRunner: 向く・移動（向きを使う）");
        RunResult r = ProgramRunner.run(winding, List.of(
                Block.turnBlock("t1", Direction.UP), Block.moveBlock("m1")));
        T.eq("向く(1) + 移動(1) = 2ステップ", 2, r.stepCount());
        T.eq("1ステップ目は向きを変えた", ActionResult.TURNED, r.trace().get(0).result());
        T.eq("トレースに向きが入る", Direction.UP, r.trace().get(0).facing());
        T.eq("上へ1マス進んでいる", 1, r.trace().get(1).y());

        r = ProgramRunner.run(winding, List.of(new Block("m", BlockType.MOVE, Direction.LEFT)));
        T.eq("移動ブロックに方向を付けても、向いている方向(右)を使う", ActionResult.BLOCKED_WALL, r.trace().get(0).result());

        r = ProgramRunner.run(TestStages.stage1Facing(Direction.DOWN), List.of(Block.moveBlock("m")));
        T.eq("ステージの最初の向きが使われる(下)", 3, r.trace().get(0).y());
        T.eq("最初の向きがトレースに入る", Direction.DOWN, r.trace().get(0).facing());

        T.group("ProgramRunner: 移動関数（方向と移動回数）");
        r = ProgramRunner.run(winding, List.of(
                Block.moveFunction("f1", Direction.UP, 2),
                Block.moveFunction("f2", Direction.RIGHT, 5),
                Block.moveFunction("f3", Direction.DOWN, 2),
                Block.moveFunction("f4", Direction.RIGHT, 3)));
        T.eq("4つの関数でゴールに着く", GameStatus.CLEARED, r.status());
        T.eq("(向く1+移動2)+(1+5)+(1+2)+(1+3) = 16ステップ", 16, r.stepCount());
        T.eq("関数の1ステップ目は向く", ActionResult.TURNED, r.trace().get(0).result());
        T.eq("関数の2ステップ目は移動", ActionResult.MOVED, r.trace().get(1).result());
        T.eq("関数のステップには関数ブロックのIDが入る", "f1", r.trace().get(2).blockId());
        T.eq("次の関数のIDに切り替わる", "f2", r.trace().get(3).blockId());
        T.eq("最後に右向き", Direction.RIGHT, r.trace().get(15).facing());
        T.eq("ゴールの座標", new Position(8, 2), new Position(r.trace().get(15).x(), r.trace().get(15).y()));

        // コマンドを並べたものと、同じ結果になる
        RunResult byCommands = ProgramRunner.run(winding, List.of(
                Block.turnBlock("a", Direction.UP), Block.moveBlock("b"), Block.moveBlock("c")));
        RunResult byFunction = ProgramRunner.run(winding, List.of(Block.moveFunction("f", Direction.UP, 2)));
        T.eq("関数はコマンドを並べたのと同じ位置になる",
                byCommands.trace().get(2).y(), byFunction.trace().get(2).y());
        T.eq("ステップ数も同じ", byCommands.stepCount(), byFunction.stepCount());

        r = ProgramRunner.run(straight, List.of(Block.moveFunction("f", Direction.LEFT, 3)));
        T.eq("壁・マップ外で動けなくても、残りの移動を続ける(向く1+移動3)", 4, r.stepCount());
        T.eq("ぶつかった結果が記録される", ActionResult.BLOCKED_OUT_OF_MAP, r.trace().get(3).result());

        r = ProgramRunner.run(jumpStage, List.of(Block.moveFunction("f", Direction.RIGHT, 8)));
        T.eq("途中で穴に落ちたら、そこで止まる", GameStatus.FAILED, r.status());
        T.eq("理由は穴への落下", ProgramRunner.FAIL_FELL_INTO_PIT, r.failReason());
        T.eq("向く1 + 移動4 = 5ステップで落下", 5, r.stepCount());

        r = ProgramRunner.run(straight, List.of(Block.moveFunction("f", Direction.RIGHT, 20)));
        T.eq("ゴールに着いたら、関数の残りの移動は実行しない", GameStatus.CLEARED, r.status());
        T.eq("向く1 + 移動8 = 9ステップ", 9, r.stepCount());

        r = ProgramRunner.run(winding, List.of(Block.moveFunction("f", Direction.RIGHT, 100)));
        T.eq("関数でもステップ数の上限(50)で止まる", ProgramRunner.FAIL_STEP_LIMIT, r.failReason());
        T.eq("ステップ数は50", 50, r.stepCount());

        r = ProgramRunner.run(straight, List.of(
                Block.repeatBlock("rep", 2, List.of(Block.moveFunction("f", Direction.RIGHT, 2)))));
        T.eq("くり返しの中でも関数を使える: (1+2)×2 = 6ステップ", 6, r.stepCount());
        T.eq("4マス進んでいる", 4, r.trace().get(5).x());

        T.group("ステージ6: 向きと関数を使って敵の前を横切る");
        r = ProgramRunner.run(patrol, List.of(Block.moveFunction("f", Direction.RIGHT, 5)));
        T.eq("待たずに関数で進むと、敵とぶつかって失敗", ProgramRunner.FAIL_DEFEATED_BY_ENEMY, r.failReason());
        T.eq("3ステップ目で敗北", 3, r.stepCount());

        r = ProgramRunner.run(patrol, List.of(
                Block.moveBlock("m1"),
                Block.whileBlock("wh", ConditionType.ENEMY_COMING, false, List.of(Block.waitBlock("w"))),
                Block.moveFunction("f", Direction.RIGHT, 5)));
        T.eq("敵が通り過ぎるのを待ってから関数で進めばクリア", GameStatus.CLEARED, r.status());
        T.eq("1 + 待つ2 + 関数(向く1+移動5) = 9ステップ", 9, r.stepCount());

        T.group("ステージファイル: facing の指定");
        String base = "name: 向きテスト\nmap:\nS.......G\n.........\n.........\n.........\n.........";
        T.eq("省略すると右向き", Direction.RIGHT, StageRepository.parse(1, base).startFacing());
        T.eq("down を指定", Direction.DOWN,
                StageRepository.parse(1, "name: x\nfacing: down\nmap:\nS.......G\n.........\n.........\n.........\n.........")
                        .startFacing());
        T.eq("大文字でも読める", Direction.UP,
                StageRepository.parse(1, "facing:  UP \nname: x\nmap:\nS.......G\n.........\n.........\n.........\n.........")
                        .startFacing());
        T.throwsError("不正な向きは例外", IllegalArgumentException.class,
                () -> StageRepository.parse(1, "name: x\nfacing: north\nmap:\nS.......G\n.........\n.........\n.........\n........."));

        T.group("RunRequest: 向く・移動関数の検査");
        RunRequest req = RunRequest.parse(program(
                "{\"id\":\"a\",\"type\":\"TURN\",\"dir\":\"DOWN\"},"
                        + "{\"id\":\"b\",\"type\":\"MOVE\"},"
                        + "{\"id\":\"c\",\"type\":\"JUMP\",\"dir\":\"LEFT\"},"
                        + "{\"id\":\"d\",\"type\":\"MOVE_FUNCTION\",\"dir\":\"UP\",\"count\":4}"));
        T.eq("向くブロックの方向", Direction.DOWN, req.program().get(0).dir());
        T.eq("移動ブロックは方向を持たない", null, req.program().get(1).dir());
        T.eq("ジャンプに付けた方向は読まない", null, req.program().get(2).dir());
        Block fn = req.program().get(3);
        T.eq("移動関数の種類", BlockType.MOVE_FUNCTION, fn.type());
        T.eq("移動関数の方向", Direction.UP, fn.dir());
        T.eq("移動関数の回数", 4, fn.count());
        T.check("関数ブロックである", fn.type().isFunction());
        T.check("向くはコマンド(行動)である", BlockType.TURN.isAction());
        T.check("関数はコマンド(1回の行動)ではない", !BlockType.MOVE_FUNCTION.isAction());

        badRequest("向くブロックに方向がない", program("{\"id\":\"a\",\"type\":\"TURN\"}"));
        badRequest("向くブロックの方向が不正", program("{\"id\":\"a\",\"type\":\"TURN\",\"dir\":\"north\"}"));
        badRequest("移動関数に方向がない", program("{\"id\":\"a\",\"type\":\"MOVE_FUNCTION\",\"count\":2}"));
        badRequest("移動関数の方向が不正", program("{\"id\":\"a\",\"type\":\"MOVE_FUNCTION\",\"dir\":\"x\",\"count\":2}"));
        badRequest("移動関数に回数がない", program("{\"id\":\"a\",\"type\":\"MOVE_FUNCTION\",\"dir\":\"UP\"}"));
        badRequest("移動関数の回数が0", program("{\"id\":\"a\",\"type\":\"MOVE_FUNCTION\",\"dir\":\"UP\",\"count\":0}"));
        badRequest("移動関数の回数が上限(100)超え",
                program("{\"id\":\"a\",\"type\":\"MOVE_FUNCTION\",\"dir\":\"UP\",\"count\":101}"));
        badRequest("移動関数の回数が文字列",
                program("{\"id\":\"a\",\"type\":\"MOVE_FUNCTION\",\"dir\":\"UP\",\"count\":\"2\"}"));
        T.eq("移動関数の回数100は受け付ける", 100, RunRequest.parse(
                program("{\"id\":\"a\",\"type\":\"MOVE_FUNCTION\",\"dir\":\"UP\",\"count\":100}")).program().get(0).count());
    }
}
