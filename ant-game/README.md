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
  game/     Direction, Position, CellType, GameMap, GameEngine, Stage, StageRepository …ゲームのルール
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
