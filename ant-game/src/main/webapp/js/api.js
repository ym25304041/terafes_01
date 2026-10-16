'use strict';

/*
 * サーバー（Java）の API を呼び出す部分と、サーバーが返すコードに対応する日本語メッセージです。
 *
 *   GET  api/stages           … ステージの一覧
 *   GET  api/stage?id=N       … 1ステージぶんのデータ（マップ・敵の位置・最初の向きなど）
 *   POST api/run              … プログラムを実行して、結果（トレース）を受け取る
 */
const AntApi = (() => {

  // 失敗理由のコードに対応するメッセージ
  const FAIL_MESSAGE = {
    FELL_INTO_PIT: '落とし穴に落ちてしまった…',
    DEFEATED_BY_ENEMY: '武器を持たずに敵と戦い、負けてしまった…',
    NOT_REACHED_GOAL: 'ゴールにたどり着けなかった…',
    STEP_LIMIT_EXCEEDED: '実行回数の上限(100回)を超えたので、止めたよ。',
    LOOP_LIMIT_EXCEEDED: '処理回数の上限(1000回)を超えたので、止めたよ。(中身が空のくり返しなど)'
  };
  const FAIL_CODES = Object.keys(FAIL_MESSAGE);

  // 実行中に画面の下に出す、ひとことメッセージ
  const RESULT_NOTE = {
    BLOCKED_DOOR: 'ドアが閉じています',
    BLOCKED_WALL: '壁があって進めません',
    BLOCKED_OUT_OF_MAP: 'マップの外には進めません',
    DROP_FAILED_NO_ITEM: 'アイテムを持っていないので、捨てられません',
    DROP_FAILED_OCCUPIED: 'このマスにはすでにアイテムがあるので、捨てられません',
    JUMP_FAILED_NO_PIT: 'ジャンプできません(前が落とし穴ではありません)',
    JUMP_FAILED_BLOCKED: 'ジャンプできません(着地点に進めません)'
  };

  // サーバーのエラーコードに対応するメッセージ
  const ERROR_MESSAGE = {
    STAGE_NOT_FOUND: 'ステージが見つかりません。',
    STAGE_DATA_ERROR: 'ステージのデータに問題があります。',
    PROGRAM_TOO_DEEP: 'ブロックの入れ子が深すぎます(6段まで)。',
    PROGRAM_TOO_LONG: 'ブロックが多すぎます(200個まで)。'
  };

  /** エラーコードから、表示するメッセージを作ります。 */
  function errorText(code) {
    return ERROR_MESSAGE[code] || 'うまく実行できませんでした。ブロックを確認してください。';
  }

  /** 通信してJSONを受け取ります。失敗したときは、{ok:false, error:コード} を返します。 */
  async function request(url, options) {
    try {
      const res = await fetch(url, options);
      const data = await res.json();
      if (!res.ok) { return { ok: false, error: data.error || 'UNKNOWN' }; }
      return { ok: true, data: data };
    } catch (e) {
      return { ok: false, error: 'NETWORK' };
    }
  }

  /** ステージの一覧を取得します。 */
  function getStages() {
    return request('api/stages');
  }

  /** 1ステージぶんのデータを取得します。 */
  function getStage(id) {
    return request('api/stage?id=' + encodeURIComponent(id));
  }

  /** プログラムを実行します。 */
  function run(stageId, program) {
    return request('api/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ stageId: stageId, program: program })
    });
  }

  return { FAIL_MESSAGE, FAIL_CODES, RESULT_NOTE, errorText, getStages, getStage, run };
})();
