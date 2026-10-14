package com.terafes.ant;

import com.terafes.ant.game.CellType;
import com.terafes.ant.game.GameMap;
import com.terafes.ant.game.Position;
import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;

import java.util.List;

/** GameMap（地形データと判定メソッド）のテストです。 */
final class GameMapTest {

    /** テスト用マップ。S=(0,2) G=(8,2) 壁=(1,0) 穴=(4,2) */
    static final List<String> ROWS = List.of(
            ".#.......",
            ".........",
            "S...P...G",
            ".........",
            "........."
    );

    static void run() {
        T.group("GameMap");
        GameMap map = GameMap.fromRows(ROWS);

        // スタート位置
        T.eq("スタート位置は (0,2)", new Position(0, 2), map.getStart());

        // 地形の判定
        T.check("壁(1,0)は isWall が true", map.isWall(new Position(1, 0)));
        T.check("道(0,0)は isWall が false", !map.isWall(new Position(0, 0)));
        T.check("穴(4,2)は isPit が true", map.isPit(new Position(4, 2)));
        T.check("道(3,2)は isPit が false", !map.isPit(new Position(3, 2)));
        T.check("ゴール(8,2)は isGoal が true", map.isGoal(new Position(8, 2)));
        T.check("スタート(0,2)は道で isGoal が false", !map.isGoal(new Position(0, 2)));
        T.eq("スタート(0,2)の地形は ROAD", CellType.ROAD, map.getCell(new Position(0, 2)));

        // マップ外の判定（四隅の内側と外側）
        T.check("左上(0,0)はマップ内", !map.isOutOfMap(new Position(0, 0)));
        T.check("右下(8,4)はマップ内", !map.isOutOfMap(new Position(8, 4)));
        T.check("x=-1 はマップ外", map.isOutOfMap(new Position(-1, 0)));
        T.check("x=9 はマップ外", map.isOutOfMap(new Position(9, 0)));
        T.check("y=-1 はマップ外", map.isOutOfMap(new Position(0, -1)));
        T.check("y=5 はマップ外", map.isOutOfMap(new Position(0, 5)));
        T.check("マップ外は isWall が false（例外にならない）", !map.isWall(new Position(-1, -1)));
        T.throwsError("マップ外の getCell は例外", IllegalArgumentException.class,
                () -> map.getCell(new Position(9, 9)));

        // 不正なマップ定義は拒否される
        T.throwsError("行数が足りないと例外", IllegalArgumentException.class,
                () -> GameMap.fromRows(List.of("S.......G")));
        T.throwsError("列数が違うと例外", IllegalArgumentException.class,
                () -> GameMap.fromRows(List.of("S.G", ".........", ".........", ".........", ".........")));
        T.throwsError("スタートがないと例外", IllegalArgumentException.class,
                () -> GameMap.fromRows(List.of(".........", ".........", "........G", ".........", ".........")));
        T.throwsError("ゴールがないと例外", IllegalArgumentException.class,
                () -> GameMap.fromRows(List.of(".........", ".........", "S........", ".........", ".........")));
        T.throwsError("スタートが2つあると例外", IllegalArgumentException.class,
                () -> GameMap.fromRows(List.of("S.......G", "S........", ".........", ".........", ".........")));
        T.throwsError("使えない文字(Z)があると例外", IllegalArgumentException.class,
                () -> GameMap.fromRows(List.of("S.......G", "Z........", ".........", ".........", ".........")));

        // 本番のステージ定義がすべて正しい形式であること
        for (Stage stage : StageRepository.findAll()) {
            T.check("ステージ" + stage.id() + "の定義が正しい", stage.createMap() != null);
        }
    }
}
