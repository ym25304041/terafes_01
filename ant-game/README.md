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
- **向き:** アリは上下左右のどれかを向く。最初の向きはステージファイルの `facing:`（省略で右）。「移動」「ジャンプ」は**向いている方向**へ進み、向きを変えるには「向く」を使う。条件の「目の前」も向いている方向の1マス先。
- **関数:** コマンドをまとめたブロック。**移動関数**（方向・移動回数）は「その方向を向く → 回数ぶん移動」で、`向く`と`移動`を並べたのと同じ（回数+1 ステップ）。途中で動けなくても残りの移動は続け、クリア・失敗・上限超過で止まる。
- **時間（ターン）:** アリが1回行動（向く・移動・ジャンプ・待つ）するごとに1ステップ。動けなかった行動・ジャンプ失敗も1ステップ。順番は「アリが先 → 敵が後」。条件を調べるだけでは時間は進まない。
- 最大100ステップ（行動の回数）。超えたら強制停止（`STEP_LIMIT_EXCEEDED`）。さらに、ブロックの実行・条件の評価・くり返しの合計が1000回を超えたら停止（`LOOP_LIMIT_EXCEEDED`。中身が空のループ対策）。
- **アイテム（自動取得）:** 物資`M`・武器`W`のマスに着くと自動で取得し、マスから消える。物資と武器を別々に1つずつ持てる。同じ種類を持っているときは取得せず床に残す。
- **スイッチ`a〜e`:** 物資を持って乗ると物資を消費して作動し、同じ記号のドア`A〜E`がすべて開く。物資がなければ何も起きない（通れるマス）。作動済みのスイッチは物資を消費しない。
- **ドア:** 閉じている間は壁と同じで通れず（`BLOCKED_DOOR`）、ジャンプの着地点にもなれない。一度開いたら開いたまま。
- **敵`X`（画面では🐍、自動戦闘）:** 敵のいる道のマスに着く（移動・ジャンプの着地）と戦闘になる。武器を持っていれば討伐して進め、敵はマップから消える（武器は減らないので何体でも倒せる）。武器がなければ敗北してゲームオーバー（`DEFEATED_BY_ENEMY`）。物資だけでは討伐できない。
- **巡回する敵（接敵ゾーン`1〜5`）:** 縦か横の1列を往復する。1ステップに1マス進み、端で即折り返す。左端（縦なら上端）から反対側へ動き始める。アリが先に動き、そのあと敵が動く。敵がアリのいるマスに入ってきても戦闘になる（その場で待っていても危険）。討伐された敵はいなくなる。
- 取得・作動・戦闘はステップ数に数えない。順序は「落下判定 → 取得 → 敵との戦闘 → スイッチ → ゴール判定」。

## 画面と素材

| 画面 | ファイル | 内容 |
|---|---|---|
| タイトル | `index.html` | 「ゲーム開始」でステージ選択へ |
| ステージ選択 | `stages.html` | 9ステージのカード（ステージファイルの `name:` / `description:` を表示） |
| ゲームプレイ | `game.html?stage=N` | マップ・ブロックのパレット・プログラム。実行すると結果を再生 |
| 結果（クリア） | `result-clear.html?stage=N&steps=回数` | 実行回数を表示。「次のステージへ」「ステージ選択へ戻る」 |
| 結果（失敗） | `result-fail.html?stage=N&reason=理由` | 失敗理由を表示。「もう一度挑戦する」「ステージ選択へ戻る」 |

画面遷移: タイトル → ステージ選択 → ゲームプレイ → 結果。URLのパラメータは、数値・許可した値だけを受け付ける。

### 画像素材の差し替え（`src/main/webapp/assets/`）
素材の設定は **`js/assets.js` の `MANIFEST`** にまとまっている。**同じファイル名で画像を上書き**するか、`MANIFEST` のファイル名を書き換えれば差し替わる。画像が無い素材は、絵文字などの仮の表示になる（画面は壊れない）。

| 素材 | 既定のファイル名 | 備考 |
|---|---|---|
| 背景（タイトル／ステージ選択） | `bg-title.png` / `bg-stages.png` | 未用意（グラデーション背景） |
| 背景（ゲーム／結果） | `game-bg.png` | |
| タイトルロゴ | `title-logo.png` | 未用意（文字のタイトルを表示） |
| マス: 道・スタート・ゴール・壁・落とし穴 | `grass.png` `start.png` `goal.png` `wall.png` `pit.png` | 上から見た正方形の絵を推奨。斜めの絵は `crop` で調整 |
| マス: 接敵ゾーン | `zone.png` | 未用意（道の上に赤色を重ねる） |
| マス: スイッチ（off／on） | `switch-off.png` / `switch-on.png` | 未用意（仮表示）。記号(A〜E)はバッジで重ねる |
| マス: ドア（閉／開） | `door-closed.png` / `door-open.png` | 未用意（仮表示） |
| アイテム（物資／武器） | `item-supply.png` / `item-weapon.png` | 背景が透明な画像。マスの上に重ねる |
| アリロボット | `ant.png` | 向きごとの画像は `facing` に指定（なければ左右反転で代用） |
| 敵（動かない／巡回） | `spider.png` / `bee.png` | 背景が透明な画像 |
| リザルト（クリア／失敗） | `result-clear.png` / `result-fail.png` | |

- `assets/place.png` は、今の画面では使っていない。
- 元の見た目の見本は `view_sample/`（そのまま残してある）。

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
- `name:`（ステージ名）、`description:`（ステージ選択画面に出す一言説明。省略可）、`facing:`（アリの最初の向き。right / left / up / down。省略で right）、`map:`（以降の5行がマップ）の順に書く。
- 記号: `.`道 `#`壁 `P`落とし穴 `G`ゴール `S`スタート（1つだけ） `M`物資 `W`武器 `X`動かない敵 `1〜5`接敵ゾーン `a〜e`スイッチ `A〜E`ドア（`E`はドア用なので敵は`X`）。5行×9文字。
- スイッチとドアは同じ文字で対応（`a`→`A`）。複数の組、同じ記号の複数配置もOK。片方だけ置くとエラー。アイテムは道の上にのみ置ける。
- 文字コードは UTF-8。ファイル名の数字がステージ番号になる。
- リクエストのたびに読み込むので、**起動中の Tomcat でも `webapps\ant-game\WEB-INF\stages\` のファイルを書き換えれば再起動なしで反映**される。ただし再デプロイ（WAR の置き直し）をすると `src` 側の内容に戻るので、恒久的な変更は `src` 側のファイルに行うこと。
- 書き方に誤りがあると API は `500 STAGE_DATA_ERROR` を返し、原因は Tomcat のログ（`localhost.*.log`）に出る。起動時にも全ファイルを検査してログに出す。
- Tomcat の外の好きなフォルダに置きたい場合は、`web.xml` に次を追加する。
  ```xml
  <context-param><param-name>stageDir</param-name><param-value>D:\ant\stages</param-value></context-param>
  ```
- ステージを増やすときは、`stage<番号>.txt` を置くだけです（ステージ選択画面は、1〜9のうちファイルのあるものを自動で表示します）。

## API
### `GET api/stages`
`{"stages":[{"id":1,"name":"…","description":"…"}, …]}`（ステージ選択画面用。番号の小さい順）

### `GET api/stage?id=1`
`{"id":1,"name":"…","rows":["…5行…"],"start":{"x":0,"y":2},"facing":"RIGHT","enemies":[{"id":"z1","x":4,"y":0}]}`（`enemies` は敵の初期位置）

### `POST api/run`（Content-Type: application/json 必須）
```json
{ "stageId": 2,
  "program": [ {"id":"b1","type":"MOVE","dir":"RIGHT"},
               {"id":"b2","type":"JUMP","dir":"RIGHT"} ] }
```
- コマンド: `TURN`（`dir`: `UP` / `DOWN` / `LEFT` / `RIGHT` を向く）、`MOVE`（向いている方向へ1マス。`dir` なし）、`JUMP`（向いている方向へジャンプ。`dir` なし）、`WAIT`（その場で1ステップ待つ）
- 関数: `MOVE_FUNCTION`（`dir`: 向く方向、`count`: 移動回数 1〜100）
  ```json
  { "id":"f", "type":"MOVE_FUNCTION", "dir":"UP", "count":3 }
  ```
- 制御ブロック（入れ子。中にブロックの配列を持つ）:
  - `IF`: `condition`、`not`（省略可。true で反転）、`then`（なら）、`else`（そうでなければ。省略可）
  - `WHILE`: `condition`、`not`（省略可）、`body`
  - `REPEAT`: `count`（1〜100）、`body`
  ```json
  { "id":"c","type":"WHILE","condition":"ENEMY_COMING","not":false,
    "body":[ {"id":"d","type":"WAIT"} ] }
  ```
- `condition`: `WALL_AHEAD`(壁／マップの端) / `ENEMY_AHEAD`(目の前に今、敵がいる) / `PIT_AHEAD`(落とし穴) / `ENEMY_COMING`(敵が今いる、または次のステップで入ってくる)。「目の前」はアリが向いている方向の1マス先（最初は右向き）
- `id`: 英数字・`-`・`_` の1〜32文字。入れ子は6段まで、ブロックは合計200個まで

レスポンス:
```json
{ "status": "CLEARED", "failReason": null, "stepCount": 7,
  "trace": [ {"step":1,"blockId":"b1","result":"MOVED","x":1,"y":2,"facing":"RIGHT",
              "events":[{"type":"PICKUP","item":"SUPPLY","x":1,"y":2}],
              "inventory":{"supply":true,"weapon":false},
              "enemies":[{"id":"z1","x":4,"y":1}],
              "conditions":[{"blockId":"c","value":true}]} ] }
```
- `status`: `CLEARED` / `FAILED`
- `failReason`: `FELL_INTO_PIT` / `DEFEATED_BY_ENEMY` / `NOT_REACHED_GOAL` / `STEP_LIMIT_EXCEEDED` / `LOOP_LIMIT_EXCEEDED` / null
- `events`: その行動で自動的に起きたこと。`type` は `PICKUP`(item=SUPPLY/WEAPON) / `SWITCH_ON` / `DOOR_OPEN` / `ENEMY_DEFEATED`。x,y は起きたマス（開いたドアはドアの座標）
- `inventory`: 行動後の持ち物
- `enemies`: 行動後の、生きている全ての敵の位置（敵の移動は反映済み。ID は動かない敵が `x0`…、巡回する敵が `z1`〜`z5`）
- `conditions`: この行動の直前に評価した制御ブロックの条件と結果（画面のハイライト用）
- `result`: `MOVED` `JUMPED` `TURNED` `WAITED` `BLOCKED_WALL` `BLOCKED_DOOR` `BLOCKED_OUT_OF_MAP` `JUMP_FAILED_NO_PIT` `JUMP_FAILED_BLOCKED` `FELL_INTO_PIT` `DEFEATED_BY_ENEMY` `REACHED_GOAL`
- エラー: `{"error":"コード"}`（400 / 404 / 413 / 415）

## ソース構成
```
src/main/java/com/terafes/ant/
  game/     Direction, Position, CellType, GameMap, GameEngine, Stage, StageRepository(ファイル読込) …ゲームのルール
  program/  Block, BlockType, ProgramRunner, RunResult, TraceStep …プログラムの実行
  json/     JsonParser, JsonWriter …自作の最小JSON処理
  api/      RunServlet, StageServlet, StagesServlet, RunRequest, SecurityHeadersFilter …HTTP入出力と入力検証
src/main/webapp/   画面（index/stages/game/result-clear/result-fail.html, js/, css/game.css, assets/）
src/test/java/     自作の簡易テスト（TestMain から実行）
```

## セキュリティ対策
- **XSS**: JSON出力は `< > & '` を `\uXXXX` にエスケープ／`X-Content-Type-Options: nosniff`／CSP（自サイトのスクリプトのみ）／画面側は `textContent` のみ使用／ブロックIDは英数字のみ許可／エラーに入力値を含めない。
- **入力検証**: ステージID・種別・方向はホワイトリスト、本文64KB・ブロック200個・JSON深さ20の上限、ステップ100回の強制停止。
- **SQLインジェクション**: DBを使わないため該当なし（将来DBを使う場合は `PreparedStatement` を使うこと）。

## 今後の拡張ポイント
- 敵の種類・強さ、武器の種類・耐久: 現在は「武器の有無」のみで判定している（`GameEngine.fightEnemy`）。
- 制御ブロック（もし／繰り返し）: `BlockType` と `Block` を拡張し、`ProgramRunner` でネストを展開しつつ、同じ100ステップ制限を適用。
