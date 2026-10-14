package com.terafes.ant.api;

import com.terafes.ant.game.ConditionType;
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
 * 送られてくる JSON の例（制御ブロックは、中にブロックを入れ子で持ちます）:
 * <pre>
 * { "stageId": 6,
 *   "program": [
 *     { "id": "b1", "type": "MOVE", "dir": "RIGHT" },
 *     { "id": "b2", "type": "WHILE", "condition": "ENEMY_COMING", "not": false,
 *       "body": [ { "id": "b3", "type": "WAIT" } ] },
 *     { "id": "b4", "type": "IF", "condition": "WALL_AHEAD",
 *       "then": [ ... ], "else": [ ... ] },
 *     { "id": "b5", "type": "REPEAT", "count": 3,
 *       "body": [ { "id": "b6", "type": "MOVE", "dir": "RIGHT" } ] } ] }
 * </pre>
 *
 * 外部から届いたデータは信用せず、すべての項目を検査します（入力検証）。
 *
 * @param stageId 挑戦するステージ番号
 * @param program 実行するブロックのリスト
 */
public record RunRequest(int stageId, List<Block> program) {

    /**
     * 受け付けるブロック数の上限（入れ子の中のブロックも含めた合計）。
     * 巨大なリクエストでサーバーを圧迫されないよう、実行上限とは別にここでも制限する。
     */
    public static final int MAX_BLOCKS = 200;

    /** 制御ブロックの入れ子の深さの上限（深すぎる入力でスタックを使い切られないようにする） */
    public static final int MAX_DEPTH = 6;

    /** 「回くり返す」で指定できる回数の上限 */
    public static final int MAX_REPEAT_COUNT = 100;

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
        if (!(programValue instanceof List<?>)) {
            throw new BadRequestException("INVALID_PROGRAM");
        }
        int[] blockCounter = {0}; // 入れ子の中も含めたブロックの合計数を数える
        List<Block> blocks = parseBlockList(programValue, 0, blockCounter);
        return new RunRequest(stageIdLong.intValue(), blocks);
    }

    /**
     * ブロックのリスト（JSON の配列）を検査して Block のリストに変換します。
     *
     * @param value   JSON の配列であるはずの値
     * @param depth   今いる入れ子の深さ（いちばん外側は 0）
     * @param counter ブロックの合計数（呼び出しをまたいで数える）
     */
    private static List<Block> parseBlockList(Object value, int depth, int[] counter) {
        if (!(value instanceof List<?> list)) {
            throw new BadRequestException("INVALID_BLOCK");
        }
        List<Block> blocks = new ArrayList<>();
        for (Object item : list) {
            counter[0]++;
            if (counter[0] > MAX_BLOCKS) {
                throw new BadRequestException("PROGRAM_TOO_LONG");
            }
            blocks.add(parseBlock(item, depth, counter));
        }
        return blocks;
    }

    /** ブロック1つぶんの JSON を検査して Block に変換します（制御ブロックは中身も再帰的に検査）。 */
    private static Block parseBlock(Object item, int depth, int[] counter) {
        if (!(item instanceof Map<?, ?> map)) {
            throw new BadRequestException("INVALID_BLOCK");
        }
        Object id = map.get("id");
        if (!(id instanceof String idText) || !BLOCK_ID_PATTERN.matcher(idText).matches()) {
            throw new BadRequestException("INVALID_BLOCK_ID");
        }

        try {
            BlockType type = BlockType.parse(asStringOrNull(map.get("type")));
            switch (type) {
                case MOVE, JUMP -> {
                    return new Block(idText, type, Direction.parse(asStringOrNull(map.get("dir"))));
                }
                case WAIT -> {
                    return Block.waitBlock(idText);
                }
                default -> {
                    // 制御ブロック(IF / WHILE / REPEAT)は、入れ子の深さを制限する
                    if (depth >= MAX_DEPTH) {
                        throw new BadRequestException("PROGRAM_TOO_DEEP");
                    }
                    return parseControlBlock(idText, type, map, depth, counter);
                }
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("INVALID_BLOCK");
        }
    }

    /** 制御ブロック（IF / WHILE / REPEAT）を検査して Block に変換します。 */
    private static Block parseControlBlock(String id, BlockType type, Map<?, ?> map, int depth, int[] counter) {
        int childDepth = depth + 1;
        switch (type) {
            case IF -> {
                ConditionType condition = ConditionType.parse(asStringOrNull(map.get("condition")));
                boolean negate = parseNegate(map.get("not"));
                List<Block> thenBody = parseBlockList(map.get("then"), childDepth, counter);
                // 「そうでなければ」は省略できる（省略時は何もしない）
                List<Block> elseBody = map.get("else") == null
                        ? List.of()
                        : parseBlockList(map.get("else"), childDepth, counter);
                return Block.ifBlock(id, condition, negate, thenBody, elseBody);
            }
            case WHILE -> {
                ConditionType condition = ConditionType.parse(asStringOrNull(map.get("condition")));
                boolean negate = parseNegate(map.get("not"));
                return Block.whileBlock(id, condition, negate, parseBlockList(map.get("body"), childDepth, counter));
            }
            case REPEAT -> {
                if (!(map.get("count") instanceof Long count) || count < 1 || count > MAX_REPEAT_COUNT) {
                    throw new BadRequestException("INVALID_BLOCK");
                }
                return Block.repeatBlock(id, count.intValue(), parseBlockList(map.get("body"), childDepth, counter));
            }
            default -> throw new IllegalArgumentException("制御ブロックではありません");
        }
    }

    /** 「〜でない」の指定を読みます。省略(null)は false、true/false 以外は不正です。 */
    private static boolean parseNegate(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        throw new BadRequestException("INVALID_BLOCK");
    }

    /** 文字列ならそのまま、そうでなければ null を返します。 */
    private static String asStringOrNull(Object value) {
        return value instanceof String s ? s : null;
    }
}
