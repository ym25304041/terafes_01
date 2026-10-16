package com.terafes.ant.api;

import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ステージの一覧を返す API です。  GET /api/stages
 *
 * ステージ選択画面が、ステージ番号・名前・説明を表示するために使います。
 * 返す内容: {"stages":[{"id":1,"name":"…","description":"…"}, …]}（ステージ番号の小さい順）
 * マップの中身(rows)は、選んだステージの取得 API (api/stage) だけが返します。
 */
@WebServlet("/api/stages")
public class StagesServlet extends HttpServlet {

    /** シリアル化(保存・転送)用のバージョン番号。警告を避けるために定義している。 */
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        List<Stage> stages;
        try {
            stages = StageRepository.findAll();
        } catch (IllegalArgumentException | IllegalStateException e) {
            // ステージファイル側の問題。詳細はサーバーのログだけに残し、クライアントには固定コードを返す
            getServletContext().log("ステージ一覧の読み込みに失敗: " + e.getMessage());
            ApiSupport.writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "STAGE_DATA_ERROR");
            return;
        }

        List<Object> list = new ArrayList<>();
        for (Stage stage : stages) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("id", stage.id());
            item.put("name", stage.name());
            item.put("description", stage.description());
            list.add(item);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("stages", list);
        ApiSupport.writeJson(resp, HttpServletResponse.SC_OK, body);
    }
}
