'use strict';

/*
 * ステージ選択画面のスクリプト。
 * サーバーのステージ一覧(api/stages)から、9ステージぶんのカードを作ります。
 * ステージファイルがまだないステージは、「じゅんびちゅう」の押せないカードになります。
 */
(async () => {
  AntRetry.discard(); // ステージを選び直すので、やり直し用に預かっていたプログラムは捨てる
  const SLOTS = 9; // 表示するステージの数
  const screen = document.getElementById('screen');
  AntAssets.applyBackground(screen, 'stages', 'linear-gradient(180deg,#74d3ff 0 31%,#e8fbff 31% 100%)');

  const grid = document.getElementById('stageGrid');
  const result = await AntApi.getStages();
  const stages = result.ok ? result.data.stages : [];

  // ステージ番号 → ステージ情報
  const byId = new Map(stages.map((s) => [s.id, s]));

  for (let id = 1; id <= SLOTS; id++) {
    const info = byId.get(id);
    // ステージがあればリンク、なければ押せないカード
    const card = AntUi.el(info ? 'a' : 'div', 'stage-card' + (info ? '' : ' locked'));
    if (info) { card.href = 'game.html?stage=' + id; }
    card.appendChild(AntUi.el('div', 'num', String(id)));
    card.appendChild(AntUi.el('strong', null, info ? info.name : 'じゅんびちゅう'));
    card.appendChild(AntUi.el('p', null, info ? info.description : ''));
    grid.appendChild(card);
  }

  if (!result.ok) {
    grid.before(AntUi.el('p', null, 'ステージの一覧を読み込めませんでした。' + AntApi.errorText(result.error)));
  }
})();
