package com.terafes.ant;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.EventType;
import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameEvent;
import com.terafes.ant.game.GameMap;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.Position;
import com.terafes.ant.game.StageRepository;
import com.terafes.ant.program.Block;
import com.terafes.ant.program.BlockType;
import com.terafes.ant.program.ProgramRunner;
import com.terafes.ant.program.RunResult;

import java.util.ArrayList;
import java.util.List;

/** 敵（武器があれば討伐、なければ敗北）のテストです。 */
final class EnemyTest {

    /** y=2 の行だけを指定して、残りは空の道にしたマップのエンジンを作る補助。 */
    private static GameEngine engineOfRow2(String row2) {
        return new GameEngine(GameMap.fromRows(
                List.of(".........", ".........", row2, ".........", ".........")));
    }

    private static GameEvent defeated(int x, int y) {
        return new GameEvent(EventType.ENEMY_DEFEATED, null, x, y);
    }

    private static Block move(String id, Direction d) {
        return new Block(id, BlockType.MOVE, d);
    }

    static void run() {
        T.group("GameMap: 敵の読み込み");
        GameMap m = GameMap.fromRows(List.of(
                ".........", ".........", "SWX.X...G", ".........", "........X"));
        T.eq("敵は3体", 3, m.getInitialEnemies().size());
        T.check("(2,2) に敵がいる", m.getInitialEnemies().contains(new Position(2, 2)));
        T.check("(8,4) に敵がいる", m.getInitialEnemies().contains(new Position(8, 4)));
        T.check("敵のいるマスの地形は道（壁ではない）", !m.isWall(new Position(2, 2)));
        T.throwsError("敵の初期配置は書き換えられない", UnsupportedOperationException.class,
                () -> m.getInitialEnemies().clear());
        T.check("ドアの記号 E は敵と区別される", GameMap.fromRows(List.of(
                ".........", ".........", "SeE.....G", ".........", ".........")).getInitialEnemies().isEmpty());

        T.group("敵: 武器があれば討伐して進める");
        GameEngine e = engineOfRow2("SWX.....G");
        e.moveRight(); // 武器を取得
        T.check("武器を持った", e.hasWeapon());
        T.check("敵がまだいる", e.hasEnemyAt(new Position(2, 2)));
        T.eq("敵のマスへ移動すると進める", ActionResult.MOVED, e.moveRight());
        T.eq("討伐の出来事が記録される", List.of(defeated(2, 2)), e.getLastEvents());
        T.eq("敵のマスに進んでいる", new Position(2, 2), e.getPosition());
        T.eq("ゲームは続く", GameStatus.PLAYING, e.getStatus());
        T.check("敵はマップからいなくなった", !e.hasEnemyAt(new Position(2, 2)));
        T.check("武器は消費されない", e.hasWeapon());
        e.moveLeft();
        e.moveRight();
        T.eq("討伐済みのマスに戻っても戦闘は起きない", 0, e.getLastEvents().size());
        T.eq("ゲームは続いたまま", GameStatus.PLAYING, e.getStatus());

        T.group("敵: 武器がなければ敗北");
        e = engineOfRow2("SX......G");
        T.eq("武器なしで敵のマスへ進むと敗北", ActionResult.DEFEATED_BY_ENEMY, e.moveRight());
        T.eq("ゲームオーバー", GameStatus.FAILED, e.getStatus());
        T.eq("敵のマスまで進んでいる", new Position(1, 2), e.getPosition());
        T.eq("討伐の出来事は起きない", 0, e.getLastEvents().size());
        T.check("敵は残ったまま", e.hasEnemyAt(new Position(1, 2)));
        T.eq("敗北後の行動は無視される", ActionResult.GAME_ALREADY_OVER, e.moveLeft());

        T.group("敵: 武器は1つで何体でも倒せる");
        e = engineOfRow2("SWXX....G");
        e.moveRight();
        T.eq("1体目を討伐", ActionResult.MOVED, e.moveRight());
        T.eq("2体目も討伐", List.of(defeated(3, 2)), moveAndEvents(e));
        T.check("2体ともいなくなった", !e.hasEnemyAt(new Position(2, 2)) && !e.hasEnemyAt(new Position(3, 2)));
        T.check("武器はまだ持っている", e.hasWeapon());

        T.group("敵: 物資だけでは討伐できない");
        e = engineOfRow2("SMX.....G");
        e.moveRight();
        T.check("物資を持った", e.hasSupply() && !e.hasWeapon());
        T.eq("物資だけでは敗北する", ActionResult.DEFEATED_BY_ENEMY, e.moveRight());

        T.group("敵: ジャンプの着地点にいる場合");
        GameMap jumpMap = GameMap.fromRows(List.of(
                "W........", ".........", "S.PX....G", ".........", "........."));
        e = new GameEngine(jumpMap);
        e.moveRight(); // (1,2)
        T.eq("武器なしで着地点の敵に着くと敗北", ActionResult.DEFEATED_BY_ENEMY, e.jumpRight());
        T.eq("ゲームオーバー", GameStatus.FAILED, e.getStatus());

        e = new GameEngine(jumpMap);
        e.moveUp();
        e.moveUp(); // (0,0) 武器
        e.moveDown();
        e.moveDown();
        e.moveRight(); // (1,2)
        T.eq("武器があれば着地点の敵を討伐してジャンプ成功", ActionResult.JUMPED, e.jumpRight());
        T.eq("討伐の出来事", List.of(defeated(3, 2)), e.getLastEvents());
        T.eq("着地している", new Position(3, 2), e.getPosition());

        T.group("敵: エンジンの独立性");
        GameMap shared = GameMap.fromRows(List.of(
                ".........", ".........", "SWX.....G", ".........", "........."));
        GameEngine first = new GameEngine(shared);
        first.moveRight();
        first.moveRight();
        GameEngine second = new GameEngine(shared);
        T.check("別のエンジンでは敵が残っている", second.hasEnemyAt(new Position(2, 2)));

        T.group("ステージ5（ファイルから読み込み）");
        var stage5 = StageRepository.findById(5).orElseThrow();
        List<Block> solution = new ArrayList<>();
        Direction[] route = {Direction.UP, Direction.UP, Direction.RIGHT, Direction.DOWN, Direction.DOWN,
                Direction.RIGHT, Direction.RIGHT, Direction.RIGHT, Direction.RIGHT, Direction.RIGHT,
                Direction.RIGHT, Direction.RIGHT};
        for (int i = 0; i < route.length; i++) {
            solution.add(move("s" + i, route[i]));
        }
        RunResult r = ProgramRunner.run(stage5, solution);
        T.eq("武器を取って2体を倒してクリア", GameStatus.CLEARED, r.status());
        T.eq("ステップ数は12", 12, r.stepCount());
        T.eq("6歩目で1体目を討伐", List.of(defeated(2, 2)), r.trace().get(5).events());
        T.eq("8歩目で2体目を討伐", List.of(defeated(4, 2)), r.trace().get(7).events());
        T.check("最後も武器を持っている", r.trace().get(11).hasWeapon());

        r = ProgramRunner.run(stage5, List.of(move("a", Direction.RIGHT), move("b", Direction.RIGHT),
                move("c", Direction.RIGHT)));
        T.eq("武器を取らずに進むと失敗", GameStatus.FAILED, r.status());
        T.eq("理由は敵に敗北", ProgramRunner.FAIL_DEFEATED_BY_ENEMY, r.failReason());
        T.eq("敗北した時点で止まる(2ステップ)", 2, r.stepCount());
        T.eq("敗北の結果が記録される", ActionResult.DEFEATED_BY_ENEMY, r.trace().get(1).result());
    }

    /** 右へ1マス動き、その行動で起きた出来事を返す補助。 */
    private static List<GameEvent> moveAndEvents(GameEngine e) {
        e.moveRight();
        return e.getLastEvents();
    }
}
