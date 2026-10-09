package com.terafes.ant;

import com.terafes.ant.game.ActionResult;
import com.terafes.ant.game.Direction;
import com.terafes.ant.game.EventType;
import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameEvent;
import com.terafes.ant.game.GameMap;
import com.terafes.ant.game.GameStatus;
import com.terafes.ant.game.ItemType;
import com.terafes.ant.game.Position;
import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;
import com.terafes.ant.program.Block;
import com.terafes.ant.program.BlockType;
import com.terafes.ant.program.ProgramRunner;
import com.terafes.ant.program.RunResult;

import java.util.ArrayList;
import java.util.List;

/** アイテム（物資・武器）とスイッチ・ドアのテストです。 */
final class ItemGimmickTest {

    /** 5行のマップ文字列から、2行目(y=2)以外を空の道で埋めたマップを作る補助。row2 を y=2 の行にする。 */
    private static GameMap mapOfRow2(String row2) {
        return GameMap.fromRows(List.of(".........", ".........", row2, ".........", "........."));
    }

    private static GameEngine engineOfRow2(String row2) {
        return new GameEngine(mapOfRow2(row2));
    }

    private static GameEvent pickup(ItemType item, int x, int y) {
        return new GameEvent(EventType.PICKUP, item, x, y);
    }

    private static GameEvent switchOn(int x, int y) {
        return new GameEvent(EventType.SWITCH_ON, null, x, y);
    }

    private static GameEvent doorOpen(int x, int y) {
        return new GameEvent(EventType.DOOR_OPEN, null, x, y);
    }

    static void run() {
        T.group("アイテム: 自動取得");
        GameEngine e = engineOfRow2("SM......G");
        T.check("最初は何も持っていない", !e.hasSupply() && !e.hasWeapon());
        T.check("物資のマスにアイテムが残っている", e.hasItemAt(new Position(1, 2)));
        T.eq("物資のマスへ移動", ActionResult.MOVED, e.moveRight());
        T.check("物資を取得した", e.hasSupply());
        T.eq("取得の出来事が記録される", List.of(pickup(ItemType.SUPPLY, 1, 2)), e.getLastEvents());
        T.check("マスから物資がなくなった", !e.hasItemAt(new Position(1, 2)));
        e.moveLeft();
        T.eq("次の行動では出来事が空になる", 0, e.getLastEvents().size());
        e.moveRight();
        T.eq("取得済みのマスに戻っても再取得されない", 0, e.getLastEvents().size());

        e = engineOfRow2("SW......G");
        e.moveRight();
        T.check("武器も自動取得される", e.hasWeapon() && !e.hasSupply());
        T.eq("武器の取得イベント", List.of(pickup(ItemType.WEAPON, 1, 2)), e.getLastEvents());

        T.group("アイテム: 持てるのは種類ごとに1つ");
        e = engineOfRow2("SMM.....G");
        e.moveRight();
        e.moveRight();
        T.check("物資をすでに持っていれば2つ目は取らない", e.hasSupply());
        T.eq("取得イベントは起きない", 0, e.getLastEvents().size());
        T.check("2つ目の物資は床に残る", e.hasItemAt(new Position(2, 2)));

        e = engineOfRow2("SMW.....G");
        e.moveRight();
        e.moveRight();
        T.check("物資と武器は別々に1つずつ持てる", e.hasSupply() && e.hasWeapon());

        e = engineOfRow2("SWW.....G");
        e.moveRight();
        e.moveRight();
        T.check("武器も2つ目は取らない", e.hasWeapon() && e.hasItemAt(new Position(2, 2)));

        T.group("アイテム: ジャンプの着地点で取得");
        e = engineOfRow2("SPM.....G");
        T.eq("ジャンプ成功", ActionResult.JUMPED, e.jumpRight());
        T.eq("着地点の物資を取得", List.of(pickup(ItemType.SUPPLY, 2, 2)), e.getLastEvents());

        T.group("スイッチ: 物資の消費とドアの開放");
        e = engineOfRow2("SaA.....G");
        T.eq("物資なしでスイッチへ移動できる", ActionResult.MOVED, e.moveRight());
        T.eq("物資がないと何も起きない", 0, e.getLastEvents().size());
        T.eq("ドアは閉じたまま通れない", ActionResult.BLOCKED_DOOR, e.moveRight());
        T.eq("ドアの手前から動いていない", new Position(1, 2), e.getPosition());
        T.eq("ドアにぶつかってもゲームは続く", GameStatus.PLAYING, e.getStatus());
        T.check("閉じたドアと判定される", e.isClosedDoor(new Position(2, 2)));

        e = engineOfRow2("SMaA....G");
        e.moveRight();
        T.eq("スイッチの手前の時点では物資を持っている", true, e.hasSupply());
        T.eq("スイッチのマスへ移動", ActionResult.MOVED, e.moveRight());
        T.eq("スイッチ作動とドア開放の出来事",
                List.of(switchOn(2, 2), doorOpen(3, 2)), e.getLastEvents());
        T.check("物資が消費された", !e.hasSupply());
        T.check("ドアが開いた", !e.isClosedDoor(new Position(3, 2)));
        T.eq("開いたドアは通れる", ActionResult.MOVED, e.moveRight());
        T.eq("ドアのマスにいる", new Position(3, 2), e.getPosition());

        T.group("スイッチ: 武器は消費されない");
        e = engineOfRow2("SWMa.A..G");
        e.moveRight();
        e.moveRight();
        e.moveRight();
        T.check("スイッチ作動後も武器は持ったまま", e.hasWeapon() && !e.hasSupply());

        T.group("スイッチ: 複数のドア・複数の組");
        e = engineOfRow2("SMaA.A..G");
        e.moveRight();
        e.moveRight();
        T.eq("同じ記号のドアがすべて開く",
                List.of(switchOn(2, 2), doorOpen(3, 2), doorOpen(5, 2)), e.getLastEvents());

        e = engineOfRow2("SMaAMbBG.");
        // 2組(a-A, b-B)が独立して動く: aを作動してもBは閉じたまま
        e.moveRight();
        e.moveRight();
        T.check("a を作動するとAが開く", !e.isClosedDoor(new Position(3, 2)));
        T.check("a を作動してもBは閉じたまま", e.isClosedDoor(new Position(6, 2)));
        e.moveRight(); // (3,2) ドア
        e.moveRight(); // (4,2) 物資
        e.moveRight(); // (5,2) スイッチb
        T.check("b を作動するとBが開く", !e.isClosedDoor(new Position(6, 2)));

        T.group("スイッチ: 同じ記号のスイッチが複数ある場合");
        e = engineOfRow2("SMaMa.A.G");
        e.moveRight();
        e.moveRight();
        T.eq("1つ目のスイッチでドアが開く",
                List.of(switchOn(2, 2), doorOpen(6, 2)), e.getLastEvents());
        e.moveRight(); // 物資
        e.moveRight(); // 2つ目のスイッチ
        T.eq("2つ目のスイッチは作動するがドアは新たに開かない", List.of(switchOn(4, 2)), e.getLastEvents());
        T.check("2つ目でも物資は消費される", !e.hasSupply());

        T.group("スイッチ: 作動済みのスイッチは物資を消費しない");
        e = engineOfRow2("SMaMA...G");
        e.moveRight();
        e.moveRight(); // スイッチ作動
        e.moveRight(); // 2つ目の物資を取得
        T.check("新しい物資を持っている", e.hasSupply());
        e.moveLeft(); // 作動済みスイッチへ戻る
        T.check("作動済みスイッチでは物資を消費しない", e.hasSupply());
        T.eq("出来事も起きない", 0, e.getLastEvents().size());

        T.group("ドア: ジャンプとの関係");
        GameMap doorJump = GameMap.fromRows(List.of(
                "a........", "M........", "S.PA....G", ".........", "........."));
        e = new GameEngine(doorJump);
        e.moveRight(); // (1,2)
        T.eq("着地点が閉じたドアならジャンプ失敗", ActionResult.JUMP_FAILED_BLOCKED, e.jumpRight());
        T.eq("動いていない", new Position(1, 2), e.getPosition());
        e.moveLeft(); // (0,2)
        e.moveUp();   // (0,1) 物資
        e.moveUp();   // (0,0) スイッチ
        T.check("ドアが開いた", !e.isClosedDoor(new Position(3, 2)));
        e.moveDown();
        e.moveDown();
        e.moveRight(); // (1,2)
        T.eq("開いたドアへは着地できる", ActionResult.JUMPED, e.jumpRight());
        T.eq("着地位置はドアのマス (3,2)", new Position(3, 2), e.getPosition());

        T.group("エンジンの独立性");
        GameMap shared = mapOfRow2("SM......G");
        GameEngine first = new GameEngine(shared);
        first.moveRight();
        GameEngine second = new GameEngine(shared);
        T.check("別のエンジンには取得済みの影響がない", second.hasItemAt(new Position(1, 2)) && !second.hasSupply());
        T.throwsError("マップの初期配置は書き換えられない", UnsupportedOperationException.class,
                () -> shared.getInitialItems().clear());

        T.group("GameMap: アイテム・スイッチ・ドアの読み込み");
        GameMap m = mapOfRow2("SMWaA...G");
        T.eq("物資の初期配置", ItemType.SUPPLY, m.getInitialItems().get(new Position(1, 2)));
        T.eq("武器の初期配置", ItemType.WEAPON, m.getInitialItems().get(new Position(2, 2)));
        T.eq("アイテムは2つ", 2, m.getInitialItems().size());
        T.check("アイテムのあるマスの地形は道(壁ではなく通れる)", !m.isWall(new Position(1, 2)));
        T.check("a はスイッチ", m.isSwitch(new Position(3, 2)));
        T.check("A はドア", m.isDoor(new Position(4, 2)));
        T.eq("スイッチの対応記号", 'a', m.getLink(new Position(3, 2)));
        T.eq("ドアの対応記号(小文字にそろう)", 'a', m.getLink(new Position(4, 2)));
        T.eq("対応するドアの位置", List.of(new Position(4, 2)), m.getDoorsFor('a'));
        T.eq("スイッチ・ドアでないマスの記号は 0", (char) 0, m.getLink(new Position(0, 2)));
        T.throwsError("ドアのないスイッチは例外", IllegalArgumentException.class,
                () -> mapOfRow2("SMa.....G"));
        T.throwsError("スイッチのないドアは例外", IllegalArgumentException.class,
                () -> mapOfRow2("SMA.....G"));
        T.throwsError("対応しない記号の組は例外", IllegalArgumentException.class,
                () -> mapOfRow2("SMaB....G"));
        T.throwsError("範囲外の記号(f)は例外", IllegalArgumentException.class,
                () -> mapOfRow2("SMfF....G"));
        T.eq("e-E まで使える", ItemType.SUPPLY, mapOfRow2("SMeE....G").getInitialItems().get(new Position(1, 2)));

        T.group("ステージ3・4（ファイルから読み込み）");
        Stage stage3 = StageRepository.findById(3).orElseThrow();
        Stage stage4 = StageRepository.findById(4).orElseThrow();

        RunResult r = ProgramRunner.run(stage3, repeat(8, Direction.RIGHT));
        T.eq("ステージ3: 右8回でクリア", GameStatus.CLEARED, r.status());
        T.eq("2歩目で物資を取得", List.of(pickup(ItemType.SUPPLY, 2, 2)), r.trace().get(1).events());
        T.check("取得後の持ち物に物資がある", r.trace().get(1).hasSupply());
        T.eq("4歩目でスイッチ作動とドア開放",
                List.of(switchOn(4, 2), doorOpen(6, 2)), r.trace().get(3).events());
        T.check("スイッチ作動後は物資がない", !r.trace().get(3).hasSupply());

        // 物資を取らずに回り道するとドアが開かず失敗する
        List<Block> detour = new ArrayList<>();
        detour.add(new Block("d0", BlockType.MOVE, Direction.UP));
        for (int i = 1; i <= 5; i++) {
            detour.add(new Block("d" + i, BlockType.MOVE, Direction.RIGHT));
        }
        detour.add(new Block("d6", BlockType.MOVE, Direction.DOWN));
        detour.add(new Block("d7", BlockType.MOVE, Direction.RIGHT));
        r = ProgramRunner.run(stage3, detour);
        T.eq("物資を取らないとドアが開かない", ActionResult.BLOCKED_DOOR, r.trace().get(7).result());
        T.eq("ステージ3: 失敗", GameStatus.FAILED, r.status());

        r = ProgramRunner.run(stage4, repeat(8, Direction.RIGHT));
        T.eq("ステージ4: 右8回でクリア", GameStatus.CLEARED, r.status());
        T.eq("武器を取得(4歩目)", List.of(pickup(ItemType.WEAPON, 4, 2)), r.trace().get(3).events());
        T.check("最後まで武器を持っている", r.trace().get(7).hasWeapon());
        T.check("最後は物資を使い切っている", !r.trace().get(7).hasSupply());
    }

    /** 同じ方向への移動ブロックを n 個並べる。 */
    private static List<Block> repeat(int n, Direction d) {
        List<Block> list = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            list.add(new Block("b" + i, BlockType.MOVE, d));
        }
        return list;
    }
}
