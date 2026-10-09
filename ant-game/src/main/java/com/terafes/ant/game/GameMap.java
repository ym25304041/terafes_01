package com.terafes.ant.game;

import java.util.List;

/**
 * 縦5 × 横9 のマップ（地形データ）を表すクラスです。
 *
 * 【役割】
 *   ・座標ごとの地形（道/壁/落とし穴/ゴール）を保持する
 *   ・「マップ外か」「壁か」「ゴールか」「落とし穴か」を調べる
 *
 * 【文字列でのマップ定義】
 *   マップは 5行×9文字 の文字列で書きます。使える文字は次の通りです。
 *     '.' = 道     '#' = 壁     'P' = 落とし穴(Pit)
 *     'G' = ゴール   'S' = スタート地点(地形としては道)
 *
 * このクラスは作成後に中身を変更できません（不変）。
 * 誰かが途中でマップを書き換えてしまう事故を防げます。
 */
public final class GameMap {

    /** マップの縦のマス数 */
    public static final int ROWS = 5;
    /** マップの横のマス数 */
    public static final int COLS = 9;

    /** 地形データ。cells[y][x] の順で参照します（行=y が先、列=x が後）。 */
    private final CellType[][] cells;
    /** アリの開始位置 */
    private final Position start;

    private GameMap(CellType[][] cells, Position start) {
        this.cells = cells;
        this.start = start;
    }

    /**
     * 文字列のリストからマップを作ります。
     *
     * 形式が正しくない場合は IllegalArgumentException を投げます。
     * （マップ定義はサーバー内の固定データですが、書き間違いを早く見つけるために厳しく検査します）
     *
     * @param rows 5行ぶんの文字列。各行は9文字
     * @return 作成したマップ
     */
    public static GameMap fromRows(List<String> rows) {
        if (rows == null || rows.size() != ROWS) {
            throw new IllegalArgumentException("マップは" + ROWS + "行で指定してください");
        }

        CellType[][] cells = new CellType[ROWS][COLS];
        Position start = null;
        int goalCount = 0;

        for (int y = 0; y < ROWS; y++) {
            String line = rows.get(y);
            if (line == null || line.length() != COLS) {
                throw new IllegalArgumentException((y + 1) + "行目は" + COLS + "文字で指定してください");
            }
            for (int x = 0; x < COLS; x++) {
                char c = line.charAt(x);
                switch (c) {
                    case '.' -> cells[y][x] = CellType.ROAD;
                    case '#' -> cells[y][x] = CellType.WALL;
                    case 'P' -> cells[y][x] = CellType.PIT;
                    case 'G' -> {
                        cells[y][x] = CellType.GOAL;
                        goalCount++;
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
        return new GameMap(cells, start);
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
        return !isOutOfMap(pos) && cells[pos.y()][pos.x()] == CellType.WALL;
    }

    /**
     * 指定した位置がゴールかどうかを判定します。
     * マップ外は false を返します。
     */
    public boolean isGoal(Position pos) {
        return !isOutOfMap(pos) && cells[pos.y()][pos.x()] == CellType.GOAL;
    }

    /**
     * 指定した位置が落とし穴かどうかを判定します。
     * マップ外は false を返します。
     */
    public boolean isPit(Position pos) {
        return !isOutOfMap(pos) && cells[pos.y()][pos.x()] == CellType.PIT;
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
}
