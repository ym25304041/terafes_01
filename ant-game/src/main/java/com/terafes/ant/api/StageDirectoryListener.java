package com.terafes.ant.api;

import com.terafes.ant.game.Stage;
import com.terafes.ant.game.StageRepository;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Web アプリの起動時に、ステージファイルのフォルダを設定するクラスです。
 *
 * ServletContextListener は「アプリが起動した／停止した」ときに呼ばれる仕組みです。
 *
 * ステージフォルダの決め方:
 *   1. web.xml の context-param「stageDir」があれば、そのフォルダを使う
 *      （Tomcat の外の好きな場所にステージを置きたいとき用）
 *   2. なければ WEB-INF/stages を使う（標準）
 *
 * 起動時に全ステージファイルを一度読み込んで検査し、書き間違いがあればログに出します。
 */
@WebListener
public class StageDirectoryListener implements ServletContextListener {

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext context = event.getServletContext();

        String configured = context.getInitParameter("stageDir");
        String dir = (configured != null && !configured.isBlank())
                ? configured
                : context.getRealPath("/WEB-INF/stages");

        if (dir == null) {
            // WAR を展開しない設定の Tomcat では実際のフォルダが得られない
            throw new IllegalStateException(
                    "ステージフォルダを特定できません。web.xml の stageDir を設定してください");
        }

        Path path = Paths.get(dir);
        StageRepository.setDirectory(path);
        context.log("ステージフォルダ: " + path);

        // 起動時の検査: 書き間違いのあるステージファイルがないか確認する
        try {
            for (Stage stage : StageRepository.findAll()) {
                context.log("ステージ読み込みOK: " + stage.id() + " " + stage.name());
            }
        } catch (RuntimeException e) {
            context.log("【警告】ステージファイルに問題があります: " + e.getMessage());
        }
    }
}
