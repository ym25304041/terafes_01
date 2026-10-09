package com.terafes.ant.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 縦5 × 横9 のマップ（地形データとアイテムの初期配置）を表すクラスです。
 *
 * 【役割】
 *   ・座標ごとの地形（道/壁/落とし穴/ゴール/スイッチ/ドア）を保持する
 *   ・アイテムの「最初の配置」を保持する
 *   ・「マップ外か」「壁か」「ゴールか」などを調べる
 *
 * このクラスが持つのは「変わらないデータ」だけです。
 * アイテムを取った、ドアが開いた、といった「ゲーム中に変わる状態」は GameEngine が持ちます。
 * （同じマップを何度も、他の人のゲームと混ざらずに使えるようにするためです）
 *
 * 【文字列でのマップ定義】
 *   マップは 5行×9文字 の文字列で書きます。使える文字は次の通りです。
 *     '.' = 道            '#' = 壁           'P' = 落とし穴(Pit)
 *     'G' = ゴール         'S' = スタート地点(地形としては道)
 *     'M' = 物資が置かれた道   'W' = 武器が置かれた道
 *     'a'〜'e' = スイッチ   'A'〜'E' = ドア
 *
 *   スイッチとドアは同じ文字で対応します。小文字のスイッチ 'a' を作動させると、
 *   大文字のドア 'A' がすべて開きます。1ステージに複数の組（a-A, b-B …）を置けます。
 *   同じ記号のスイッチやドアを複数置くこともできます。
 *
 * このクラスは作成後に中身を変更できません（不変）。
 */
public final class GameMap {

    /** マップの縦のマス数 */
    public static final int ROWS = 5;
    /** マップの横のマス数 */
    public static final int COLS = 9;

    /** 地形データ。cells[y][x] の順で参照します（行=y が先、列=x が後）。 */
    private final CellType[][] cells;
    /**
     * スイッチ／ドアの対応記号。スイッチとドアのマスだけ、小文字の記号('a'〜'e')が入ります。
     * それ以外のマスは 0 です。
     */
    private final char[][] links;
    /** アイテムの初期配置（座標 → アイテムの種類）。変更不可。 */
    private final Map<Position, ItemType> initialItems;
    /** アリの開始位置 */
    private final Position start;

    private GameMap(CellType[][] cells, char[][] links, Map<Position, ItemType> items, Position start) {
        this.cells = cells;
        this.links = links;
        this.initialItems = Collections.unmodifiableMap(items);
        this.start = start;
    }

    /**
     * 文字列のリストからマップを作ります。
     *
     * 形式が正しくない場合は IllegalArgumentException を投げます。
     * （書き間違いを早く見つけるために厳しく検査します）
     *
     * @param rows 5行ぶんの文字列。各行は9文字
     * @return 作成したマップ
     */
    public static GameMap fromRows(List<String> rows) {
        if (rows == null || rows.size() != ROWS) {
            throw new IllegalArgumentException("マップは" + ROWS + "行で指定してください");
        }

        CellType[][] cells = new CellType[ROWS][COLS];
        char[][] links = new char[ROWS][COLS];
        Map<Position, ItemType> items = new HashMap<>();
        Set<Character> switchLinks = new HashSet<>();
        Set<Character> doorLinks = new HashSet<>();
        Position start = null;
        int goalCount = 0;

        for (int y = 0; y < ROWS; y++) {
            String line = rows.get(y);
            if (line == null || line.length() != COLS) {
                throw new IllegalArgumentException((y + 1) + "行目は" + COLS + "文字で指定してください");
            }
            for (int x = 0; x < COLS; x++) {
                char c = line.charAt(x);
                if (c >= 'a' && c <= 'e') {
                    // 小文字 = スイッチ
                    cells[y][x] = CellType.SWITCH;
                    links[y][x] = c;
                    switchLinks.add(c);
                    continue;
                }
                if (c >= 'A' && c <= 'E') {
                    // 大文字 = ドア。対応する記号は小文字にそろえて覚える
                    char link = Character.toLowerCase(c);
                    cells[y][x] = CellType.DOOR;
                    links[y][x] = link;
                    doorLinks.add(link);
                    continue;
                }
                switch (c) {
                    case '.' -> cells[y][x] = CellType.ROAD;
                    case '#' -> cells[y][x] = CellType.WALL;
                    case 'P' -> cells[y][x] = CellType.PIT;
                    case 'G' -> {
                        cells[y][x] = CellType.GOAL;
                        goalCount++;
                    }
                    case 'M' -> {
                        // 物資は「道の上」に置く
                        cells[y][x] = CellType.ROAD;
                        items.put(new Position(x, y), ItemType.SUPPLY);
                    }
                    case 'W' -> {
                        cells[y][x] = CellType.ROAD;
                        items.put(new Position(x, y), ItemType.WEAPON);
                    }
                    case 'S' -> {
                        // スタート地点は「道」として扱い、位置だけ別に覚えておく
                        cells[y][x] = CellType.ROAD;
                        if (start != null) {
                            throw new IllegalArgumentException("スタート地点(S)は1つだけにしてください");
                        }
                        start = new Position(x, y);
                    }
                    default -> throw new IllegalArgumentException("使えない文字です: " + c);
                }
            }
        }

        if (start == null) {
            throw new IllegalArgumentException("スタート地点(S)がありません");
        }
        if (goalCount == 0) {
            throw new IllegalArgumentException("ゴール(G)がありません");
        }
        // スイッチとドアの記号が対応していること(開けられないドアや、効果のないスイッチを防ぐ)
        for (char link : switchLinks) {
            if (!doorLinks.contains(link)) {
                throw new IllegalArgumentException(
                        "スイッチ(" + link + ")に対応するドア(" + Character.toUpperCase(link) + ")がありません");
            }
        }
        for (char link : doorLinks) {
            if (!switchLinks.contains(link)) {
                throw new IllegalArgumentException(
                        "ドア(" + Character.toUpperCase(link) + ")に対応するスイッチ(" + link + ")がありません");
            }
        }
        return new GameMap(cells, links, items, start);
    }

    /** アリの開始位置を返します。 */
    public Position getStart() {
        return start;
    }

    /**
     * 指定した位置がマップの外かどうかを判定します。
     *
     * x が 0〜8、y が 0〜4 の範囲に入っていなければ「マップ外」です。
     * 配列の範囲外にアクセスするとエラーになるため、
     * 地形を調べる前に必ずこのメソッドで確認します。
     *
     * @return マップ外なら true
     */
    public boolean isOutOfMap(Position pos) {
        return pos.x() < 0 || pos.x() >= COLS || pos.y() < 0 || pos.y() >= ROWS;
    }

    /**
     * 指定した位置が壁かどうかを判定します。
     * マップ外は「壁ではない」として false を返します（マップ外は isOutOfMap で別に判定します）。
     */
    public boolean isWall(Position pos) {
        return is(pos, CellType.WALL);
    }

    /** 指定した位置がゴールかどうかを判定します。マップ外は false です。 */
    public boolean isGoal(Position pos) {
        return is(pos, CellType.GOAL);
    }

    /** 指定した位置が落とし穴かどうかを判定します。マップ外は false です。 */
    public boolean isPit(Position pos) {
        return is(pos, CellType.PIT);
    }

    /** 指定した位置がスイッチかどうかを判定します。マップ外は false です。 */
    public boolean isSwitch(Position pos) {
        return is(pos, CellType.SWITCH);
    }

    /**
     * 指定した位置がドア（開閉どちらの状態でも）かどうかを判定します。マップ外は false です。
     * 今そのドアが開いているかどうかは、ゲーム中に変わる状態なので GameEngine が管理します。
     */
    public boolean isDoor(Position pos) {
        return is(pos, CellType.DOOR);
    }

    /**
     * 指定したスイッチ／ドアの対応記号（小文字）を返します。
     *
     * @return 対応記号('a'〜'e')。スイッチでもドアでもないマスは 0
     */
    public char getLink(Position pos) {
        return isOutOfMap(pos) ? 0 : links[pos.y()][pos.x()];
    }

    /** 指定した記号に対応するドアのマスをすべて返します。 */
    public List<Position> getDoorsFor(char link) {
        List<Position> doors = new ArrayList<>();
        for (int y = 0; y < ROWS; y++) {
            for (int x = 0; x < COLS; x++) {
                if (cells[y][x] == CellType.DOOR && links[y][x] == link) {
                    doors.add(new Position(x, y));
                }
            }
        }
        return doors;
    }

    /** アイテムの初期配置（座標 → 種類）を返します。変更はできません。 */
    public Map<Position, ItemType> getInitialItems() {
        return initialItems;
    }

    /**
     * 指定した位置の地形を返します。
     *
     * @throws IllegalArgumentException マップ外の座標を指定した場合
     */
    public CellType getCell(Position pos) {
        if (isOutOfMap(pos)) {
            throw new IllegalArgumentException("マップ外の座標です");
        }
        return cells[pos.y()][pos.x()];
    }

    /** マップ内で、指定した位置が指定した地形かどうかを調べる共通処理。 */
    private boolean is(Position pos, CellType type) {
        return !isOutOfMap(pos) && cells[pos.y()][pos.x()] == type;
    }
}
