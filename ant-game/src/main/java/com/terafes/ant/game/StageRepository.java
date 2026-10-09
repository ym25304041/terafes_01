package com.terafes.ant.game;

import java.util.List;
import java.util.Optional;

/**
 * ステージ定義の置き場所です。
 *
 * データベースは使わない仕様なので、ステージはソースコード内に固定で持ちます。
 * （このため SQL インジェクションの心配はありません）
 *
 * ステージを増やすときは、STAGES に Stage を追加するだけです。
 */
public final class StageRepository {

    /** 全ステージ。List.of で作ったリストは変更できません。 */
    private static final List<Stage> STAGES = List.of(
            new Stage(1, "まっすぐ進もう", List.of(
                    ".........",
                    ".###.###.",
                    "S.......G",
                    ".###.###.",
                    "........."
            )),
            new Stage(2, "穴をジャンプ！", List.of(
                    "....#....",
                    ".##.#.##.",
                    "S...P...G",
                    ".##.#.##.",
                    "....#...."
            ))
    );

    /** インスタンスを作らせない（static メソッドだけのクラス） */
    private StageRepository() {
    }

    /**
     * ステージ番号からステージを探します。
     *
     * @param id ステージ番号
     * @return 見つかればそのステージ、なければ空の Optional
     */
    public static Optional<Stage> findById(int id) {
        return STAGES.stream().filter(s -> s.id() == id).findFirst();
    }

    /** 全ステージの一覧を返します。 */
    public static List<Stage> findAll() {
        return STAGES;
    }
}
