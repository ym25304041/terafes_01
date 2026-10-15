package com.terafes.ant;

import com.terafes.ant.game.Direction;
import com.terafes.ant.game.Stage;

import java.util.List;

/**
 * テスト専用のステージ定義です。
 *
 * 本番のステージファイル（WEB-INF/stages）は、ステージを作る人が自由に編集します。
 * テストが本番のステージの中身に依存すると、ステージを編集しただけでテストが壊れてしまいます。
 * そのため、ロジックのテストには、このクラスの「変わらないステージ」を使います。
 * （本番のステージファイルが正しい形式かどうかは、GameMapTest / StageRepositoryTest で別に確認します）
 */
final class TestStages {

    private TestStages() {
    }

    /**
     * テスト用ステージを返します。
     *   1 = まっすぐ進む / 2 = 落とし穴をジャンプ / 3 = 物資・スイッチ・ドア
     *   4 = スイッチとドアが2組 / 5 = 武器と動かない敵2体 / 6 = 縦に往復する敵を横切る
     *   7 = 上へ回り込んでから右へ進む、曲がりくねった道
     */
    static Stage stage(int n) {
        return switch (n) {
            case 1 -> new Stage(1, "テスト1", List.of(
                    ".........", ".###.###.", "S.......G", ".###.###.", "........."));
            case 2 -> new Stage(2, "テスト2", List.of(
                    "....#....", ".##.#.##.", "S...P...G", ".##.#.##.", "....#...."));
            case 3 -> new Stage(3, "テスト3", List.of(
                    "......#..", "......#..", "S.M.a.A.G", "......#..", "......#.."));
            case 4 -> new Stage(4, "テスト4", List.of(
                    "...#...#.", "...#...#.", "SMaAWMbBG", "...#...#.", "...#...#."));
            case 5 -> new Stage(5, "テスト5", List.of(
                    ".W.......", ".........", "S.X.X...G", ".........", "........."));
            case 6 -> new Stage(6, "テスト6", List.of(
                    "....1....", "....1....", "..S.1...G", "....1....", "....1...."));
            case 7 -> new Stage(7, "テスト7", List.of(
                    "......###", ".####.###", "S####...G", "#########", "#########"));
            default -> throw new IllegalArgumentException("テスト用ステージがありません: " + n);
        };
    }

    /** 最初の向きを指定したテスト用ステージ（ステージ1と同じマップ）。 */
    static Stage stage1Facing(Direction facing) {
        return new Stage(1, "テスト1(向き指定)", stage(1).rows(), facing);
    }
}
