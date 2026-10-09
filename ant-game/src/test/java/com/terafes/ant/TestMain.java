package com.terafes.ant;

/**
 * すべてのテストを実行するメインクラスです。
 * 失敗が1つでもあれば、終了コード 1 で終わります。
 *
 * 実行方法: test.bat（Windows）を実行してください。
 */
public final class TestMain {

    public static void main(String[] args) {
        GameMapTest.run();
        GameEngineTest.run();
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
