package com.terafes.ant;

import com.terafes.ant.api.BadRequestException;
import com.terafes.ant.api.RunRequest;
import com.terafes.ant.game.Direction;
import com.terafes.ant.json.JsonParseException;
import com.terafes.ant.json.JsonParser;
import com.terafes.ant.json.JsonWriter;
import com.terafes.ant.program.BlockType;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** JSON の読み書きと、リクエスト検査（入力検証・XSS対策）のテストです。 */
final class ApiTest {

    /** RunRequest.parse が BadRequestException を投げ、そのコードが期待どおりか確認する */
    private static void badRequest(String name, String expectedCode, String json) {
        try {
            RunRequest.parse(json);
            T.check(name + "（例外が出なかった）", false);
        } catch (BadRequestException e) {
            T.eq(name, expectedCode, e.getErrorCode());
        }
    }

    static void run() {
        T.group("JsonParser");
        Object parsed = JsonParser.parse("{\"a\": 1, \"b\": [true, null, \"x\"], \"c\": {\"d\": -2.5}}");
        T.check("オブジェクトは Map になる", parsed instanceof Map);
        Map<?, ?> map = (Map<?, ?>) parsed;
        T.eq("整数は Long", 1L, map.get("a"));
        T.eq("配列は List（要素3つ）", 3, ((List<?>) map.get("b")).size());
        T.eq("入れ子の小数", -2.5, ((Map<?, ?>) map.get("c")).get("d"));
        T.eq("エスケープの読み取り", "a\"b\\c\nd\u3042", JsonParser.parse("\"a\\\"b\\\\c\\nd\\u3042\""));
        T.eq("日本語の文字列", "あり", JsonParser.parse("\"あり\""));
        T.throwsError("閉じていないJSONは例外", JsonParseException.class, () -> JsonParser.parse("{\"a\": 1"));
        T.throwsError("末尾に余計な文字があると例外", JsonParseException.class, () -> JsonParser.parse("{} x"));
        T.throwsError("空文字は例外", JsonParseException.class, () -> JsonParser.parse(""));
        T.throwsError("null 入力は例外", JsonParseException.class, () -> JsonParser.parse(null));
        T.throwsError("不正なエスケープは例外", JsonParseException.class, () -> JsonParser.parse("\"\\q\""));
        T.throwsError("深すぎる入れ子は例外（スタック枯渇対策）", JsonParseException.class,
                () -> JsonParser.parse("[".repeat(5000) + "]".repeat(5000)));

        T.group("JsonWriter（XSS対策のエスケープ）");
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("n", 3);
        out.put("s", "<script>alert('x')</script>&");
        out.put("e", Direction.UP);
        out.put("nil", null);
        out.put("list", List.of(1, "a"));
        String json = JsonWriter.write(out);
        T.check("< > & ' が文字のまま出力されない",
                !json.contains("<") && !json.contains(">") && !json.contains("&") && !json.contains("'"));
        T.check("\\u003c にエスケープされる", json.contains("\\u003cscript\\u003e"));
        T.check("enum は名前で出力される", json.contains("\"e\":\"UP\""));
        T.check("null が出力される", json.contains("\"nil\":null"));
        T.eq("書いた JSON を読み戻すと元の文字列に戻る",
                "<script>alert('x')</script>&", ((Map<?, ?>) JsonParser.parse(json)).get("s"));
        T.eq("引用符のエスケープ", "\"a\\\"b\"", JsonWriter.write("a\"b"));
        T.eq("改行のエスケープ", "\"a\\nb\"", JsonWriter.write("a\nb"));

        T.group("RunRequest（入力検証）");
        RunRequest ok = RunRequest.parse(
                "{\"stageId\":2,\"program\":[{\"id\":\"b1\",\"type\":\"MOVE\",\"dir\":\"RIGHT\"},"
                        + "{\"id\":\"b-2_X\",\"type\":\"TURN\",\"dir\":\"LEFT\"}]}");
        T.eq("ステージ番号", 2, ok.stageId());
        T.eq("ブロック数", 2, ok.program().size());
        T.eq("1つ目の種類", BlockType.MOVE, ok.program().get(0).type());
        T.eq("2つ目の種類(向く)", BlockType.TURN, ok.program().get(1).type());
        T.eq("2つ目の方向", Direction.LEFT, ok.program().get(1).dir());
        T.eq("空のプログラムは受け付ける", 0,
                RunRequest.parse("{\"stageId\":1,\"program\":[]}").program().size());

        badRequest("JSONでない", "INVALID_JSON", "これはJSONではない");
        badRequest("ルートが配列", "INVALID_REQUEST", "[]");
        badRequest("stageId がない", "INVALID_STAGE_ID", "{\"program\":[]}");
        badRequest("stageId が文字列", "INVALID_STAGE_ID", "{\"stageId\":\"1\",\"program\":[]}");
        badRequest("stageId が小数", "INVALID_STAGE_ID", "{\"stageId\":1.5,\"program\":[]}");
        badRequest("stageId が0以下", "INVALID_STAGE_ID", "{\"stageId\":0,\"program\":[]}");
        badRequest("program がない", "INVALID_PROGRAM", "{\"stageId\":1}");
        badRequest("program が配列でない", "INVALID_PROGRAM", "{\"stageId\":1,\"program\":\"x\"}");
        badRequest("ブロックがオブジェクトでない", "INVALID_BLOCK", "{\"stageId\":1,\"program\":[1]}");
        badRequest("ブロックIDがない", "INVALID_BLOCK_ID",
                "{\"stageId\":1,\"program\":[{\"type\":\"MOVE\",\"dir\":\"UP\"}]}");
        badRequest("ブロックIDにスクリプトタグ(XSS)", "INVALID_BLOCK_ID",
                "{\"stageId\":1,\"program\":[{\"id\":\"<script>\",\"type\":\"MOVE\",\"dir\":\"UP\"}]}");
        badRequest("ブロックIDが長すぎる", "INVALID_BLOCK_ID",
                "{\"stageId\":1,\"program\":[{\"id\":\"" + "a".repeat(33) + "\",\"type\":\"MOVE\",\"dir\":\"UP\"}]}");
        badRequest("未知のブロック種別", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"b1\",\"type\":\"HACK\",\"dir\":\"UP\"}]}");
        badRequest("未知の方向", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"b1\",\"type\":\"TURN\",\"dir\":\"up\"}]}");
        badRequest("向くブロックに方向がない", "INVALID_BLOCK",
                "{\"stageId\":1,\"program\":[{\"id\":\"b1\",\"type\":\"TURN\"}]}");

        StringBuilder many = new StringBuilder("{\"stageId\":1,\"program\":[");
        for (int i = 0; i <= RunRequest.MAX_BLOCKS; i++) {
            if (i > 0) {
                many.append(',');
            }
            many.append("{\"id\":\"b").append(i).append("\",\"type\":\"MOVE\",\"dir\":\"UP\"}");
        }
        many.append("]}");
        badRequest("ブロック数が上限超過", "PROGRAM_TOO_LONG", many.toString());
    }
}
