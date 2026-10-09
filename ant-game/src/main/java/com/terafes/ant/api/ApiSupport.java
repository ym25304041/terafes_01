package com.terafes.ant.api;

import com.terafes.ant.game.GameEvent;
import com.terafes.ant.json.JsonWriter;
import com.terafes.ant.program.RunResult;
import com.terafes.ant.program.TraceStep;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 各 Servlet で共通して使う、レスポンス作成の補助メソッド集です。
 */
final class ApiSupport {

    private ApiSupport() {
    }

    /**
     * JSON をレスポンスとして書き出します。セキュリティ関連のヘッダーもここで付けます。
     *
     * @param resp   レスポンス
     * @param status HTTPステータスコード（200, 400 など）
     * @param body   JSON にする内容
     */
    static void writeJson(HttpServletResponse resp, int status, Object body) throws IOException {
        resp.setStatus(status);
        // JSON であり文字コードは UTF-8 であることを明示する
        resp.setContentType("application/json");
        resp.setCharacterEncoding("UTF-8");
        // ブラウザに「Content-Type を勝手に推測して HTML として扱わない」よう指示する(XSS対策)
        resp.setHeader("X-Content-Type-Options", "nosniff");
        // 実行結果をキャッシュさせない
        resp.setHeader("Cache-Control", "no-store");
        resp.getWriter().write(JsonWriter.write(body));
    }

    /** エラーレスポンス {"error":"コード"} を書き出します。 */
    static void writeError(HttpServletResponse resp, int status, String errorCode) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", errorCode);
        writeJson(resp, status, body);
    }

    /** 実行結果(RunResult)を、クライアントへ返す Map 形式に変換します。 */
    static Map<String, Object> toResponseBody(RunResult result) {
        List<Object> trace = new ArrayList<>();
        for (TraceStep t : result.trace()) {
            Map<String, Object> step = new LinkedHashMap<>();
            step.put("step", t.step());
            step.put("blockId", t.blockId());
            step.put("result", t.result());
            step.put("x", t.x());
            step.put("y", t.y());
            step.put("facing", t.facing());
            step.put("events", toEventList(t.events()));
            Map<String, Object> inventory = new LinkedHashMap<>();
            inventory.put("supply", t.hasSupply());
            inventory.put("weapon", t.hasWeapon());
            step.put("inventory", inventory);
            trace.add(step);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", result.status());
        body.put("failReason", result.failReason());
        body.put("stepCount", result.stepCount());
        body.put("trace", trace);
        return body;
    }

    /** 自動で起きた出来事のリストを、JSON にできる形に変換します。 */
    private static List<Object> toEventList(List<GameEvent> events) {
        List<Object> list = new ArrayList<>();
        for (GameEvent e : events) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", e.type());
            m.put("item", e.item()); // 取得以外のときは null
            m.put("x", e.x());
            m.put("y", e.y());
            list.add(m);
        }
        return list;
    }
}
