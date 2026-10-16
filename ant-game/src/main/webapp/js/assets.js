'use strict';

/*
 * ============================================================
 *  画像素材の設定ファイル（素材を差し替えるときは、主にここを見ます）
 * ============================================================
 *
 * 【差し替え方】
 *   1. 同じファイル名の画像を assets フォルダに上書きすれば、そのまま差し替わります。
 *   2. ファイル名を変えたいときは、下の MANIFEST の file を書き換えます。
 *   3. 画像がまだ無い（見つからない）ときは、絵文字などの仮の表示になります。
 *      画面が壊れることはないので、素材ができたものから順に追加できます。
 *
 * 【画像の作り方の目安】
 *   ・マスの画像（tiles）: 正方形。上から見下ろした絵で、隙間なくタイルとして並べます。
 *     ぴったり正方形の絵なら crop を指定する必要はありません（省略すれば全体を表示します）。
 *   ・アリロボットは、武器を持っているときだけ、別の画像（armedFile）に切り替わります。
 *   ・アリ・敵・アイテムの画像: 背景が透明な PNG。マスの上に重ねて表示します。
 *   ・背景・ロゴ・リザルトの画像: 自由なサイズ。
 *
 * 【crop（切り抜き）の意味】※ 今ある仮素材のような、斜めから見た絵を正方形に収めるための調整です。
 *   cx, cy : 画像の中の、表示したい範囲の中心位置（%）。50, 50 が画像の中央
 *   zoom   : 拡大率。1 なら画像全体、2 なら半分の範囲を拡大して表示
 *
 * 【fallback（仮の表示）】
 *   画像が見つからないときの絵文字(glyph)と、マスの背景色(bg)です。
 */
const AntAssets = (() => {

  const DIR = 'assets/'; // 画像を置くフォルダ（このフォルダからの相対パス）

  const MANIFEST = {

    // 各画面の背景
    backgrounds: {
      title: 'bg-title.png',     // タイトル画面
      stages: 'bg-stages.png',   // ステージ選択画面
      game: 'game-bg.png',       // ゲームプレイ画面
      result: 'game-bg.png'      // リザルト画面（クリア・失敗）
    },

    // タイトルロゴ（なければ、文字のタイトルを表示します）
    logo: 'title-logo.png',

    // タイトル画面に表示するアリロボットの画像
    titleVisual: 'ant.png',

    // リザルト画面の大きな画像
    result: {
      clear: 'result-clear.png',
      fail: 'result-fail.png'
    },

    // マップの各マス（地形）。全マスを隙間なく並べます
    // ぴったり正方形の絵なので、crop（切り抜き）は使っていません。bg は、絵の角が丸く透明な部分を埋める色です
    tiles: {
      road:  { file: 'grass.webp', bg: '#7fcf2a' },   // 道（草）
      start: { file: 'grass.webp', bg: '#7fcf2a' },   // スタート地点（専用の絵がないので、道と同じ）
      goal:  { file: 'goal.webp',  bg: '#7fcf2a' },   // ゴール（旗）
      wall:  { file: 'wall.webp',  bg: '#2a303c' },   // 壁（石）
      pit:   { file: 'pit.webp',   bg: '#7fcf2a' },   // 落とし穴
      // 接敵ゾーン（敵が往復する範囲）。画像がなければ、道の上に赤い色を重ねます
      zone:  { file: 'zone.webp', fallbackTile: 'road', tint: 'rgba(255, 70, 70, 0.38)' },
      // スイッチ（off＝作動前／on＝作動後）とドア（閉／開）
      'switch-off':  { file: 'switch-off.webp',  bg: '#7fcf2a', fallback: { glyph: '🔘', bg: '#b9c6d8' } },
      'switch-on':   { file: 'switch-on.webp',   bg: '#7fcf2a', fallback: { glyph: '✅', bg: '#9fe0a8' } },
      'door-closed': { file: 'door-closed.webp', bg: '#2a303c', fallback: { glyph: '🚪', bg: '#b9824a' } },
      'door-open':   { file: 'door-open.webp',   bg: '#2a303c', fallback: { glyph: '▫', bg: '#d9c7a1' } }
    },

    // アイテム（マスの上に重ねる、背景が透明な画像）
    items: {
      supply: { file: 'item-supply.webp', fallback: { glyph: '📦' } },   // 物資（木箱）
      weapon: { file: 'item-weapon.webp', fallback: { glyph: '⚔' } }     // 武器（砲台）
    },

    // アリロボットと敵（背景が透明な画像）
    units: {
      // armedFile: 武器を持っているときの、アリロボットの画像（持っていないときは file）
      // facing に、向きごとの画像を書くと、向きに合わせて画像が切り替わります（なければ null のまま）。
      // null のときは、1枚の画像を、左右の向きに合わせて反転して使います。
      ant: {
        file: 'ant.png', armedFile: 'ant-armed.webp',
        facing: { UP: null, DOWN: null, LEFT: null, RIGHT: null }, flipWhenRight: true
      },
      enemyStatic: { file: 'spider.png', fallback: { glyph: '🕷' } },   // 動かない敵
      enemyPatrol: { file: 'bee.png', fallback: { glyph: '🐝' } }       // 巡回する敵
    }
  };

  /** ファイル名から、画像のURLを作ります。 */
  function url(file) {
    return DIR + file;
  }

  /**
   * 要素の背景に、画面ごとの背景画像を設定します。
   * 画像が見つからなくても、下に重ねてある gradient（グラデーション）が表示されます。
   */
  function applyBackground(element, name, gradient) {
    const file = MANIFEST.backgrounds[name];
    element.style.backgroundImage = 'url("' + url(file) + '"), ' + gradient;
    element.style.backgroundSize = 'cover, cover';
    element.style.backgroundPosition = 'center, center';
    element.style.backgroundRepeat = 'no-repeat, no-repeat';
  }

  /**
   * 画像を読み込めるか確認します（ロゴなど、「あれば使う」素材用）。
   * @returns {Promise<boolean>} 読み込めれば true
   */
  function exists(file) {
    return new Promise((resolve) => {
      const img = new Image();
      img.onload = () => resolve(true);
      img.onerror = () => resolve(false);
      img.src = url(file);
    });
  }

  /** 画像が見つからないときの、仮の表示（絵文字）の要素を作ります。 */
  function glyph(fallback) {
    return AntUi.el('span', 'glyph', fallback.glyph);
  }

  /**
   * マス1つぶんのタイル（地形）の要素を作ります。
   * 画像を、マスいっぱいに表示します。見つからなければ、背景色と絵文字の仮の表示になります。
   *
   * @param key MANIFEST.tiles のキー（'road', 'wall', 'switch-off' など）
   */
  function tile(key) {
    const def = MANIFEST.tiles[key];
    const box = AntUi.el('div', 'tile tile-' + key);
    if (def.bg) { box.style.backgroundColor = def.bg; }
    if (def.fallback && def.fallback.bg) { box.style.backgroundColor = def.fallback.bg; }

    const img = new Image();
    img.alt = '';
    img.draggable = false;
    if (def.crop) {
      // 画像の指定範囲(cx, cy を中心に zoom 倍)が、マスの中央に来るように、移動と拡大をする
      const c = def.crop;
      img.style.transformOrigin = c.cx + '% ' + c.cy + '%';
      img.style.transform = 'translate(' + (50 - c.cx) + '%, ' + (50 - c.cy) + '%) scale(' + c.zoom + ')';
    }
    img.onerror = () => {
      // 画像が見つからないとき
      box.textContent = '';
      if (def.fallbackTile) {
        // 別のマスの画像で代用し、色を重ねる（接敵ゾーンなど）
        box.appendChild(tile(def.fallbackTile));
        if (def.tint) {
          const tint = AntUi.el('div', 'tint');
          tint.style.backgroundColor = def.tint;
          box.appendChild(tint);
        }
      } else if (def.fallback) {
        box.appendChild(glyph(def.fallback));
      }
    };
    img.src = url(def.file);
    box.appendChild(img);
    return box;
  }

  /**
   * アイテムの画像の要素を作ります（マスの上に重ねる）。
   * @param kind 'supply'（物資）または 'weapon'（武器）
   */
  function item(kind) {
    const def = MANIFEST.items[kind];
    const box = AntUi.el('div', 'item item-' + kind);
    const img = new Image();
    img.alt = '';
    img.draggable = false;
    img.onerror = () => {
      box.textContent = '';
      box.appendChild(glyph(def.fallback));
    };
    img.src = url(def.file);
    box.appendChild(img);
    return box;
  }

  /**
   * アリロボット・敵の画像の要素を返します。
   * @param kind    'ant' / 'enemyStatic' / 'enemyPatrol'
   * @param facing  アリの向き（'UP' など）。向きごとの画像があれば、それを使う
   * @param armed   武器を持っているか（アリだけ。true なら、武器を持った姿の画像を使う）
   */
  function unitImage(kind, facing, armed) {
    const def = MANIFEST.units[kind];
    const img = new Image();
    img.alt = '';
    img.draggable = false;
    img.className = 'sprite';
    img.src = url(unitFile(kind, facing, armed));
    return img;
  }

  /**
   * アリ・敵の画像のファイル名を返します。
   * 武器を持っているアリは armedFile（なければ通常の画像）。向きごとの画像があれば、それを優先します。
   */
  function unitFile(kind, facing, armed) {
    const def = MANIFEST.units[kind];
    if (def.facing && facing && def.facing[facing]) { return def.facing[facing]; }
    if (armed && def.armedFile) { return def.armedFile; }
    return def.file;
  }

  /** アリロボットの画像を、右向きのときに左右反転するかどうか。 */
  function antFlipsWhenRight() {
    return !!MANIFEST.units.ant.flipWhenRight;
  }

  return { MANIFEST, url, applyBackground, exists, tile, item, unitImage, unitFile, antFlipsWhenRight, glyph };
})();
