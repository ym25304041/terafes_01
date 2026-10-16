'use strict';

/*
 * タイトル画面のスクリプト: 背景・ロゴ・アリロボットの画像を、素材の設定(assets.js)から表示します。
 * ロゴ画像がまだないときは、HTML に書いてある文字のタイトルがそのまま表示されます。
 */
(async () => {
  const screen = document.getElementById('screen');
  AntAssets.applyBackground(screen, 'title', 'linear-gradient(180deg,#74d3ff 0 31%,#e8fbff 31% 100%)');

  // タイトルロゴ: 画像が読み込めたときだけ、文字のタイトルと入れ替える
  const logoFile = AntAssets.MANIFEST.logo;
  if (await AntAssets.exists(logoFile)) {
    const logo = new Image();
    logo.className = 'title-logo';
    logo.alt = 'アント・コマンド（仮）';
    logo.src = AntAssets.url(logoFile);
    document.getElementById('titleText').replaceWith(logo);
  }

  // アリロボットの画像
  const visualFile = AntAssets.MANIFEST.titleVisual;
  if (await AntAssets.exists(visualFile)) {
    const visual = new Image();
    visual.alt = 'アリロボット';
    visual.src = AntAssets.url(visualFile);
    document.getElementById('titleVisual').appendChild(visual);
  }
})();
