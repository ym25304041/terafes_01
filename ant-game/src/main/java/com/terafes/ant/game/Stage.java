package com.terafes.ant.game;

import java.util.List;

/**
 * ステージ1面ぶんの定義です。
 *
 * ステージの中身（マップ）はサーバー内の固定データであり、クライアントからは受け取りません。
 * クライアントに地形を送らせてしまうと、「壁のないマップ」を送って簡単にクリアする
 * といった不正ができてしまうためです。
 *
 * @param id   ステージ番号（1から）
 * @param name 画面に表示する名前
 * @param rows マップの文字列（5行×9文字）。記号の意味は {@link GameMap} を参照
 * @param startFacing アリの最初の向き
 * @param description ステージの一言説明（ステージ選択画面などに表示する。なければ空文字）
 */
public record Stage(int id, String name, List<String> rows, Direction startFacing, String description) {

    /** アリの最初の向きと説明を指定しない場合は、右向き・説明なしになる。 */
    public Stage(int id, String name, List<String> rows) {
        this(id, name, rows, Direction.RIGHT, "");
    }

    /** 説明を指定しない場合は、説明なしになる。 */
    public Stage(int id, String name, List<String> rows, Direction startFacing) {
        this(id, name, rows, startFacing, "");
    }

    /** このステージのマップオブジェクトを作ります。 */
    public GameMap createMap() {
        return GameMap.fromRows(rows);
    }
}
