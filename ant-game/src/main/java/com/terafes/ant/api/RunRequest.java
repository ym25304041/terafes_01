package com.terafes.ant.api;

import com.terafes.ant.game.Direction;
import com.terafes.ant.json.JsonParseException;
import com.terafes.ant.json.JsonParser;
import com.terafes.ant.program.Block;
import com.terafes.ant.program.BlockType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 「プログラムを実行して」というリクエストの中身です。
 *
 * 送られてくる JSON の例:
 * <pre>
 * { "stageId": 2,
 *   "program": [ { "id": "b1", "type": "MOVE", "dir": "RIGHT" },
 *                { "id": "b2", "type": "JUMP", "dir": "RIGHT" } ] }
 * </pre>
 *
 * 外部から届いたデータは信用せず、すべての項目を検査します（入力検証）。
 *
 * @param stageId 挑戦するステージ番号
 * @param program 実行するブロックのリスト
 */
public record RunRequest(int stageId, List<Block> program) {

    /**
     * 受け付けるブロック数の上限。
     * 実行上限は100ステップだが、巨大なリクエストでサーバーを圧迫されないよう、
     * 少し余裕を持たせた数でここでも制限する。
     */
    public static final int MAX_BLOCKS = 200;

    /**
     * ブロックIDとして許可する形式: 英数字・ハイフン・アンダースコアのみ、1〜32文字。
     * IDは実行結果に含めて返すため、特殊文字（&lt; など）を最初から受け付けない。
     */
    private static final Pattern BLOCK_ID_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,32}$");

    /**
     * JSON 文字列を検査しながら RunRequest に変換します。
     *
     * @param json リクエスト本文
     * @return 検査済みのリクエスト
     * @throws BadRequestException 形式や値が不正な場合
     */
    public static RunRequest parse(String json) {
        Object root;
        try {
            root = JsonParser.parse(json);
        } catch (JsonParseException e) {
            throw new BadRequestException("INVALID_JSON");
        }
        if (!(root instanceof Map<?, ?> rootMap)) {
            throw new BadRequestException("INVALID_REQUEST");
        }

        // --- stageId の検査 ---
        Object stageIdValue = rootMap.get("stageId");
        if (!(stageIdValue instanceof Long stageIdLong)
                || stageIdLong < 1 || stageIdLong > 10_000) {
            throw new BadRequestException("INVALID_STAGE_ID");
        }

        // --- program の検査 ---
        Object programValue = rootMap.get("program");
        if (!(programValue instanceof List<?> programList)) {
            throw new BadRequestException("INVALID_PROGRAM");
        }
        if (programList.size() > MAX_BLOCKS) {
            throw new BadRequestException("PROGRAM_TOO_LONG");
        }

        List<Block> blocks = new ArrayList<>();
        for (Object item : programList) {
            blocks.add(parseBlock(item));
        }
        return new RunRequest(stageIdLong.intValue(), blocks);
    }

    /** ブロック1つぶんの JSON を検査して Block に変換します。 */
    private static Block parseBlock(Object item) {
        if (!(item instanceof Map<?, ?> map)) {
            throw new BadRequestException("INVALID_BLOCK");
        }
        Object id = map.get("id");
        if (!(id instanceof String idText) || !BLOCK_ID_PATTERN.matcher(idText).matches()) {
            throw new BadRequestException("INVALID_BLOCK_ID");
        }
        try {
            BlockType type = BlockType.parse(asStringOrNull(map.get("type")));
            Direction dir = Direction.parse(asStringOrNull(map.get("dir")));
            return new Block(idText, type, dir);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("INVALID_BLOCK");
        }
    }

    /** 文字列ならそのまま、そうでなければ null を返します。 */
    private static String asStringOrNull(Object value) {
        return value instanceof String s ? s : null;
    }
}
