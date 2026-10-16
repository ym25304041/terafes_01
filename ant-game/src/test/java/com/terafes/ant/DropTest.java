package com.terafes.ant;

import com.terafes.ant.api.RunRequest;
import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.EventType;
import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameEvent;
import com.terafes.ant.game.GameMap;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.ItemType;
import com.terafes.ant.game.Position;
import com.terafes.ant.program.Block;
import com.terafes.ant.program.BlockType;
import com.terafes.ant.program.ProgramRunner;
import com.terafes.ant.program.RunResult;

import java.util.List;

/** アイテムのスロットが1つであること、「アイテムを捨てる」コマンドのテストです。 */
final class DropTest {

    private static GameEngine engineOfRow2(String row2) {
        return new GameEngine(GameMap.fromRows(
                List.of(".........", ".........", row2, ".........", ".........")));
    }

    private static GameEvent dropped(ItemType item, int x, int y) {
        return new GameEvent(EventType.ITEM_DROPPED, item, x, y);
    }

    private static GameEvent pickup(ItemType item, int x, int y) {
        return new GameEvent(EventType.PICKUP, item, x, y);
    }

    static void run() {
        T.group("アイテムを捨てる: 基本");
        GameEngine e = engineOfRow2("SM......G");
        T.eq("最初は何も持っていない", null, e.getHeldItem());
        T.eq("何も持っていないと捨てられない", ActionResult.DROP_FAILED_NO_ITEM, e.dropItem());
        T.eq("失敗しても出来事は起きない", 0, e.getLastEvents().size());

        e.moveRight(); // (1,2) 物資を取得
        T.eq("物資を持っている", ItemType.SUPPLY, e.getHeldItem());
        T.check("取った場所の床にはもうない", !e.hasItemAt(new Position(1, 2)));
        T.eq("今いるマスに捨てる", ActionResult.DROPPED, e.dropItem());
        T.eq("捨てた出来事が記録される", List.of(dropped(ItemType.SUPPLY, 1, 2)), e.getLastEvents());
        T.eq("何も持たなくなる", null, e.getHeldItem());
        T.check("今いるマスにアイテムが置かれる", e.hasItemAt(new Position(1, 2)));

        e.waitOneTurn();
        T.eq("その場に立ったままでは、拾い直さない", null, e.getHeldItem());

        e.moveRight(); // (2,2)
        T.eq("離れても、置いたアイテムは床に残る", true, e.hasItemAt(new Position(1, 2)));
        e.moveLeft(); // (1,2) に戻る
        T.eq("そのマスに再び来ると、拾える", ItemType.SUPPLY, e.getHeldItem());
        T.eq("拾った出来事", List.of(pickup(ItemType.SUPPLY, 1, 2)), e.getLastEvents());
        T.check("拾うと床からなくなる", !e.hasItemAt(new Position(1, 2)));

        T.group("アイテムを捨てる: 捨てられない場合");
        e = engineOfRow2("SMM.....G");
        e.moveRight(); // (1,2) 物資を取得
        e.moveRight(); // (2,2) 手がふさがっているので、2つ目の物資は取れない
        T.eq("2つ目の物資は取れない", ItemType.SUPPLY, e.getHeldItem());
        T.check("2つ目の物資は床に残る", e.hasItemAt(new Position(2, 2)));
        T.eq("すでにアイテムがあるマスには捨てられない", ActionResult.DROP_FAILED_OCCUPIED, e.dropItem());
        T.eq("捨てられなかったので、まだ持っている", ItemType.SUPPLY, e.getHeldItem());
        T.eq("床のアイテムもそのまま", true, e.hasItemAt(new Position(2, 2)));

        T.group("アイテムを捨てる: 持ち替え（捨ててから、別のアイテムを拾う）");
        e = engineOfRow2("SWM.....G");
        e.moveRight(); // (1,2) 武器
        T.eq("武器を持った", ItemType.WEAPON, e.getHeldItem());
        e.moveRight(); // (2,2) 物資は拾えない
        T.eq("物資は拾えない", ItemType.WEAPON, e.getHeldItem());
        e.moveLeft();  // (1,2)
        T.eq("武器を捨てる", ActionResult.DROPPED, e.dropItem());
        e.moveRight(); // (2,2)
        T.eq("手ぶらなので物資を拾える", ItemType.SUPPLY, e.getHeldItem());
        T.eq("拾った出来事", List.of(pickup(ItemType.SUPPLY, 2, 2)), e.getLastEvents());

        T.group("アイテムを捨てる: 時間が進む・終了後は何もしない");
        GameEngine timed = new GameEngine(GameMap.fromRows(List.of(
                "....1....", "....1....", "..S.1...G", "....1....", "....1....")));
        T.eq("最初の敵は(4,0)", 0, timed.getEnemyStates().get(0).y());
        timed.dropItem();
        T.eq("捨てられなくても、1ステップ進んで敵が動く", 1, timed.getEnemyStates().get(0).y());
        e = engineOfRow2("SM......G");
        e.moveRight();
        e.markFailed();
        T.eq("ゲーム終了後は何もしない", ActionResult.GAME_ALREADY_OVER, e.dropItem());
        T.eq("持っているアイテムもそのまま", ItemType.SUPPLY, e.getHeldItem());

        T.group("アイテムを捨てる: 武器を捨てると敵に倒される");
        e = engineOfRow2("SWX.....G");
        e.moveRight(); // 武器
        T.eq("武器を捨てる", ActionResult.DROPPED, e.dropItem());
        T.eq("武器なしで敵のマスに進むと敗北", ActionResult.DEFEATED_BY_ENEMY, e.moveRight());

        e = engineOfRow2("SWX.....G");
        e.moveRight();    // (1,2) 武器を取得
        e.dropItem();     // 武器を捨てる
        e.moveLeft();     // (0,2)
        e.moveRight();    // (1,2) 拾い直す
        T.eq("拾い直した", ItemType.WEAPON, e.getHeldItem());
        e.moveRight();    // (2,2) 敵
        T.eq("拾い直した武器で敵を討伐できる", GameStatus.PLAYING, e.getStatus());
        T.eq("討伐の出来事", List.of(new GameEvent(EventType.ENEMY_DEFEATED, null, 2, 2)), e.getLastEvents());

        T.group("アイテムを捨てる: 物資を捨てるとスイッチが作動しない");
        e = engineOfRow2("SMaA....G");
        e.moveRight();    // (1,2) 物資
        e.dropItem();     // 物資を捨てる
        e.moveRight();    // (2,2) スイッチ。物資がないので作動しない
        T.check("ドアは閉じたまま", e.isClosedDoor(new Position(3, 2)));
        e.moveLeft();     // (1,2) 物資を拾い直す
        e.moveRight();    // (2,2) スイッチ: 作動する
        T.check("物資を拾い直せば、スイッチが作動してドアが開く", !e.isClosedDoor(new Position(3, 2)));

        T.group("ProgramRunner / RunRequest: 捨てるブロック");
        RunResult r = ProgramRunner.run(TestStages.stage(3), List.of(
                Block.moveBlock("a"), Block.moveBlock("b"), Block.dropBlock("d")));
        T.eq("3ステップ", 3, r.stepCount());
        T.eq("物資を拾った(2歩目)", ItemType.SUPPLY, r.trace().get(1).heldItem());
        T.eq("捨てた結果が記録される", ActionResult.DROPPED, r.trace().get(2).result());
        T.eq("捨てたあとは何も持たない", null, r.trace().get(2).heldItem());
        T.eq("トレースに捨てた出来事が入る", List.of(dropped(ItemType.SUPPLY, 2, 2)), r.trace().get(2).events());
        T.check("捨てるはコマンド(1回の行動)である", BlockType.DROP.isAction());

        r = ProgramRunner.run(TestStages.stage(3), List.of(Block.dropBlock("d")));
        T.eq("何も持たずに捨てても1ステップ数える", 1, r.stepCount());
        T.eq("結果は捨てられなかった", ActionResult.DROP_FAILED_NO_ITEM, r.trace().get(0).result());

        RunRequest req = RunRequest.parse("{\"stageId\":1,\"program\":["
                + "{\"id\":\"a\",\"type\":\"DROP\"},{\"id\":\"b\",\"type\":\"DROP\",\"dir\":\"UP\"}]}");
        T.eq("DROP ブロックを受け付ける", BlockType.DROP, req.program().get(0).type());
        T.eq("方向を付けても読まない", null, req.program().get(1).dir());
    }
}
