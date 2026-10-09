package com.terafes.ant.api;

import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;
import com.terafes.ant.program.ProgramRunner;
import com.terafes.ant.program.RunResult;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.Reader;
import java.util.Locale;
import java.util.Optional;

/**
 * 「プログラムを実行する」API です。  POST /api/run
 *
 * 流れ: リクエストのJSONを検査 → ステージを探す → プログラムを実行 → 結果をJSONで返す
 *
 * このクラスにはゲームのロジックは書かず、HTTP の入出力だけを担当します。
 * ロジックは ProgramRunner / GameEngine に任せます。
 *
 * ステータスコード:
 *   200 = 実行できた（クリア・失敗どちらも200。結果は本文の status で判断）
 *   400 = リクエストが不正
 *   404 = 指定したステージがない
 *   413 = リクエストが大きすぎる
 *   415 = Content-Type が application/json ではない
 */
@WebServlet("/api/run")
public class RunServlet extends HttpServlet {

    /** シリアル化(保存・転送)用のバージョン番号。警告を避けるために定義している。 */
    private static final long serialVersionUID = 1L;

    /** 受け付けるリクエスト本文の最大文字数（巨大データによるメモリ圧迫を防ぐ） */
    private static final int MAX_BODY_CHARS = 64 * 1024;

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        // Content-Type が JSON でなければ拒否する。
        // 他サイトのフォームからの勝手な送信(CSRF)を受け付けにくくする効果もある。
        String contentType = req.getContentType();
        if (contentType == null || !contentType.toLowerCase(Locale.ROOT).startsWith("application/json")) {
            ApiSupport.writeError(resp, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE");
            return;
        }

        // 文字コードを UTF-8 に固定して本文を読む
        req.setCharacterEncoding("UTF-8");
        String body = readBody(req);
        if (body == null) {
            ApiSupport.writeError(resp, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "REQUEST_TOO_LARGE");
            return;
        }

        try {
            RunRequest request = RunRequest.parse(body);

            Optional<Stage> stage;
            try {
                stage = StageRepository.findById(request.stageId());
            } catch (IllegalArgumentException | IllegalStateException e) {
                // ステージファイル側の問題。詳細はサーバーのログだけに残し、クライアントには固定コードを返す
                getServletContext().log("ステージの読み込みに失敗: " + e.getMessage());
                ApiSupport.writeError(resp, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "STAGE_DATA_ERROR");
                return;
            }
            if (stage.isEmpty()) {
                ApiSupport.writeError(resp, HttpServletResponse.SC_NOT_FOUND, "STAGE_NOT_FOUND");
                return;
            }

            RunResult result = ProgramRunner.run(stage.get(), request.program());
            ApiSupport.writeJson(resp, HttpServletResponse.SC_OK, ApiSupport.toResponseBody(result));

        } catch (BadRequestException e) {
            // 返すのは固定のエラーコードだけ（入力値は含めない）
            ApiSupport.writeError(resp, HttpServletResponse.SC_BAD_REQUEST, e.getErrorCode());
        }
    }

    /**
     * リクエスト本文を最大 MAX_BODY_CHARS 文字まで読み込みます。
     *
     * @return 本文。上限を超えていたら null
     */
    private static String readBody(HttpServletRequest req) throws IOException {
        StringBuilder sb = new StringBuilder();
        char[] buffer = new char[2048];
        try (Reader reader = req.getReader()) {
            int n;
            while ((n = reader.read(buffer)) != -1) {
                sb.append(buffer, 0, n);
                if (sb.length() > MAX_BODY_CHARS) {
                    return null;
                }
            }
        }
        return sb.toString();
    }
}
