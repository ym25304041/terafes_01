package com.terafes.ant;

import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** ステージファイルの読み込み（StageRepository）のテストです。 */
final class StageRepositoryTest {

    /** 正しいステージファイルの内容（コメント・空行・name・map を含む） */
    private static final String VALID = String.join("\n",
            "; コメント行",
            "",
            "name: テスト面",
            "map:",
            ".........",
            ".#.......",
            "S...P...G",
            ".........",
            ".........");

    static void run() throws IOException {
        T.group("StageRepository: 標準のステージファイル（WEB-INF/stages）");
        List<Stage> all = StageRepository.findAll();
        T.check("ステージが1つ以上ある", !all.isEmpty());
        int previousId = 0;
        for (Stage stage : all) {
            T.check("ステージ" + stage.id() + "は番号が昇順", stage.id() > previousId);
            T.check("ステージ" + stage.id() + "に名前がある", !stage.name().isBlank());
            T.eq("ステージ" + stage.id() + "のマップは5行", 5, stage.rows().size());
            T.check("ステージ" + stage.id() + "は最初の向きが決まっている", stage.startFacing() != null);
            previousId = stage.id();
        }
        T.check("存在しない番号は空", StageRepository.findById(99).isEmpty());
        T.check("0以下の番号は空", StageRepository.findById(0).isEmpty());
        T.check("負の番号は空", StageRepository.findById(-1).isEmpty());

        T.group("StageRepository: 書き方の解析");
        Stage s = StageRepository.parse(7, VALID);
        T.eq("ステージ番号", 7, s.id());
        T.eq("名前", "テスト面", s.name());
        T.eq("マップは5行", 5, s.rows().size());
        T.eq("3行目", "S...P...G", s.rows().get(2));
        T.eq("CRLF 改行でも読める", 5, StageRepository.parse(1, VALID.replace("\n", "\r\n")).rows().size());
        T.eq("行末の空白は無視される", 5, StageRepository.parse(1, VALID.replace("\n", "  \n")).rows().size());

        T.throwsError("name がないと例外", IllegalArgumentException.class,
                () -> StageRepository.parse(1, VALID.replace("name: テスト面", "")));
        T.throwsError("name が空だと例外", IllegalArgumentException.class,
                () -> StageRepository.parse(1, VALID.replace("name: テスト面", "name:   ")));
        T.throwsError("map: がないと例外", IllegalArgumentException.class,
                () -> StageRepository.parse(1, VALID.replace("map:", "")));
        T.throwsError("解釈できない行があると例外", IllegalArgumentException.class,
                () -> StageRepository.parse(1, "title: x\n" + VALID));
        T.throwsError("マップが4行だと例外", IllegalArgumentException.class,
                () -> StageRepository.parse(1, VALID.substring(0, VALID.lastIndexOf('\n'))));
        T.throwsError("スタートがないと例外", IllegalArgumentException.class,
                () -> StageRepository.parse(1, VALID.replace("S", ".")));

        T.group("StageRepository: 説明（description）の解析");
        String body = "name: x\n%smap:\nS.......G\n.........\n.........\n.........\n.........";
        T.eq("description を読める", "ひとこと説明",
                StageRepository.parse(1, String.format(body, "description: ひとこと説明\n")).description());
        T.eq("description は省略できる(空文字)", "", StageRepository.parse(1, String.format(body, "")).description());
        T.eq("前後の空白は取り除かれる", "説明", StageRepository.parse(1, String.format(body, "description:   説明  \n")).description());

        T.group("StageRepository: ファイルの読み込み（一時フォルダ）");
        Path tmp = Files.createTempDirectory("ant-stages");
        Path original = Path.of("src/main/webapp/WEB-INF/stages");
        try {
            StageRepository.setDirectory(tmp);
            T.eq("空のフォルダにはステージがない", 0, StageRepository.findAll().size());

            Files.writeString(tmp.resolve("stage10.txt"), VALID, StandardCharsets.UTF_8);
            Files.writeString(tmp.resolve("stage2.txt"), "﻿" + VALID, StandardCharsets.UTF_8); // BOM付き
            Files.writeString(tmp.resolve("memo.txt"), "関係ないファイル", StandardCharsets.UTF_8);
            Files.writeString(tmp.resolve("stageX.txt"), "名前が規則に合わないファイル", StandardCharsets.UTF_8);

            List<Stage> found = StageRepository.findAll();
            T.eq("規則に合うファイルだけ読む", 2, found.size());
            T.eq("番号の小さい順(2が先)", 2, found.get(0).id());
            T.eq("次に10", 10, found.get(1).id());
            T.eq("BOM付きでも読める", "テスト面", found.get(0).name());

            // ファイルを書き換えると、再起動なしで次の読み込みから反映される
            Files.writeString(tmp.resolve("stage10.txt"), VALID.replace("テスト面", "書き換え後"), StandardCharsets.UTF_8);
            Optional<Stage> reloaded = StageRepository.findById(10);
            T.eq("書き換えが反映される", "書き換え後", reloaded.orElseThrow().name());

            // 壊れたファイル
            Files.writeString(tmp.resolve("stage3.txt"), "name: 壊れた面\nmap:\n###", StandardCharsets.UTF_8);
            T.throwsError("壊れたファイルは例外（原因入り）", IllegalArgumentException.class,
                    () -> StageRepository.findById(3));

            // 大きすぎるファイル
            Files.writeString(tmp.resolve("stage4.txt"), "; " + "x".repeat(9000), StandardCharsets.UTF_8);
            T.throwsError("大きすぎるファイルは例外", IllegalArgumentException.class,
                    () -> StageRepository.findById(4));
        } finally {
            // 後続のテストのために標準のフォルダへ戻し、一時ファイルを削除する
            StageRepository.setDirectory(original);
            try (var files = Files.list(tmp)) {
                for (Path p : (Iterable<Path>) files::iterator) {
                    Files.deleteIfExists(p);
                }
            }
            Files.deleteIfExists(tmp);
        }
    }
}
