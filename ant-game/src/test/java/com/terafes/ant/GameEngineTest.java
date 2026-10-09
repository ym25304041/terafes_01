package com.terafes.ant;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameMap;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.Position;

import java.util.List;

/** GameEngine（移動・ジャンプ・判定）のテストです。 */
final class GameEngineTest {

    /**
     * テスト用マップ（座標は (x,y)）
     *   y=0:  . # . . . . . . .      壁 (1,0)
     *   y=1:  . . . . . . . . .
     *   y=2:  S . . . P . . . G      スタート(0,2) 穴(4,2) ゴール(8,2)
     *   y=3:  . . . . # . . . .      壁 (4,3)
     *   y=4:  . . . . . . . . P      穴 (8,4) （右下の隅）
     */
    private static GameMap map() {
        return GameMap.fromRows(List.of(
                ".#.......",
                ".........",
                "S...P...G",
                "....#....",
                "........P"
        ));
    }

    static void run() {
        T.group("GameEngine: 初期状態");
        GameEngine e = new GameEngine(map());
        T.eq("開始位置はスタート", new Position(0, 2), e.getPosition());
        T.eq("開始時はプレイ中", GameStatus.PLAYING, e.getStatus());
        T.check("開始位置はゴールではない", !e.isAtGoal());
        T.check("開始位置は穴ではない", !e.isOnPit());

        T.group("GameEngine: 上下左右の移動");
        e = new GameEngine(map());
        T.eq("右へ移動", ActionResult.MOVED, e.moveRight());
        T.eq("右へ移動後の位置 (1,2)", new Position(1, 2), e.getPosition());
        T.eq("上へ移動", ActionResult.MOVED, e.moveUp());
        T.eq("上へ移動後の位置 (1,1)", new Position(1, 1), e.getPosition());
        T.eq("左へ移動", ActionResult.MOVED, e.moveLeft());
        T.eq("左へ移動後の位置 (0,1)", new Position(0, 1), e.getPosition());
        T.eq("下へ移動", ActionResult.MOVED, e.moveDown());
        T.eq("下へ移動後の位置 (0,2)", new Position(0, 2), e.getPosition());
        T.eq("向きは最後に動いた方向(DOWN)", Direction.DOWN, e.getFacing());

        T.group("GameEngine: マップ外・壁には移動できない");
        e = new GameEngine(map());
        T.eq("左端から左へはマップ外", ActionResult.BLOCKED_OUT_OF_MAP, e.moveLeft());
        T.eq("動いていない (0,2)", new Position(0, 2), e.getPosition());
        T.eq("マップ外にぶつかってもゲームは続く", GameStatus.PLAYING, e.getStatus());
        e.moveUp();
        e.moveUp(); // (0,0) へ
        T.eq("(0,0) に到達", new Position(0, 0), e.getPosition());
        T.eq("上端から上へはマップ外", ActionResult.BLOCKED_OUT_OF_MAP, e.moveUp());
        T.eq("(0,0)から右は壁(1,0)", ActionResult.BLOCKED_WALL, e.moveRight());
        T.eq("壁には入っていない (0,0)", new Position(0, 0), e.getPosition());
        T.eq("壁にぶつかっても向きは更新される", Direction.RIGHT, e.getFacing());

        // 下端・右端の確認
        e = new GameEngine(map());
        e.moveDown();
        e.moveDown(); // (0,4)
        T.eq("下端から下はマップ外", ActionResult.BLOCKED_OUT_OF_MAP, e.moveDown());

        T.group("GameEngine: 落とし穴とゴール");
        e = new GameEngine(map());
        e.moveRight();
        e.moveRight();
        e.moveRight(); // (3,2)
        T.eq("穴の手前では playing", GameStatus.PLAYING, e.getStatus());
        T.eq("穴に歩いて入ると落下", ActionResult.FELL_INTO_PIT, e.moveRight());
        T.check("落下後は穴の上にいる(isOnPit)", e.isOnPit());
        T.eq("落下後は失敗状態", GameStatus.FAILED, e.getStatus());
        T.eq("失敗後の行動は無視される", ActionResult.GAME_ALREADY_OVER, e.moveLeft());
        T.eq("失敗後は位置が変わらない", new Position(4, 2), e.getPosition());

        // ゴールまで穴を迂回して到達 (上の行を通る)
        e = new GameEngine(map());
        e.moveUp(); // (0,1)
        for (int i = 0; i < 8; i++) {
            e.moveRight(); // (8,1)
        }
        T.eq("ゴールの1つ上まで来た", new Position(8, 1), e.getPosition());
        T.eq("下へ移動してゴール到達", ActionResult.REACHED_GOAL, e.moveDown());
        T.check("ゴール位置で isAtGoal が true", e.isAtGoal());
        T.eq("ゴール後はクリア状態", GameStatus.CLEARED, e.getStatus());
        T.eq("クリア後の行動は無視される", ActionResult.GAME_ALREADY_OVER, e.moveUp());

        T.group("GameEngine: ジャンプ");
        e = new GameEngine(map());
        e.moveRight();
        e.moveRight();
        e.moveRight(); // (3,2) 穴の手前
        T.eq("穴の手前から右ジャンプ成功", ActionResult.JUMPED, e.jumpRight());
        T.eq("着地は穴の2つ先 (5,2)", new Position(5, 2), e.getPosition());
        T.eq("ジャンプ後もプレイ中", GameStatus.PLAYING, e.getStatus());

        // 前方が穴でない場合は失敗
        e = new GameEngine(map());
        T.eq("前方が穴でなければジャンプ失敗", ActionResult.JUMP_FAILED_NO_PIT, e.jumpRight());
        T.eq("失敗したら動かない", new Position(0, 2), e.getPosition());
        T.eq("ジャンプ失敗でもゲームは続く", GameStatus.PLAYING, e.getStatus());
        T.eq("前方がマップ外でもジャンプ失敗", ActionResult.JUMP_FAILED_NO_PIT, e.jumpLeft());

        // 着地点が壁の場合は失敗: 穴(4,2)の下は (4,3) が壁。
        // (4,1) から下へ…は前方(4,2)が穴、着地(4,3)が壁
        e = new GameEngine(map());
        e.moveUp(); // (0,1)
        for (int i = 0; i < 4; i++) {
            e.moveRight(); // (4,1)
        }
        T.eq("(4,1)に到達", new Position(4, 1), e.getPosition());
        T.eq("着地点が壁ならジャンプ失敗", ActionResult.JUMP_FAILED_BLOCKED, e.jumpDown());
        T.eq("失敗したら動かない (4,1)", new Position(4, 1), e.getPosition());

        // 着地点がマップ外の場合は失敗: (8,3)は道、(8,4)は穴。(8,3)から下へ→前方(8,4)が穴、着地(8,5)はマップ外
        e = new GameEngine(map());
        e.moveDown();
        e.moveDown(); // (0,4)
        for (int i = 0; i < 7; i++) {
            e.moveRight(); // (7,4)
        }
        T.eq("(7,4)に到達", new Position(7, 4), e.getPosition());
        T.eq("着地点がマップ外ならジャンプ失敗", ActionResult.JUMP_FAILED_BLOCKED, e.jumpRight());

        // ジャンプで着地した先がゴール
        GameMap goalAfterPit = GameMap.fromRows(List.of(
                ".........", ".........", "S.P.G....", ".........", "........."));
        e = new GameEngine(goalAfterPit);
        e.moveRight(); // (1,2)
        T.eq("穴を飛び越えて(3,2)に着地", ActionResult.JUMPED, e.jumpRight());
        T.eq("さらに右へ進みゴール", ActionResult.REACHED_GOAL, e.moveRight());

        // ジャンプの着地点がゴール
        GameMap goalOnLanding = GameMap.fromRows(List.of(
                ".........", ".........", "SPG......", ".........", "........."));
        e = new GameEngine(goalOnLanding);
        T.eq("ジャンプ着地点がゴールならクリア", ActionResult.REACHED_GOAL, e.jumpRight());
        T.eq("クリア状態", GameStatus.CLEARED, e.getStatus());

        // ジャンプの着地点が穴 → 落下
        GameMap twoPits = GameMap.fromRows(List.of(
                ".........", ".........", "SPP.G....", ".........", "........."));
        e = new GameEngine(twoPits);
        T.eq("着地点も穴なら落下", ActionResult.FELL_INTO_PIT, e.jumpRight());
        T.eq("失敗状態", GameStatus.FAILED, e.getStatus());

        // 上下左右のジャンプメソッドが全て動くこと
        // 前方が道だけのマップでは、4方向とも「穴がない」ので失敗する
        e = new GameEngine(map());
        T.eq("上ジャンプ: 前方が道なので失敗", ActionResult.JUMP_FAILED_NO_PIT, e.jumpUp());
        T.eq("下ジャンプ: 前方が道なので失敗", ActionResult.JUMP_FAILED_NO_PIT, e.jumpDown());
        T.eq("左ジャンプ: 前方がマップ外なので失敗", ActionResult.JUMP_FAILED_NO_PIT, e.jumpLeft());
        T.eq("右ジャンプ: 前方が道なので失敗", ActionResult.JUMP_FAILED_NO_PIT, e.jumpRight());

        // 穴を挟んでゴールがあるマップで、4方向のジャンプ成功を確認する
        GameMap vertical = GameMap.fromRows(List.of(
                "....G....", "....P....", "....S....", "....P....", "....G...."));
        e = new GameEngine(vertical);
        T.eq("上ジャンプ成功しゴール", ActionResult.REACHED_GOAL, e.jumpUp());
        e = new GameEngine(vertical);
        T.eq("下ジャンプ成功しゴール", ActionResult.REACHED_GOAL, e.jumpDown());

        GameMap horizontal = GameMap.fromRows(List.of(
                ".........", ".........", "GP..S..PG", ".........", "........."));
        // S は (4,2)。穴は (1,2) と (7,2)。穴の手前まで2歩進んでからジャンプする
        e = new GameEngine(horizontal);
        e.moveLeft();
        e.moveLeft(); // (2,2)
        T.eq("左ジャンプ成功しゴール", ActionResult.REACHED_GOAL, e.jumpLeft());
        e = new GameEngine(horizontal);
        e.moveRight();
        e.moveRight(); // (6,2)
        T.eq("右ジャンプ成功しゴール", ActionResult.REACHED_GOAL, e.jumpRight());

        T.group("GameEngine: 失敗扱いにする");
        e = new GameEngine(map());
        e.markFailed();
        T.eq("markFailed で失敗状態", GameStatus.FAILED, e.getStatus());
    }
}
