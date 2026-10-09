# アント・コマンド バックエンド（移動・ジャンプ・ゴール判定）

Java 21 / Jakarta Servlet 6（Tomcat 10）/ 素のHTML・JS。外部ライブラリ不使用（コンパイル時に Tomcat 付属の `servlet-api.jar` を参照するだけ）。

## ビルド・テスト・起動
```
test.bat      … ビルド + 全テスト実行（失敗があれば終了コード1）
build.bat     … ビルドのみ。build\ant-game.war ができる
```
- Tomcat が `C:\tomcat10` 以外にある場合は、先に環境変数 `TOMCAT_HOME` を設定する。
- 起動: `build\ant-game.war` を Tomcat の `webapps` に置く → `http://localhost:8080/ant-game/`

## ルール（確定事項）
- マップは縦5×横9。左上が原点(0,0)、x は右、y は下に増える。
- 移動は1マス。壁・マップ外へは動かない（ゲームは続行、1ステップとして数える）。
- 落とし穴へ通常移動 → 落下して失敗。
- ジャンプ: 前方1マスが落とし穴のときだけ成功し2マス先へ着地。穴でない／着地点が壁・マップ外なら失敗（その場から動かない）。着地点が穴なら落下、ゴールならクリア。
- 最大100ステップ。超えたら強制停止（`STEP_LIMIT_EXCEEDED`）。

## ステージファイル（マップの編集方法）
マップは `src/main/webapp/WEB-INF/stages/stage<番号>.txt` に置きます。ファイルを追加・編集するだけで、プログラムの修正は不要です。

```
; 「;」で始まる行はコメント、空行は無視
name: 穴をジャンプ！
map:
....#....
.##.#.##.
S...P...G
.##.#.##.
....#....
```
- 記号: `.`道 `#`壁 `P`落とし穴 `G`ゴール `S`スタート（1つだけ）。5行×9文字。
- 文字コードは UTF-8。ファイル名の数字がステージ番号になる。
- リクエストのたびに読み込むので、**起動中の Tomcat でも `webapps\ant-game\WEB-INF\stages\` のファイルを書き換えれば再起動なしで反映**される。ただし再デプロイ（WAR の置き直し）をすると `src` 側の内容に戻るので、恒久的な変更は `src` 側のファイルに行うこと。
- 書き方に誤りがあると API は `500 STAGE_DATA_ERROR` を返し、原因は Tomcat のログ（`localhost.*.log`）に出る。起動時にも全ファイルを検査してログに出す。
- Tomcat の外の好きなフォルダに置きたい場合は、`web.xml` に次を追加する。
  ```xml
  <context-param><param-name>stageDir</param-name><param-value>D:\ant\stages</param-value></context-param>
  ```
- 動作確認ページ（`index.html`）のステージ選択は固定の2つです。ステージを増やしたら `<option>` も追加してください。

## API
### `GET api/stage?id=1`
`{"id":1,"name":"…","rows":["…5行…"],"start":{"x":0,"y":2}}`

### `POST api/run`（Content-Type: application/json 必須）
```json
{ "stageId": 2,
  "program": [ {"id":"b1","type":"MOVE","dir":"RIGHT"},
               {"id":"b2","type":"JUMP","dir":"RIGHT"} ] }
```
- `type`: `MOVE` / `JUMP`　`dir`: `UP` / `DOWN` / `LEFT` / `RIGHT`
- `id`: 英数字・`-`・`_` の1〜32文字

レスポンス:
```json
{ "status": "CLEARED", "failReason": null, "stepCount": 7,
  "trace": [ {"step":1,"blockId":"b1","result":"MOVED","x":1,"y":2,"facing":"RIGHT"} ] }
```
- `status`: `CLEARED` / `FAILED`
- `failReason`: `FELL_INTO_PIT` / `NOT_REACHED_GOAL` / `STEP_LIMIT_EXCEEDED` / null
- `result`: `MOVED` `JUMPED` `BLOCKED_WALL` `BLOCKED_OUT_OF_MAP` `JUMP_FAILED_NO_PIT` `JUMP_FAILED_BLOCKED` `FELL_INTO_PIT` `REACHED_GOAL`
- エラー: `{"error":"コード"}`（400 / 404 / 413 / 415）

## ソース構成
```
src/main/java/com/terafes/ant/
  game/     Direction, Position, CellType, GameMap, GameEngine, Stage, StageRepository(ファイル読込) …ゲームのルール
  program/  Block, BlockType, ProgramRunner, RunResult, TraceStep …プログラムの実行
  json/     JsonParser, JsonWriter …自作の最小JSON処理
  api/      RunServlet, StageServlet, RunRequest, SecurityHeadersFilter …HTTP入出力と入力検証
src/main/webapp/   動作確認用の簡易画面（index.html, js, css）
src/test/java/     自作の簡易テスト（TestMain から実行）
```

## セキュリティ対策
- **XSS**: JSON出力は `< > & '` を `\uXXXX` にエスケープ／`X-Content-Type-Options: nosniff`／CSP（自サイトのスクリプトのみ）／画面側は `textContent` のみ使用／ブロックIDは英数字のみ許可／エラーに入力値を含めない。
- **入力検証**: ステージID・種別・方向はホワイトリスト、本文64KB・ブロック200個・JSON深さ20の上限、ステップ100回の強制停止。
- **SQLインジェクション**: DBを使わないため該当なし（将来DBを使う場合は `PreparedStatement` を使うこと）。

## 今後の拡張ポイント
- 敵・アイテム: 地形（`CellType`）とは別のレイヤーとして `GameMap` / `GameEngine` に追加。
- 制御ブロック（もし／繰り返し）: `BlockType` と `Block` を拡張し、`ProgramRunner` でネストを展開しつつ、同じ100ステップ制限を適用。
