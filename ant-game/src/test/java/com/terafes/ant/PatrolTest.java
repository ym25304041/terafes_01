package com.terafes.ant;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.ConditionType;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.EnemyState;
import com.terafes.ant.game.EventType;
import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameEvent;
import com.terafes.ant.game.GameMap;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.PatrolRoute;
import com.terafes.ant.game.Position;

import java.util.List;

/** 接敵ゾーンと巡回する敵（時間の進み方・戦闘の順番・条件の判定）のテストです。 */
final class PatrolTest {

    private static GameMap mapOf(String... rows) {
        return GameMap.fromRows(List.of(rows));
    }

    /** ステージ6と同じ、縦のゾーン(x=4, y=0〜4)を横切るマップ。S は (2,2)。 */
    private static GameMap verticalMap() {
        return mapOf("....1....", "....1....", "..S.1...G", "....1....", "....1....");
    }

    /** 生きている敵の位置を "x,y" の文字列のリストで返す（比較しやすくするため）。 */
    private static List<String> enemyAt(GameEngine e) {
        return e.getEnemyStates().stream().map(s -> s.x() + "," + s.y()).toList();
    }

    static void run() {
        T.group("GameMap: 接敵ゾーンの読み込み");
        GameMap m = mapOf(".........", ".........", "S..1111.G", "....2....", "....2....");
        T.eq("ゾーンは2つ", 2, m.getPatrols().size());
        PatrolRoute z1 = m.getPatrols().get(0);
        T.eq("1つ目はゾーン1", '1', z1.zoneId());
        T.eq("横ゾーンは4マス", 4, z1.path().size());
        T.eq("経路の先頭は左端", new Position(3, 2), z1.path().get(0));
        T.eq("経路の末尾は右端", new Position(6, 2), z1.path().get(3));
        PatrolRoute z2 = m.getPatrols().get(1);
        T.eq("縦ゾーンは上から下", List.of(new Position(4, 3), new Position(4, 4)), z2.path());
        T.check("ゾーンのマスは接敵ゾーン", m.isZone(new Position(5, 2)));
        T.check("ゾーンのマスは壁ではない", !m.isWall(new Position(5, 2)));
        T.check("ゾーンでないマスは接敵ゾーンではない", !m.isZone(new Position(0, 2)));
        T.check("ゾーンのないマップは巡回経路が空",
                mapOf("S.......G", ".........", ".........", ".........", ".........").getPatrols().isEmpty());
        T.throwsError("ゾーンが1マスだけだと例外", IllegalArgumentException.class,
                () -> mapOf("S.......G", ".1.......", ".........", ".........", "........."));
        T.throwsError("ゾーンがL字だと例外", IllegalArgumentException.class,
                () -> mapOf("S.......G", ".11......", ".1.......", ".........", "........."));
        T.throwsError("ゾーンが途切れていると例外", IllegalArgumentException.class,
                () -> mapOf("S.......G", ".1.1.....", ".........", ".........", "........."));
        T.throwsError("範囲外の数字(6)は例外", IllegalArgumentException.class,
                () -> mapOf("S.......G", ".66......", ".........", ".........", "........."));
        T.throwsError("ゾーンの経路は書き換えられない", UnsupportedOperationException.class,
                () -> m.getPatrols().clear());

        T.group("巡回する敵: 1ステップに1マス、端で即折り返し");
        GameEngine e = new GameEngine(mapOf(".........", ".........", "S..1111.G", ".........", "........."));
        T.eq("最初は先頭(左端)にいる", List.of("3,2"), enemyAt(e));
        T.eq("敵のIDはゾーン番号", "z1", e.getEnemyStates().get(0).id());
        // アリはスタートで待ち続ける（敵は届かない）。待つたびに敵が1マス進む
        String[] expected = {"4,2", "5,2", "6,2", "5,2", "4,2", "3,2", "4,2"};
        for (int i = 0; i < expected.length; i++) {
            T.eq("待った結果 " + (i + 1) + " 回目", ActionResult.WAITED, e.waitOneTurn());
            T.eq("敵の位置 " + (i + 1) + " ステップ後", List.of(expected[i]), enemyAt(e));
        }
        T.eq("アリはその場から動かない", new Position(0, 2), e.getPosition());

        T.group("巡回する敵: 縦のゾーンは上から下へ動き始める");
        e = new GameEngine(verticalMap());
        T.eq("縦ゾーンの最初は上端", List.of("4,0"), enemyAt(e));
        e.waitOneTurn();
        T.eq("1ステップ後は下へ1マス", List.of("4,1"), enemyAt(e));

        T.group("時間: 動けなかった行動・ジャンプ失敗も1ステップ進む");
        e = new GameEngine(verticalMap());
        e.moveUp();
        e.moveUp(); // (2,0)
        T.eq("2回行動で敵は2マス進む", List.of("4,2"), enemyAt(e));
        e.moveUp(); // マップ外にぶつかる
        T.eq("動けなくても時間は進む", List.of("4,3"), enemyAt(e));
        e.jumpRight(); // 前が穴でないので失敗
        T.eq("ジャンプ失敗でも時間は進む", List.of("4,4"), enemyAt(e));
        List<String> before = enemyAt(e);
        e.check(ConditionType.ENEMY_COMING);
        e.check(ConditionType.ENEMY_AHEAD);
        e.check(ConditionType.WALL_AHEAD);
        T.eq("条件を調べるだけでは時間は進まない(敵が動かない)", before, enemyAt(e));

        T.group("戦闘の順番: アリが先に動き、そのあとで敵が動く");
        e = new GameEngine(verticalMap());
        T.eq("1歩目は安全", ActionResult.MOVED, e.moveRight()); // アリ(3,2) 敵(4,1)
        T.eq("2歩目は、アリが(4,2)へ進んだあと、敵が(4,2)へ入ってきて敗北",
                ActionResult.DEFEATED_BY_ENEMY, e.moveRight());
        T.eq("アリは(4,2)に進んでいる", new Position(4, 2), e.getPosition());
        T.eq("武器がないので失敗状態", GameStatus.FAILED, e.getStatus());
        T.eq("敗北後の行動は無視される(時間も進まない)", ActionResult.GAME_ALREADY_OVER, e.waitOneTurn());

        T.group("戦闘: ゾーンの中で待っていると、戻ってきた敵が入ってくる");
        e = new GameEngine(verticalMap());
        e.moveRight();   // t1 アリ(3,2) 敵(4,1)
        e.waitOneTurn(); // t2 敵(4,2)
        e.waitOneTurn(); // t3 敵(4,3)
        T.eq("敵が通り過ぎたあとで進めば安全", ActionResult.MOVED, e.moveRight()); // t4 アリ(4,2) 敵(4,4)
        T.eq("ゾーンの中でも、敵と離れていれば無事", GameStatus.PLAYING, e.getStatus());
        e.waitOneTurn(); // t5 敵(4,3)
        T.eq("まだ無事", GameStatus.PLAYING, e.getStatus());
        T.eq("t6 で敵が戻ってきてアリのマスに入り、敗北", ActionResult.DEFEATED_BY_ENEMY, e.waitOneTurn());

        T.group("戦闘: 武器があれば、敵が入ってきても討伐できる");
        GameEngine armed = new GameEngine(
                mapOf("W...1....", "....1....", "S...1...G", "....1....", "....1...."));
        armed.moveUp();
        armed.moveUp(); // t2 (0,0) 武器
        T.check("武器を取得", armed.hasWeapon());
        for (int i = 0; i < 4; i++) {
            armed.moveRight(); // t3〜t6 (4,0)へ
        }
        T.eq("ゾーンの端(4,0)に着いた", new Position(4, 0), armed.getPosition());
        T.eq("敵はまだ生きている", 1, armed.getEnemyStates().size());
        armed.waitOneTurn(); // t7 敵(4,1)
        T.eq("まだ戦闘していない", List.<GameEvent>of(), armed.getLastEvents());
        ActionResult last = armed.waitOneTurn(); // t8 敵が(4,0)へ入ってくる
        T.eq("敵が入ってきても武器があるので無事", GameStatus.PLAYING, armed.getStatus());
        T.eq("行動の結果は待った(WAITED)のまま", ActionResult.WAITED, last);
        T.eq("討伐の出来事", List.of(new GameEvent(EventType.ENEMY_DEFEATED, null, 4, 0)), armed.getLastEvents());
        T.eq("敵はいなくなった", List.<EnemyState>of(), armed.getEnemyStates());
        armed.waitOneTurn();
        T.eq("討伐後はゾーンが空で、何も起きない", GameStatus.PLAYING, armed.getStatus());

        T.group("戦闘: アリが先に敵のいるマスへ入る場合（武器あり）");
        armed = new GameEngine(mapOf("W1111...G", ".........", "S........", ".........", "........."));
        armed.moveUp();
        armed.moveUp(); // t2 (0,0) 武器。敵は(3,0)
        armed.moveRight(); // t3 アリ(1,0) 敵(4,0)
        armed.moveRight(); // t4 アリ(2,0) 敵は折り返して(3,0)
        T.eq("t4 のあと、敵は(3,0)", List.of("3,0"), enemyAt(armed));
        T.eq("敵のいる(3,0)へ進むと討伐", ActionResult.MOVED, armed.moveRight());
        T.eq("討伐の出来事", List.of(new GameEvent(EventType.ENEMY_DEFEATED, null, 3, 0)), armed.getLastEvents());
        T.eq("敵はいなくなった", 0, armed.getEnemyStates().size());

        T.group("戦闘: アリが先に敵のいるマスへ入る場合（武器なし）");
        e = new GameEngine(mapOf("S1111...G", ".........", ".........", ".........", "........."));
        T.eq("ゾーンの端の敵(1,0)のマスへ進むと敗北", ActionResult.DEFEATED_BY_ENEMY, e.moveRight());
        List<String> frozen = enemyAt(e);
        e.waitOneTurn();
        T.eq("敗北後は敵も動かない", frozen, enemyAt(e));

        T.group("条件の判定（目の前は、アリが向いている方向の1マス先）");
        e = new GameEngine(verticalMap());
        T.eq("最初は右向き", Direction.RIGHT, e.getFacing());
        e.moveRight(); // アリ(3,2) 敵(4,1)。目の前は(4,2)
        T.check("敵は目の前に「いま」はいない", !e.check(ConditionType.ENEMY_AHEAD));
        T.check("次のステップで入ってくるので、敵が来る", e.check(ConditionType.ENEMY_COMING));
        e.waitOneTurn(); // 敵(4,2)
        T.check("敵がいま目の前にいる", e.check(ConditionType.ENEMY_AHEAD));
        T.check("いま目の前にいるときも、敵が来るは真", e.check(ConditionType.ENEMY_COMING));
        e.waitOneTurn(); // 敵(4,3)、次は(4,4)
        T.check("敵が通り過ぎたあとは、いまいない", !e.check(ConditionType.ENEMY_AHEAD));
        T.check("次も来ないので、敵が来るは偽（安全）", !e.check(ConditionType.ENEMY_COMING));

        e = new GameEngine(mapOf("S........", ".........", "..P.....G", ".#.......", "........."));
        T.check("最初の目の前(右)は壁ではない", !e.check(ConditionType.WALL_AHEAD));
        e.moveLeft(); // マップ外にぶつかり、左を向く
        T.check("マップの端は壁として扱う", e.check(ConditionType.WALL_AHEAD));
        e.moveDown();
        e.moveDown(); // (0,2)
        e.moveRight(); // (1,2) 右を向く。目の前は穴(2,2)
        T.check("目の前が穴", e.check(ConditionType.PIT_AHEAD));
        T.check("穴は壁ではない", !e.check(ConditionType.WALL_AHEAD));
        e.moveDown(); // (1,3) は壁 → 動けず下を向く
        T.check("目の前が壁", e.check(ConditionType.WALL_AHEAD));
        T.check("壁の前は穴ではない", !e.check(ConditionType.PIT_AHEAD));

        e = new GameEngine(mapOf("S.X......", ".........", "........G", ".........", "........."));
        T.check("最初は目の前(1,0)に敵はいない", !e.check(ConditionType.ENEMY_AHEAD));
        e.moveRight(); // (1,0)
        T.check("動かない敵が目の前にいる", e.check(ConditionType.ENEMY_AHEAD));
        T.check("動かない敵は、敵が来るでも真", e.check(ConditionType.ENEMY_COMING));

        T.group("動かない敵は時間が進んでも動かない");
        e = new GameEngine(mapOf("S.X......", ".........", "........G", ".........", "........."));
        T.eq("動かない敵のID", "x0", e.getEnemyStates().get(0).id());
        e.waitOneTurn();
        e.waitOneTurn();
        T.eq("2ステップ後も同じ位置", List.of("2,0"), enemyAt(e));
    }
}
