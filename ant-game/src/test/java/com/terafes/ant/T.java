package com.terafes.ant;

/**
 * JUnit などの外部ライブラリを使わないための、ごく簡単なテスト用ヘルパーです。
 *
 * check() で「期待どおりか」を確認し、結果を画面に出します。
 * 失敗が1つでもあれば TestMain が終了コード1で終わるので、バッチやCIで検知できます。
 */
final class T {

    /** 成功したチェックの数 */
    static int passed = 0;
    /** 失敗したチェックの数 */
    static int failed = 0;

    private T() {
    }

    /** 条件が true ならテスト成功として記録します。 */
    static void check(String name, boolean condition) {
        if (condition) {
            passed++;
            System.out.println("  [OK]   " + name);
        } else {
            failed++;
            System.out.println("  [NG]   " + name);
        }
    }

    /** 期待値と実際の値が等しいか確認します。 */
    static void eq(String name, Object expected, Object actual) {
        boolean ok = (expected == null) ? (actual == null) : expected.equals(actual);
        if (ok) {
            passed++;
            System.out.println("  [OK]   " + name);
        } else {
            failed++;
            System.out.println("  [NG]   " + name + "  期待=" + expected + " 実際=" + actual);
        }
    }

    /** 実行すると指定した種類の例外が出ることを確認します。 */
    static void throwsError(String name, Class<? extends Throwable> type, Runnable action) {
        try {
            action.run();
            failed++;
            System.out.println("  [NG]   " + name + "  例外が発生しませんでした");
        } catch (Throwable t) {
            if (type.isInstance(t)) {
                passed++;
                System.out.println("  [OK]   " + name);
            } else {
                failed++;
                System.out.println("  [NG]   " + name + "  別の例外: " + t);
            }
        }
    }

    /** テストグループの見出しを出します。 */
    static void group(String title) {
        System.out.println();
        System.out.println("■ " + title);
    }
}
