'use strict';

/*
 * ゲームプレイ画面のスクリプトです。
 *
 * 【全体の流れ】
 *   1. URL の ?stage=番号 のステージを、サーバー(api/stage)から取得して、マップを描く
 *   2. ブロックをドラッグ＆ドロップ(またはクリック)で、プログラムに組み立てる
 *   3. 「実行」を押すと、プログラムをサーバー(api/run)へ送る。サーバーがゲームを最後まで計算して、
 *      1ステップごとの記録(トレース)を返す
 *   4. 受け取ったトレースを、1ステップずつ再生する(アリと敵が動き、ブロックが光る)
 *   5. 最後まで再生したら、結果の画面(クリア／失敗)へ移動する
 *
 * 【プログラムの持ち方】
 *   プログラムは「ブロックの配列」で、制御ブロックは中に配列を持ちます(入れ子)。
 *   この形は、そのままサーバー(api/run)へ送る JSON になります。
 *     向く         : {id, type:'TURN', dir}
 *     前へ移動     : {id, type:'MOVE'}          (アリが今向いている方向へ進む)
 *     前へジャンプ : {id, type:'JUMP'}
 *     待つ         : {id, type:'WAIT'}
 *     移動関数     : {id, type:'MOVE_FUNCTION', dir, count}  (方向を向いて、回数ぶん移動する)
 *     もし         : {id, type:'IF', condition, not, then:[...], else:[...]}
 *     の間         : {id, type:'WHILE', condition, not, body:[...]}
 *     回くり返す   : {id, type:'REPEAT', count, body:[...]}
 *
 * 【XSS 対策】画面に文字を出すときは textContent だけを使います(AntUi.el)。innerHTML は使いません。
 */
(() => {

  const COLS = 9;  // マップの横のマス数
  const ROWS = 5;  // マップの縦のマス数
  const STEP_MS = 420; // 再生1ステップの長さ(ミリ秒)

  const DIR_LABEL = { UP: '↑ 上', DOWN: '↓ 下', LEFT: '← 左', RIGHT: '→ 右' };
  const DIR_ARROW = { UP: '▲', DOWN: '▼', LEFT: '◀', RIGHT: '▶' };
  // 条件の表示名: [成り立つとき, 成り立たない(「〜でない」)とき]
  const COND_LABEL = {
    WALL_AHEAD: ['目の前に壁がある', '目の前に壁がない'],
    ENEMY_AHEAD: ['目の前に敵がいる', '目の前に敵がいない'],
    PIT_AHEAD: ['目の前に落とし穴がある', '目の前に落とし穴がない'],
    ENEMY_COMING: ['目の前に敵が来る', '目の前に敵が来ない']
  };

  // ブロックのパレット(グループごと)
  const PALETTE = [
    { group: 'コマンド', items: [['TURN', '向く'], ['MOVE', '前へ移動'], ['JUMP', '前へジャンプ'], ['WAIT', '待つ']] },
    { group: '関数', items: [['MOVE_FUNCTION', '移動関数']] },
    { group: '制御', items: [['IF', 'もし…なら'], ['WHILE', '…の間'], ['REPEAT', '回くり返す']] }
  ];
  const BLOCK_CLASS = {
    TURN: 'b-turn', MOVE: 'b-move', JUMP: 'b-jump', WAIT: 'b-wait',
    MOVE_FUNCTION: 'b-func', IF: 'b-if', WHILE: 'b-while', REPEAT: 'b-repeat'
  };
  const BLOCK_ICON = {
    TURN: '⟳', MOVE: '➡', JUMP: '⬆', WAIT: '⏳', MOVE_FUNCTION: '🏃', IF: '？', WHILE: '🔁', REPEAT: '↻'
  };

  const stageId = AntUi.intParam('stage', 1, 9999, 1);

  // 画面の部品
  const $ = (id) => document.getElementById(id);
  const mapWrap = $('mapWrap');
  const mapStage = $('mapStage');
  const mapGrid = $('mapGrid');
  const unitsEl = $('units');
  const programEl = $('program');
  const messageEl = $('message');
  const inventoryEl = $('inventory');
  const runButton = $('runButton');

  // 状態
  let stage = null;          // 読み込んだステージ情報
  let view = [];             // 表示用のマップ(2次元配列の記号)。アイテム取得・ドア開放などで書き換える
  let letters = [];          // スイッチ・ドアの記号(a〜e)。バッジの表示に使う
  let cellEls = [];          // マスの要素(cellEls[y][x])
  let antEl = null;          // アリロボットの要素
  let enemyEls = new Map();  // 敵のID → 敵の要素
  let inventory = { supply: false, weapon: false };
  let facing = 'RIGHT';      // 画面に表示するアリの向き
  let program = [];          // プログラム(ブロックの配列。入れ子)
  let target = program;      // 次にブロックを追加する先の配列(選んだ枠)
  let nextBlockNo = 1;       // ブロックIDの連番
  let blockEls = new Map();  // ブロックID → 画面上のブロックの行(ハイライト用)
  let playing = false;       // 再生中か
  let runToken = 0;          // 実行の番号。「やり直す」で番号を進めて、再生を打ち切る

  function showMessage(text, kind) {
    messageEl.textContent = text;
    messageEl.className = 'message-bar' + (kind ? ' ' + kind : '');
  }

  // ======================================================================
  // マップの表示
  // ======================================================================

  /** 表示用マップの記号から、マスの地形の画像(タイル)のキーを決める。 */
  function tileKeyOf(ch) {
    if (ch === '#') { return 'wall'; }
    if (ch === 'P') { return 'pit'; }
    if (ch === 'G') { return 'goal'; }
    if (ch === 'S') { return 'start'; }
    if (ch >= '1' && ch <= '5') { return 'zone'; }
    if (ch >= 'a' && ch <= 'e') { return 'switch-off'; }
    if (ch === '!') { return 'switch-on'; }   // 作動済みのスイッチ(画面専用の記号)
    if (ch >= 'A' && ch <= 'E') { return 'door-closed'; }
    if (ch === '_') { return 'door-open'; }   // 開いたドア(画面専用の記号)
    return 'road';                            // '.'(道)、'M'・'W'(アイテムのある道)
  }

  /** 1マスぶんの表示(タイル・アイテム・記号のバッジ)を作り直す。 */
  function updateCell(x, y) {
    const cell = cellEls[y][x];
    const ch = view[y][x];
    cell.textContent = '';
    cell.appendChild(AntAssets.tile(tileKeyOf(ch)));
    if (ch === 'M') { cell.appendChild(AntAssets.item('supply')); }
    if (ch === 'W') { cell.appendChild(AntAssets.item('weapon')); }
    const letter = letters[y][x];
    if (letter) {
      // スイッチとドアは、対応する記号(A〜E)を、バッジで表示する
      cell.appendChild(AntUi.el('span', 'badge badge-' + letter, letter.toUpperCase()));
    }
  }

  /** マップの 9×5 のマスを作る(最初に1回だけ)。 */
  function buildGrid() {
    mapGrid.textContent = '';
    cellEls = [];
    for (let y = 0; y < ROWS; y++) {
      const row = [];
      for (let x = 0; x < COLS; x++) {
        const cell = AntUi.el('div', 'cell');
        mapGrid.appendChild(cell);
        row.push(cell);
      }
      cellEls.push(row);
    }
  }

  /** 画面の広さに合わせて、1マスの大きさを決める(マスは正方形。隙間なく並べる)。 */
  function fitMap() {
    const w = mapWrap.clientWidth;
    const h = mapWrap.clientHeight;
    const size = Math.max(24, Math.floor(Math.min(w / COLS, h / ROWS)));
    mapStage.style.setProperty('--cell', size + 'px');
  }

  /** アリ・敵などの要素(ユニット)を置く位置(マスの座標)を設定する。CSS の transition で滑らかに動く。 */
  function placeUnit(el, x, y) {
    el.style.setProperty('--x', x);
    el.style.setProperty('--y', y);
  }

  /** アリロボットの向きを、画像と矢印のバッジに反映する。 */
  function setFacing(f) {
    facing = f;
    const sprite = antEl.querySelector('.sprite');
    if (sprite) { sprite.src = AntAssets.url(AntAssets.unitFile('ant', f)); }
    // 向きごとの画像がないときは、右向きのとき左右反転する(元の絵は左向き)
    const hasFacingImage = !!AntAssets.MANIFEST.units.ant.facing[f];
    antEl.classList.toggle('face-right', !hasFacingImage && f === 'RIGHT' && AntAssets.antFlipsWhenRight());
    antEl.querySelector('.facing-badge').textContent = DIR_ARROW[f];
  }

  /** アリ・敵の画像の要素を作る。画像が見つからないときは、絵文字で代用する。 */
  function makeUnitImage(kind, fallbackGlyph) {
    const img = AntAssets.unitImage(kind, 'LEFT');
    img.onerror = () => {
      const fb = AntAssets.MANIFEST.units[kind].fallback;
      img.replaceWith(AntUi.el('span', 'glyph', fb ? fb.glyph : fallbackGlyph));
    };
    return img;
  }

  /** 敵の要素を作る。IDが z で始まる敵は巡回する敵、x で始まる敵は動かない敵。 */
  function makeEnemy(id) {
    const kind = id.startsWith('z') ? 'enemyPatrol' : 'enemyStatic';
    const el = AntUi.el('div', 'unit enemy');
    el.appendChild(makeUnitImage(kind, '👾'));
    return el;
  }

  /** ステージの最初の状態に戻す(マップ・アリ・敵・持ち物)。 */
  function resetBoard() {
    // 動かない敵 'X' は道として扱い、敵は enemies の位置から別に描く
    view = stage.rows.map((row) => row.replace(/X/g, '.').split(''));
    letters = stage.rows.map((row) => row.split('').map((ch) => {
      if (ch >= 'a' && ch <= 'e') { return ch; }
      if (ch >= 'A' && ch <= 'E') { return ch.toLowerCase(); }
      return '';
    }));
    for (let y = 0; y < ROWS; y++) {
      for (let x = 0; x < COLS; x++) { updateCell(x, y); }
    }

    // アリ
    unitsEl.textContent = '';
    antEl = AntUi.el('div', 'unit ant');
    antEl.appendChild(makeUnitImage('ant', '🐜'));
    antEl.appendChild(AntUi.el('span', 'facing-badge', ''));
    unitsEl.appendChild(antEl);
    placeUnit(antEl, stage.start.x, stage.start.y);
    setFacing(stage.facing);

    // 敵
    enemyEls = new Map();
    stage.enemies.forEach((e) => {
      const el = makeEnemy(e.id);
      unitsEl.appendChild(el);
      placeUnit(el, e.x, e.y);
      enemyEls.set(e.id, el);
    });

    inventory = { supply: false, weapon: false };
    drawInventory();
  }

  /** 持ち物(物資・武器)の表示を更新する。持っているものは明るく表示する。 */
  function drawInventory() {
    inventoryEl.textContent = '';
    [['supply', '物資'], ['weapon', '武器']].forEach(([kind, label]) => {
      const slot = AntUi.el('div', 'inv-slot' + (inventory[kind] ? ' have' : ''));
      slot.appendChild(AntAssets.item(kind));
      slot.appendChild(AntUi.el('span', null, label));
      inventoryEl.appendChild(slot);
    });
    inventoryEl.appendChild(AntUi.el('span', null, '向き ' + DIR_ARROW[facing]));
  }

  /** 自動で起きた出来事(アイテム取得・スイッチ作動・ドアが開く)を、マップの表示に反映する。 */
  function applyEvents(events) {
    events.forEach((e) => {
      if (e.type === 'PICKUP') { view[e.y][e.x] = '.'; updateCell(e.x, e.y); }
      if (e.type === 'SWITCH_ON') { view[e.y][e.x] = '!'; updateCell(e.x, e.y); }
      if (e.type === 'DOOR_OPEN') { view[e.y][e.x] = '_'; updateCell(e.x, e.y); }
      // 敵の討伐は、敵の位置の一覧から、倒された敵がいなくなることで表現される
    });
  }

  /** ステージのマップから、クリア条件の説明を作る。 */
  function conditionTexts() {
    const text = stage.rows.join('');
    const list = ['ゴールに到達する'];
    if (/[X1-5]/.test(text)) { list.push('敵にやられない(武器を使おう)'); }
    if (/[a-e]/.test(text)) { list.push('物資でスイッチを押してドアを開ける'); }
    if (/P/.test(text) && list.length < 3) { list.push('落とし穴に落ちない'); }
    return list.slice(0, 3);
  }

  /** ミッションの表示(ステージ番号・名前・説明・クリア条件)。 */
  function drawMission() {
    $('stageNo').textContent = String(stage.id);
    $('missionTitle').textContent = stage.name;
    $('missionDesc').textContent = stage.description || 'アリロボットを動かして、ゴールを目指そう。';
    const box = $('conditions');
    box.textContent = '';
    box.appendChild(AntUi.el('div', null, '⭐ クリア条件'));
    conditionTexts().forEach((t) => box.appendChild(AntUi.el('div', null, '○ ' + t)));
  }

  // ======================================================================
  // プログラム(ブロック)の編集
  // ======================================================================

  /** 新しいブロックを作る(種類ごとの初期値つき)。 */
  function newBlock(type) {
    const id = 'b' + (nextBlockNo++);
    switch (type) {
      case 'TURN': return { id: id, type: type, dir: 'RIGHT' };
      case 'MOVE_FUNCTION': return { id: id, type: type, dir: 'RIGHT', count: 3 };
      case 'IF': return { id: id, type: type, condition: 'WALL_AHEAD', not: false, then: [], else: [] };
      case 'WHILE': return { id: id, type: type, condition: 'WALL_AHEAD', not: false, body: [] };
      case 'REPEAT': return { id: id, type: type, count: 3, body: [] };
      default: return { id: id, type: type }; // MOVE / JUMP / WAIT
    }
  }

  /** ブロックの中にある、ブロックの配列(なら／そうでなければ／中身)を返す。 */
  function childArrays(block) {
    return [block.then, block.else, block.body].filter((a) => Array.isArray(a));
  }

  /** 配列の中から、IDのブロックを探す(入れ子の中も探す)。 */
  function findBlock(arr, id) {
    for (let i = 0; i < arr.length; i++) {
      if (arr[i].id === id) { return { arr: arr, idx: i, block: arr[i] }; }
      for (const child of childArrays(arr[i])) {
        const found = findBlock(child, id);
        if (found) { return found; }
      }
    }
    return null;
  }

  /** block の中(入れ子の中も含む)に、配列 arr があるか。自分の中にブロックを移動するのを防ぐために使う。 */
  function containsArray(block, arr) {
    return childArrays(block).some((child) => child === arr || child.some((b) => containsArray(b, arr)));
  }

  /** ブロックを消す。 */
  function removeBlock(id) {
    const found = findBlock(program, id);
    if (!found) { return; }
    found.arr.splice(found.idx, 1);
    // 追加先に選んでいた枠が、消したブロックの中だったら、プログラムの最後に戻す
    if (target !== program && !isReachable(target)) { target = program; }
    renderProgram();
  }

  /** 配列 arr が、プログラムの中につながっているか。 */
  function isReachable(arr) {
    return arr === program || program.some((b) => containsArray(b, arr));
  }

  /**
   * ドロップされたブロックを、配列の指定位置に入れる。
   * @param arr   入れ先の配列
   * @param index 入れる位置
   * @param data  ドラッグしてきたデータ('new:種類' = パレットから、'move:ID' = プログラムの中から)
   */
  function dropInto(arr, index, data) {
    if (data.startsWith('new:')) {
      const type = data.slice(4);
      if (!BLOCK_CLASS[type]) { return; } // 知らない種類は無視する
      arr.splice(index, 0, newBlock(type));
    } else if (data.startsWith('move:')) {
      const found = findBlock(program, data.slice(5));
      if (!found || containsArray(found.block, arr)) { return; } // 自分の中には入れられない
      found.arr.splice(found.idx, 1);
      // 同じ配列の中で、前から後ろへ動かすときは、取り除いた分だけ位置がずれる
      const at = (found.arr === arr && found.idx < index) ? index - 1 : index;
      arr.splice(at, 0, found.block);
    } else {
      return;
    }
    renderProgram();
  }

  /** パレットのクリックなどで、選んでいる枠の最後にブロックを追加する。 */
  function addToTarget(type) {
    if (playing) { return; }
    target.push(newBlock(type));
    renderProgram();
  }

  /** ドロップできる場所にする(ドラッグ中に枠を強調し、ドロップを受け付ける)。 */
  function enableDrop(element, arr, indexOf) {
    element.addEventListener('dragover', (e) => {
      if (playing) { return; }
      e.preventDefault();      // これでドロップできるようになる
      e.stopPropagation();     // 内側の枠が優先される
      element.classList.add(indexOf ? 'drop-before' : 'drop-over');
    });
    element.addEventListener('dragleave', () => {
      element.classList.remove('drop-before', 'drop-over');
    });
    element.addEventListener('drop', (e) => {
      e.preventDefault();
      e.stopPropagation();
      element.classList.remove('drop-before', 'drop-over');
      if (playing) { return; }
      dropInto(arr, indexOf ? indexOf() : arr.length, e.dataTransfer.getData('text/plain'));
    });
  }

  /** 方向の選択欄を作る。 */
  function dirSelect(value, onChange) {
    const sel = AntUi.el('select');
    Object.keys(DIR_LABEL).forEach((d) => {
      const o = AntUi.el('option', null, DIR_LABEL[d]);
      o.value = d;
      sel.appendChild(o);
    });
    sel.value = value;
    sel.addEventListener('change', () => onChange(sel.value));
    return sel;
  }

  /** 条件の選択欄を作る(「〜がある」「〜がない」の両方を、1つの選択欄に入れる)。 */
  function condSelect(block) {
    const sel = AntUi.el('select');
    Object.keys(COND_LABEL).forEach((c) => {
      [0, 1].forEach((n) => {
        const o = AntUi.el('option', null, COND_LABEL[c][n]);
        o.value = c + '|' + n;
        sel.appendChild(o);
      });
    });
    sel.value = block.condition + '|' + (block.not ? 1 : 0);
    sel.addEventListener('change', () => {
      const [cond, n] = sel.value.split('|');
      block.condition = cond;
      block.not = n === '1';
    });
    return sel;
  }

  /** 回数の入力欄を作る(1〜100)。 */
  function countInput(block) {
    const input = AntUi.el('input');
    input.type = 'number';
    input.min = '1';
    input.max = '100';
    input.value = String(block.count);
    input.addEventListener('change', () => {
      const n = Math.min(100, Math.max(1, parseInt(input.value, 10) || 1)); // 1〜100の整数に収める
      block.count = n;
      input.value = String(n);
    });
    return input;
  }

  /** ブロックの行の、文字・選択欄・入力欄を作る。 */
  function buildLabel(b, line) {
    const text = (t) => line.appendChild(AntUi.el('span', null, t));
    switch (b.type) {
      case 'TURN': line.appendChild(dirSelect(b.dir, (v) => { b.dir = v; })); text('を向く'); break;
      case 'MOVE': text('前へ移動'); break;
      case 'JUMP': text('前へジャンプ'); break;
      case 'WAIT': text('待つ'); break;
      case 'MOVE_FUNCTION':
        text('移動関数(');
        line.appendChild(dirSelect(b.dir, (v) => { b.dir = v; }));
        line.appendChild(countInput(b));
        text('回)');
        break;
      case 'IF': text('もし'); line.appendChild(condSelect(b)); text('なら'); break;
      case 'WHILE': line.appendChild(condSelect(b)); text('の間'); break;
      case 'REPEAT': line.appendChild(countInput(b)); text('回くり返す'); break;
      default: break;
    }
  }

  /** ブロックの配列を、入れ子の <ul> として作る。 */
  function buildList(arr) {
    const ul = AntUi.el('ul');
    arr.forEach((b) => ul.appendChild(buildBlock(b, arr)));
    return ul;
  }

  /** ブロック1つ(と、制御ブロックなら、中の枠)を作る。 */
  function buildBlock(b, arr) {
    const li = AntUi.el('li');
    const line = AntUi.el('div', 'block-line ' + BLOCK_CLASS[b.type]);
    line.draggable = true;
    line.appendChild(AntUi.el('span', 'ico', BLOCK_ICON[b.type]));
    buildLabel(b, line);

    const del = AntUi.el('button', 'del', '×');
    del.type = 'button';
    del.title = 'このブロックを消す';
    del.addEventListener('click', (e) => { e.stopPropagation(); if (!playing) { removeBlock(b.id); } });
    line.appendChild(del);
    blockEls.set(b.id, line);

    // ドラッグして、並べ替え・別の枠へ移動できる
    line.addEventListener('dragstart', (e) => {
      if (playing || e.target !== line) { e.preventDefault(); return; }
      e.dataTransfer.setData('text/plain', 'move:' + b.id);
      e.dataTransfer.effectAllowed = 'move';
      line.classList.add('dragging');
    });
    line.addEventListener('dragend', () => line.classList.remove('dragging'));
    // このブロックの上にドロップすると、このブロックの前に入る
    enableDrop(line, arr, () => arr.indexOf(b));
    li.appendChild(line);

    if (b.type === 'IF') {
      li.appendChild(buildSlot('なら', b.then));
      li.appendChild(buildSlot('そうでなければ', b.else));
    } else if (b.type === 'WHILE' || b.type === 'REPEAT') {
      li.appendChild(buildSlot('中身', b.body));
    }
    return li;
  }

  /** 制御ブロックの「枠」(なら／そうでなければ／中身)を作る。見出しのクリックで、追加先に選べる。 */
  function buildSlot(title, arr) {
    const slot = AntUi.el('div', 'slot');
    const head = AntUi.el('button', 'slot-head' + (arr === target ? ' selected' : ''), '▼ ' + title + '(ここに追加)');
    head.type = 'button';
    head.addEventListener('click', () => selectTarget(arr));
    slot.appendChild(head);
    slot.appendChild(buildList(arr));
    if (arr.length === 0) { slot.appendChild(AntUi.el('div', 'empty-hint', 'ここにブロックを入れる')); }
    enableDrop(slot, arr, null);
    return slot;
  }

  /** プログラム全体を作り直して表示する。 */
  function renderProgram() {
    blockEls = new Map();
    programEl.textContent = '';
    const head = AntUi.el('button', 'slot-head' + (program === target ? ' selected' : ''), '▼ プログラムの最後(ここに追加)');
    head.type = 'button';
    head.addEventListener('click', () => selectTarget(program));
    programEl.appendChild(head);
    programEl.appendChild(buildList(program));
    if (program.length === 0) {
      programEl.appendChild(AntUi.el('div', 'empty-hint', 'ブロックをここにドラッグしてね'));
    }
  }

  /** ブロックの追加先(枠)を切り替える。 */
  function selectTarget(arr) {
    if (playing) { return; }
    target = arr;
    renderProgram();
  }

  /** パレット(使えるブロックの一覧)を作る。ドラッグまたはクリックで、プログラムに加えられる。 */
  function buildPalette() {
    const palette = $('palette');
    PALETTE.forEach((group) => {
      palette.appendChild(AntUi.el('div', 'group-title', group.group));
      const list = AntUi.el('div', 'block-list');
      group.items.forEach(([type, label]) => {
        const item = AntUi.el('div', 'block-line ' + BLOCK_CLASS[type]);
        item.draggable = true;
        item.appendChild(AntUi.el('span', 'ico', BLOCK_ICON[type]));
        item.appendChild(AntUi.el('span', null, label));
        item.addEventListener('dragstart', (e) => {
          if (playing) { e.preventDefault(); return; }
          e.dataTransfer.setData('text/plain', 'new:' + type);
          e.dataTransfer.effectAllowed = 'copy';
        });
        item.addEventListener('click', () => addToTarget(type));
        list.appendChild(item);
      });
      palette.appendChild(list);
    });
  }

  /** 実行中のハイライト: 実行中のブロックと、直前に調べた条件の結果(成り立てば緑・成り立たなければ赤)を強調する。 */
  function highlight(activeId, conditions) {
    blockEls.forEach((el) => el.classList.remove('active', 'cond-true', 'cond-false'));
    (conditions || []).forEach((c) => {
      const el = blockEls.get(c.blockId);
      if (el) { el.classList.add(c.value ? 'cond-true' : 'cond-false'); }
    });
    const active = blockEls.get(activeId);
    if (active) { active.classList.add('active'); }
  }

  // ======================================================================
  // 実行と再生
  // ======================================================================

  /** 1ステップぶんの記録(トレース)を、画面に反映する。 */
  function playStep(t) {
    highlight(t.blockId, t.conditions);
    applyEvents(t.events);
    inventory = t.inventory;

    // アリ
    placeUnit(antEl, t.x, t.y);
    setFacing(t.facing);

    // 敵: 生き残っている敵を動かし、いなくなった敵(討伐された敵)は消す
    const alive = new Set();
    t.enemies.forEach((e) => {
      alive.add(e.id);
      const el = enemyEls.get(e.id);
      if (el) { placeUnit(el, e.x, e.y); el.classList.remove('gone'); }
    });
    enemyEls.forEach((el, id) => { if (!alive.has(id)) { el.classList.add('gone'); } });

    if (t.result === 'DEFEATED_BY_ENEMY') {
      antEl.classList.add('hit');
    }
    drawInventory();
    if (AntApi.RESULT_NOTE[t.result]) { showMessage(AntApi.RESULT_NOTE[t.result]); }
  }

  /** 「実行」: プログラムをサーバーへ送り、返ってきた記録を再生して、結果の画面へ進む。 */
  async function onRun() {
    if (playing || !stage) { return; }
    if (program.length === 0) {
      showMessage('ブロックを置いてから、実行してね。', 'ng');
      return;
    }
    playing = true;
    runButton.disabled = true;
    const token = ++runToken;
    resetBoard();
    renderProgram();
    showMessage('実行中…');

    const res = await AntApi.run(stage.id, program);
    if (token !== runToken) { return; } // 「やり直す」が押された
    if (!res.ok) {
      showMessage(AntApi.errorText(res.error), 'ng');
      playing = false;
      runButton.disabled = false;
      return;
    }

    const data = res.data;
    for (const t of data.trace) {
      playStep(t);
      await AntUi.sleep(STEP_MS);
      if (token !== runToken) { return; }
    }
    highlight(null, []);
    showMessage(data.status === 'CLEARED' ? 'ゴール！' : '…', data.status === 'CLEARED' ? 'ok' : 'ng');
    await AntUi.sleep(800);
    if (token !== runToken) { return; }

    // 結果の画面へ移動する(クリア／失敗)
    if (data.status === 'CLEARED') {
      location.href = 'result-clear.html?stage=' + stage.id + '&steps=' + data.stepCount;
    } else {
      location.href = 'result-fail.html?stage=' + stage.id + '&reason=' + encodeURIComponent(data.failReason);
    }
  }

  /** 「やり直す」: 再生を止めて、ステージの最初の状態に戻す(プログラムはそのまま残す)。 */
  function onReset() {
    if (!stage) { return; }
    runToken++;
    playing = false;
    runButton.disabled = false;
    resetBoard();
    renderProgram();
    showMessage('最初の状態に戻したよ。');
  }

  // ======================================================================
  // 起動
  // ======================================================================

  async function start() {
    AntAssets.applyBackground($('screen'), 'game', 'linear-gradient(180deg,#74d3ff 0 31%,#bfe9a8 31% 100%)');
    buildGrid();
    buildPalette();
    renderProgram();
    fitMap();
    new ResizeObserver(fitMap).observe(mapWrap);

    runButton.addEventListener('click', onRun);
    $('resetButton').addEventListener('click', onReset);

    const res = await AntApi.getStage(stageId);
    if (!res.ok) {
      $('missionTitle').textContent = 'ステージを読み込めませんでした';
      showMessage(AntApi.errorText(res.error), 'ng');
      runButton.disabled = true;
      return;
    }
    stage = res.data;
    drawMission();
    resetBoard();
    fitMap();
  }

  start();
})();
