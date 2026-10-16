package com.terafes.ant;

import com.terafes.ant.game.StageRepository;

import java.nio.file.Path;

/**
 * すべてのテストを実行するメインクラスです。
 * 失敗が1つでもあれば、終了コード 1 で終わります。
 *
 * 実行方法: test.bat（Windows）を実行してください。
 */
public final class TestMain {

    public static void main(String[] args) throws Exception {
        // ステージファイルの場所を設定する（test.bat はプロジェクト直下から実行される）
        StageRepository.setDirectory(Path.of("src/main/webapp/WEB-INF/stages"));

        StageRepositoryTest.run();
        GameMapTest.run();
        GameEngineTest.run();
        ItemGimmickTest.run();
        EnemyTest.run();
        PatrolTest.run();
        ControlBlockTest.run();
        FacingTest.run();
        DropTest.run();
        ProgramRunnerTest.run();
        ApiTest.run();

        System.out.println();
        System.out.println("==========================================");
        System.out.println("成功: " + T.passed + " 件 / 失敗: " + T.failed + " 件");
        System.out.println("==========================================");
        if (T.failed > 0) {
            System.exit(1);
        }
    }
}
