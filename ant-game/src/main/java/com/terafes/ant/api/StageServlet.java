package com.terafes.ant.api;

import com.terafes.ant.game.GameEngine;
import com.terafes.ant.game.GameMap;
import com.terafes.ant.game.Position;
import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * ステージ情報（マップの描画に必要なデータ）を返す API です。  GET /api/stage?id=1
 *
 * 画面側がマップを描くために使います。
 * 返す内容: ステージ番号・名前・マップ(5行×9文字)・スタート位置
 */
@WebServlet("/api/stage")
public class StageServlet extends HttpServlet {

    /** シリアル化(保存・転送)用のバージョン番号。警告を避けるために定義している。 */
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // id パラメータは文字列で届くので、数値に変換できるか検査する
        int id;
        try {
            id = Integer.parseInt(req.getParameter("id"));
        } catch (NumberFormatException e) {
            ApiSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, "INVALID_STAGE_ID");
            return;
        }

        Optional<Stage> found;
        try {
            found = StageRepository.findById(id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            // ステージファイル側の問題。詳細はサーバーのログだけに残す
            getServletContext().log("ステージの読み込みに失敗: " + e.getMessage());
            ApiSupport.writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "STAGE_DATA_ERROR");
            return;
        }
        if (found.isEmpty()) {
            ApiSupport.writeError(resp, HttpServletResponse.SC_NOT_FOUND, "STAGE_NOT_FOUND");
            return;
        }

        Stage stage = found.get();
        GameMap map = stage.createMap();
        Position start = map.getStart();

        Map<String, Object> startMap = new LinkedHashMap<>();
        startMap.put("x", start.x());
        startMap.put("y", start.y());

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", stage.id());
        body.put("name", stage.name());
        body.put("rows", stage.rows());
        body.put("start", startMap);
        // 敵の初期位置（画面が最初の状態を描くために使う）
        body.put("enemies", ApiSupport.toEnemyList(new GameEngine(map).getEnemyStates()));
        ApiSupport.writeJson(resp, HttpServletResponse.SC_OK, body);
    }
}
