'use strict';

/*
 * リザルト画面(クリア・失敗)のスクリプト。
 *
 * ゲーム画面から、次のような URL で移動してきます。
 *   result-clear.html?stage=3&steps=16             … クリア(steps は実行回数)
 *   result-fail.html?stage=3&reason=FELL_INTO_PIT  … 失敗(reason は失敗理由)
 * URL の値は外部から届く値なので、数値・許可した値の一覧に当てはまるものだけを使います。
 */
(async () => {
  const screen = document.getElementById('screen');
  const isClear = screen.dataset.result === 'clear';
  AntAssets.applyBackground(screen, 'result', 'linear-gradient(180deg,#74d3ff 0 31%,#e8fbff 31% 100%)');

  const stageId = AntUi.intParam('stage', 1, 9999, 1);
  const info = document.getElementById('resultInfo');
  const popup = document.getElementById('popup');

  // 大きな画像(ステージクリア！／ステージ失敗…)。なければ文字で表示する
  const file = isClear ? AntAssets.MANIFEST.result.clear : AntAssets.MANIFEST.result.fail;
  if (await AntAssets.exists(file)) {
    const img = new Image();
    img.className = 'result-popup';
    img.alt = isClear ? 'ステージクリア' : 'ステージ失敗';
    img.src = AntAssets.url(file);
    popup.appendChild(img);
  } else {
    popup.appendChild(AntUi.el('div', 'result-fallback' + (isClear ? '' : ' fail'),
      isClear ? 'ステージクリア！' : 'ステージ失敗…'));
  }

  if (isClear) {
    // 実行回数(クリアまでにかかったステップ数)を表示する
    const steps = AntUi.intParam('steps', 0, 100, 0);
    info.textContent = 'ステージ ' + stageId + '　実行回数: ' + steps + ' 回';

    // 「次のステージへ」: 次のステージがあればそのステージへ、なければステージ選択へ
    const next = document.getElementById('nextButton');
    const stages = await AntApi.getStages();
    const hasNext = stages.ok && stages.data.stages.some((s) => s.id === stageId + 1);
    next.href = hasNext ? 'game.html?stage=' + (stageId + 1) : 'stages.html';
    if (!hasNext) { next.textContent = 'ステージ選択へ'; }
  } else {
    const reason = AntUi.listParam('reason', AntApi.FAIL_CODES, 'NOT_REACHED_GOAL');
    info.textContent = AntApi.FAIL_MESSAGE[reason];
    // 「もう一度挑戦する」: 同じステージのゲーム画面へ
    document.getElementById('retryButton').href = 'game.html?stage=' + stageId;
  }
})();
