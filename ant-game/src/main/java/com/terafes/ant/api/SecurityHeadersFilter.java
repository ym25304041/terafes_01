package com.terafes.ant.api;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * すべてのレスポンスにセキュリティ用のヘッダーを付けるフィルターです。
 *
 * フィルターとは、リクエストが Servlet に届く前後に共通処理を挟む仕組みです。
 * ここでは次のヘッダーを付けて、XSS などの被害を受けにくくします。
 *
 *   Content-Security-Policy: 自分のサイトのスクリプト以外は実行しない。
 *                            万一 HTML にスクリプトが紛れ込んでも、ブラウザが実行を拒否する。
 *   X-Content-Type-Options : ブラウザによる Content-Type の勝手な推測を禁止する。
 *   X-Frame-Options        : 他サイトの iframe に埋め込まれるのを禁止する(クリックジャッキング対策)。
 *   Referrer-Policy        : 他サイトへ遷移するとき、元のURLを送らない。
 *   Cache-Control          : ブラウザが古い画面ファイル(js・css・html)を使い続けないよう、
 *                            毎回サーバーに更新の有無を確認させる(再デプロイ後に古い画面が残る問題の対策)。
 *                            API のレスポンスでは、別途 no-store を指定して上書きする。
 */
@WebFilter("/*")
public class SecurityHeadersFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        if (response instanceof HttpServletResponse resp) {
            resp.setHeader("Content-Security-Policy",
                    "default-src 'self'; script-src 'self'; style-src 'self'; "
                            + "img-src 'self' data:; object-src 'none'; frame-ancestors 'none'; base-uri 'self'");
            resp.setHeader("X-Content-Type-Options", "nosniff");
            resp.setHeader("X-Frame-Options", "DENY");
            resp.setHeader("Referrer-Policy", "no-referrer");
            resp.setHeader("Cache-Control", "no-cache");
        }
        // 次の処理（別のフィルターまたは Servlet）へ進む
        chain.doFilter(request, response);
    }
}
