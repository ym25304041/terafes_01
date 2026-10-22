package com.terafes.ant.api;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * Tomcat の標準のエラーページの代わりに表示する、エラーページです。  /error
 *
 * WEB-INF/web.xml の error-page から呼ばれます（400・404・405・500 と、予期しない例外）。
 * 標準のエラーページには、Tomcat のバージョンや例外の詳細が出てしまうため、使いません。
 *
 *   ・/api/ 配下のリクエストのエラー … {"error":"コード"} の JSON を返す（他の API のエラーと同じ形）
 *   ・それ以外（画面のリクエスト）  … 日本語のエラーページを返す
 *
 * どちらも、元のステータスコードをそのまま返します。
 * 元のリクエストの内容（URL・パラメータ）は、レスポンスに含めません。
 * セキュリティ用のヘッダーは、SecurityHeadersFilter がエラーのときにも付けます。
 */
@WebServlet("/error")
public class ErrorServlet extends HttpServlet {

    /** シリアル化(保存・転送)用のバージョン番号。警告を避けるために定義している。 */
    private static final long serialVersionUID = 1L;

    /** HTTPメソッドに関係なく、同じエラーの応答を返す（GET以外でエラーになったときも呼ばれるため） */
    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        Object statusValue = req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        // /error を直接開かれたとき(エラーの情報がないとき)は、存在しないページとして扱う
        int status = (statusValue instanceof Integer code) ? code : HttpServletResponse.SC_NOT_FOUND;

        Object uriValue = req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        boolean isApi = uriValue instanceof String uri && uri.startsWith(req.getContextPath() + "/api/");

        if (isApi) {
            ApiSupport.writeError(resp, status, errorCode(status));
        } else {
            writePage(req, resp, status);
        }
    }

    /** API のエラーコード（他の API のエラーと同じ、固定の文字列） */
    private static String errorCode(int status) {
        return switch (status) {
            case HttpServletResponse.SC_BAD_REQUEST -> "BAD_REQUEST";
            case HttpServletResponse.SC_NOT_FOUND -> "NOT_FOUND";
            case HttpServletResponse.SC_METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            default -> "INTERNAL_ERROR";
        };
    }

    /** 画面用のエラーページに出す文言 */
    private static String message(int status) {
        return switch (status) {
            case HttpServletResponse.SC_BAD_REQUEST -> "リクエストが正しくありません。";
            case HttpServletResponse.SC_NOT_FOUND -> "ページが見つかりません。";
            case HttpServletResponse.SC_METHOD_NOT_ALLOWED -> "この操作はできません。";
            default -> "エラーが発生しました。";
        };
    }

    /**
     * 画面用のエラーページを書き出します。
     * 表示する文字は、すべてこのクラスの中で決めた固定の文字です（リクエストの内容は出しません）。
     * CSP で inline のスタイルが使えないため、スタイルは既存の css/game.css を読み込みます。
     */
    private static void writePage(HttpServletRequest req, HttpServletResponse resp, int status) throws IOException {
        resp.setStatus(status);
        resp.setContentType("text/html");
        resp.setCharacterEncoding("UTF-8");
        String home = req.getContextPath() + "/index.html";
        String css = req.getContextPath() + "/css/game.css";
        resp.getWriter().write("<!DOCTYPE html>\n"
                + "<html lang=\"ja\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\">\n"
                + "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">\n"
                + "  <title>エラー | Aligorithm</title>\n"
                + "  <link rel=\"stylesheet\" href=\"" + css + "\">\n"
                + "</head>\n"
                + "<body>\n"
                + "  <main class=\"screen\">\n"
                + "    <div class=\"shell\">\n"
                + "      <div class=\"panel rules-panel\">\n"
                + "        <h1>" + message(status) + "</h1>\n"
                + "        <p><a class=\"navbtn\" href=\"" + home + "\">⌂ ホームへ戻る</a></p>\n"
                + "      </div>\n"
                + "    </div>\n"
                + "  </main>\n"
                + "</body>\n"
                + "</html>\n");
    }
}
