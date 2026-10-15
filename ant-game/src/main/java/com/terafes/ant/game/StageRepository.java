package com.terafes.ant.game;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * ステージ定義ファイルの読み込みを担当するクラスです。
 *
 * 【ファイルの置き場所】
 *   ステージフォルダ（標準は WEB-INF/stages）の中に、stage1.txt, stage2.txt, … と置きます。
 *   ファイル名の数字がそのままステージ番号になります。
 *   ステージを増やすときは、ファイルを1つ追加するだけです（プログラムの修正は不要）。
 *
 * 【ファイルの書き方】（例）
 * <pre>
 * ; 「;」で始まる行はコメント。空行は無視される
 * name: 穴をジャンプ！
 * map:
 * ....#....
 * .##.#.##.
 * S...P...G
 * .##.#.##.
 * ....#....
 * </pre>
 *
 * 【読み込みのタイミング】
 *   リクエストのたびにファイルを読みます（ファイルは数百バイトと小さいため負担になりません）。
 *   そのため、Tomcat を再起動しなくても、ファイルを書き換えれば次の実行から反映されます。
 *
 * 【セキュリティ】
 *   ファイル名はステージ番号（整数）から自分で組み立てます。
 *   外部から届いた文字列でファイルを指定することはないため、
 *   「../」を使って他のファイルを読ませるパストラバーサル攻撃はできません。
 */
public final class StageRepository {

    /** ステージファイル名の形式（stage + 数字 + .txt） */
    private static final Pattern FILE_NAME = Pattern.compile("^stage(\\d{1,4})\\.txt$");

    /** 1ファイルの最大サイズ（バイト）。誤って巨大なファイルを置いてもメモリを圧迫しないようにする */
    private static final long MAX_FILE_BYTES = 8 * 1024;

    /** 受け付けるステージ番号の最大値 */
    private static final int MAX_STAGE_ID = 9999;

    /** ステージファイルがあるフォルダ。起動時に設定する（未設定のまま使うとエラー） */
    private static volatile Path directory;

    /** インスタンスを作らせない（static メソッドだけのクラス） */
    private StageRepository() {
    }

    /**
     * ステージファイルのフォルダを設定します。
     * Web アプリの起動時に StageDirectoryListener が呼び出します。
     * テストでは、テスト用のフォルダを指定します。
     *
     * @param dir ステージファイルがあるフォルダ
     */
    public static void setDirectory(Path dir) {
        directory = dir;
    }

    /**
     * ステージ番号からステージを探します。
     *
     * @param id ステージ番号
     * @return 見つかればそのステージ。ファイルがなければ空の Optional
     * @throws IllegalArgumentException ファイルはあるが書き方が正しくない場合
     * @throws IllegalStateException    フォルダが設定されていない、またはファイルを読めない場合
     */
    public static Optional<Stage> findById(int id) {
        if (id < 1 || id > MAX_STAGE_ID) {
            return Optional.empty();
        }
        // 整数から自分でファイル名を作る（外部の文字列をそのままファイル名にしない）
        Path file = requireDirectory().resolve("stage" + id + ".txt");
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        return Optional.of(parse(id, readFile(file)));
    }

    /**
     * フォルダ内の全ステージを、ステージ番号の小さい順に返します。
     *
     * @throws IllegalArgumentException 書き方が正しくないファイルがある場合
     */
    public static List<Stage> findAll() {
        List<Integer> ids = new ArrayList<>();
        try (Stream<Path> files = Files.list(requireDirectory())) {
            files.forEach(p -> {
                Matcher m = FILE_NAME.matcher(p.getFileName().toString());
                if (m.matches()) {
                    ids.add(Integer.parseInt(m.group(1)));
                }
            });
        } catch (IOException e) {
            throw new IllegalStateException("ステージフォルダを読めません", e);
        }
        ids.sort(null); // 小さい順に並べる

        List<Stage> stages = new ArrayList<>();
        for (int id : ids) {
            findById(id).ifPresent(stages::add);
        }
        return stages;
    }

    /**
     * ステージファイルの文字列を解析して Stage にします。
     *
     * 決まり:
     *   ・「;」で始まる行（コメント）と空行は無視する
     *   ・「name:」の行でステージ名を指定する（必須）
     *   ・「facing:」の行でアリの最初の向きを指定する（省略可。right / left / up / down。省略すると right）
     *   ・「map:」の行より後の行が、マップの各行になる（必須。5行×9文字）
     *
     * @param id   ステージ番号（ファイル名から決まる）
     * @param text ファイルの内容
     * @return 解析したステージ
     * @throws IllegalArgumentException 書き方が正しくない場合（原因が分かるメッセージ付き）
     */
    public static Stage parse(int id, String text) {
        String name = null;
        Direction facing = Direction.RIGHT;
        boolean inMap = false;
        List<String> rows = new ArrayList<>();

        for (String rawLine : text.split("\\R")) { // \R は改行(CRLF/LF)どちらにも対応
            String line = rawLine.stripTrailing();
            if (line.isEmpty() || line.startsWith(";")) {
                continue;
            }
            if (inMap) {
                // map: の後ろは、すべてマップの行として扱う
                rows.add(line);
            } else if (line.startsWith("name:")) {
                name = line.substring("name:".length()).strip();
            } else if (line.startsWith("facing:")) {
                facing = parseFacing(id, line.substring("facing:".length()).strip());
            } else if (line.equals("map:")) {
                inMap = true;
            } else {
                throw new IllegalArgumentException("stage" + id + ".txt: 解釈できない行があります: " + line);
            }
        }

        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("stage" + id + ".txt: name: の行がありません");
        }
        if (!inMap) {
            throw new IllegalArgumentException("stage" + id + ".txt: map: の行がありません");
        }

        Stage stage = new Stage(id, name, List.copyOf(rows), facing);
        try {
            stage.createMap(); // マップの形式（行数・文字数・S/G）を検査する
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("stage" + id + ".txt: " + e.getMessage(), e);
        }
        return stage;
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    /** 「facing:」の値（right / left / up / down）を Direction に変換します。 */
    private static Direction parseFacing(int id, String value) {
        try {
            return Direction.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException(
                    "stage" + id + ".txt: facing: には right / left / up / down のどれかを書いてください");
        }
    }

    /** フォルダが設定済みか確認して返します。 */
    private static Path requireDirectory() {
        Path dir = directory;
        if (dir == null) {
            throw new IllegalStateException("ステージフォルダが設定されていません");
        }
        return dir;
    }

    /** ファイルを UTF-8 の文字列として読み込みます。サイズが大きすぎる場合は拒否します。 */
    private static String readFile(Path file) {
        try {
            if (Files.size(file) > MAX_FILE_BYTES) {
                throw new IllegalArgumentException(file.getFileName() + ": ファイルが大きすぎます");
            }
            String text = Files.readString(file, StandardCharsets.UTF_8);
            // Windows のメモ帳などが付ける BOM（先頭の目印文字）があれば取り除く
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (IOException e) {
            throw new IllegalStateException("ステージファイルを読めません: " + file.getFileName(), e);
        }
    }
}
