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
 *     アイテムを捨てる : {id, type:'DROP'}   (持っているアイテムを、今いるマスに置く)
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
  const TEST_RESET_MS = 3000; // テスト実行が終わってから、マップを最初の状態に戻すまでの時間(ミリ秒)

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
    { group: 'コマンド', items: [['TURN', '向く'], ['MOVE', '前へ移動'], ['JUMP', '前へジャンプ'], ['WAIT', '待つ'], ['DROP', 'アイテムを捨てる']] },
    { group: '関数', items: [['MOVE_FUNCTION', '移動関数']] },
    { group: '制御', items: [['IF', 'もし…なら'], ['WHILE', '…の間'], ['REPEAT', '回くり返す']] }
  ];
  const BLOCK_CLASS = {
    TURN: 'b-turn', MOVE: 'b-move', JUMP: 'b-jump', WAIT: 'b-wait', DROP: 'b-drop',
    MOVE_FUNCTION: 'b-func', IF: 'b-if', WHILE: 'b-while', REPEAT: 'b-repeat'
  };
  const BLOCK_ICON = {
    TURN: '⟳', MOVE: '➡', JUMP: '⬆', WAIT: '⏳', DROP: '⤓', MOVE_FUNCTION: '🏃', IF: '？', WHILE: '🔁', REPEAT: '↻'
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
  const heldSlot = $('heldSlot');
  const clearButton = $('clearButton');
  const runButton = $('runButton');
  const testButton = $('testButton');

  // 状態
  let stage = null;          // 読み込んだステージ情報
  let view = [];             // 表示用のマップ(2次元配列の記号)。アイテム取得・ドア開放などで書き換える
  let letters = [];          // スイッチ・ドアの記号(a〜e)。バッジの表示に使う
  let cellEls = [];          // マスの要素(cellEls[y][x])
  let antEl = null;          // アリロボットの要素
  let enemyEls = new Map();  // 敵のID → 敵の要素
  let held = null;           // 持っているアイテム(1つだけ。'SUPPLY' / 'WEAPON' / なければ null)
  let floorItems = [];       // 床に置かれているアイテム(floorItems[y][x] = 'supply' / 'weapon' / null)
  let facing = 'RIGHT';      // 画面に表示するアリの向き
  let program = [];          // プログラム(ブロックの配列。入れ子)
  let target = program;      // 次にブロックを追加する先の配列(選んだ枠)
  let nextBlockNo = 1;       // ブロックIDの連番
  let blockEls = new Map();  // ブロックID → 画面上のブロックの行(ハイライト用)
  let playing = false;       // 再生中か
  let runToken = 0;          // 実行の番号。新しい実行が始まったとき、古い再生を打ち切るために使う

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
    // 床のアイテム(最初から置いてあるものと、捨てたもの)
    if (floorItems[y][x]) { cell.appendChild(AntAssets.item(floorItems[y][x])); }
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
    view = stage.rows.map((row) => row.replace(/[XMW]/g, '.').split(''));
    // アイテムは、地形とは別に、床のアイテムとして覚える(M=物資、W=武器)
    floorItems = stage.rows.map((row) => row.split('').map((ch) => (ch === 'M' ? 'supply' : ch === 'W' ? 'weapon' : null)));
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

    held = null;
    drawHeld();
  }

  /** もちもの枠の表示を更新する。持てるアイテムは1つだけ。持っていなければ「なし」と表示する。 */
  function drawHeld() {
    heldSlot.textContent = '';
    heldSlot.classList.toggle('have', held !== null);
    if (held === null) {
      heldSlot.title = 'なし';
      heldSlot.appendChild(AntUi.el('span', null, 'なし'));
      return;
    }
    heldSlot.appendChild(AntAssets.item(held.toLowerCase()));
    heldSlot.title = held === 'SUPPLY' ? '物資' : '武器'; // 画像にマウスを重ねると名前が出る
  }

  /** 自動で起きた出来事(アイテム取得・スイッチ作動・ドアが開く)を、マップの表示に反映する。 */
  function applyEvents(events) {
    events.forEach((e) => {
      if (e.type === 'PICKUP') { floorItems[e.y][e.x] = null; updateCell(e.x, e.y); }
      if (e.type === 'ITEM_DROPPED') { floorItems[e.y][e.x] = e.item.toLowerCase(); updateCell(e.x, e.y); }
      if (e.type === 'SWITCH_ON') { view[e.y][e.x] = '!'; updateCell(e.x, e.y); }
      if (e.type === 'DOOR_OPEN') { view[e.y][e.x] = '_'; updateCell(e.x, e.y); }
      // 敵の討伐は、敵の位置の一覧から、倒された敵がいなくなることで表現される
    });
  }

  /** ミッションの表示(ステージ番号・名前・説明・クリア条件)。 */
  function drawMission() {
    $('stageNo').textContent = String(stage.id);
    $('missionTitle').textContent = stage.name;
    $('missionDesc').textContent = stage.description || 'アリロボットを動かして、ゴールを目指そう。';
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
      case 'DROP': text('アイテムを捨てる'); break;
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
    // プログラムの最後へドロップするための領域(ブロックの下の空き全体)。ここにドロップすると、最後に入る
    const endZone = AntUi.el('div', 'end-zone',
      program.length === 0 ? 'ブロックをここにドラッグしてね' : 'ここにドロップすると、最後に入る');
    enableDrop(endZone, program, null);
    programEl.appendChild(endZone);
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
    held = t.heldItem;

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
    drawHeld();
    if (AntApi.RESULT_NOTE[t.result]) { showMessage(AntApi.RESULT_NOTE[t.result]); }
  }

  /**
   * プログラムをサーバーへ送り、返ってきた記録を再生する。
   *
   *   「実行」      : 再生が終わったら、結果の画面(クリア／失敗)へ移動する。
   *   「テスト実行」: 動きを記録して再生するだけ。ゴールの判定(クリア・失敗の判定)はしない。
   *                  再生が終わったら、3秒待って、マップを最初の状態に戻す。
   *
   * @param isTest テスト実行か
   */
  async function runProgram(isTest) {
    if (playing || !stage) { return; }
    if (program.length === 0) {
      showMessage('ブロックを置いてから、実行してね。', 'ng');
      return;
    }
    playing = true;
    setRunButtons(false);
    const token = ++runToken;
    resetBoard();
    renderProgram();
    showMessage(isTest ? 'テスト実行中…' : '実行中…');

    const res = await AntApi.run(stage.id, program, isTest);
    if (token !== runToken) { return; } // 別の実行が始まった(古い再生は打ち切る)
    if (!res.ok) {
      showMessage(AntApi.errorText(res.error), 'ng');
      playing = false;
      setRunButtons(true);
      return;
    }

    const data = res.data;
    for (const t of data.trace) {
      playStep(t);
      await AntUi.sleep(STEP_MS);
      if (token !== runToken) { return; }
    }
    highlight(null, []);

    if (isTest) {
      // テスト実行: 結果の画面へは進まない。落下・敗北・上限超過で止まったときは、その理由を伝える
      const reason = data.status === 'FAILED' ? (AntApi.FAIL_MESSAGE[data.failReason] || '') + ' ' : '';
      showMessage('テスト実行が終わったよ。' + reason + '(3秒後に最初の状態に戻るよ)');
      await AntUi.sleep(TEST_RESET_MS);
      if (token !== runToken) { return; }
      resetBoard();
      renderProgram();
      showMessage('最初の状態に戻したよ。');
      playing = false;
      setRunButtons(true);
      return;
    }

    showMessage(data.status === 'CLEARED' ? 'ゴール！' : '…', data.status === 'CLEARED' ? 'ok' : 'ng');
    await AntUi.sleep(800);
    if (token !== runToken) { return; }

    // 結果の画面へ移動する(クリア／失敗)
    if (data.status === 'CLEARED') {
      location.href = 'result-clear.html?stage=' + stage.id + '&steps=' + data.stepCount;
    } else {
      // 失敗: 「もう一度挑戦する」で再開できるように、今のプログラムを預かってから移動する
      AntRetry.save(stage.id, program);
      location.href = 'result-fail.html?stage=' + stage.id + '&reason=' + encodeURIComponent(data.failReason);
    }
  }

  /** 「実行」「テスト実行」ボタンを、押せる／押せないに切り替える。 */
  function setRunButtons(enabled) {
    runButton.disabled = !enabled;
    testButton.disabled = !enabled;
  }

  /** 「クリア」: プログラムのブロックを、すべて消す(再生中は押せない)。 */
  function onClear() {
    if (playing) { return; }
    program = [];
    target = program;
    renderProgram();
    showMessage('プログラムを空にしたよ。');
  }

  // ======================================================================
  // 起動
  // ======================================================================

  /**
   * 預かっていたプログラム(外部から読み込んだデータ)を、安全な形に作り直す。
   * 知らない種類のブロックは捨て、方向・条件・回数は、許可した値だけを受け付ける。
   * ブロックのIDは新しく振り直す。入れ子は6段まで、全部で200個までに収める。
   */
  function sanitizeProgram(list, depth, counter) {
    const has = (obj, key) => Object.prototype.hasOwnProperty.call(obj, key);
    if (!Array.isArray(list) || depth > 6) { return []; }
    const out = [];
    for (const b of list) {
      if (counter.n >= 200) { break; }
      if (!b || typeof b !== 'object' || typeof b.type !== 'string' || !has(BLOCK_CLASS, b.type)) { continue; }
      counter.n++;
      const nb = newBlock(b.type);
      if (typeof b.dir === 'string' && has(DIR_LABEL, b.dir) && 'dir' in nb) { nb.dir = b.dir; }
      if (Number.isInteger(b.count) && 'count' in nb) { nb.count = Math.min(100, Math.max(1, b.count)); }
      if (typeof b.condition === 'string' && has(COND_LABEL, b.condition) && 'condition' in nb) {
        nb.condition = b.condition;
        nb.not = b.not === true;
      }
      ['then', 'else', 'body'].forEach((key) => {
        if (Array.isArray(nb[key])) { nb[key] = sanitizeProgram(b[key], depth + 1, counter); }
      });
      out.push(nb);
    }
    return out;
  }

  async function start() {
    // 失敗のあとに預かっていたプログラムを取り出す(取り出すと同時に、預かりは必ず捨てられる)。
    // 「もう一度挑戦する」から来たとき(?retry=1)だけ、あとで復元に使う
    const saved = AntRetry.take();
    const wantsRetry = AntUi.intParam('retry', 0, 1, 0) === 1;
    AntAssets.applyBackground($('screen'), 'game', 'linear-gradient(180deg,#74d3ff 0 31%,#bfe9a8 31% 100%)');
    buildGrid();
    buildPalette();
    renderProgram();
    fitMap();
    new ResizeObserver(fitMap).observe(mapWrap);

    runButton.addEventListener('click', () => runProgram(false));
    testButton.addEventListener('click', () => runProgram(true));
    clearButton.addEventListener('click', onClear);

    const res = await AntApi.getStage(stageId);
    if (!res.ok) {
      $('missionTitle').textContent = 'ステージを読み込めませんでした';
      showMessage(AntApi.errorText(res.error), 'ng');
      setRunButtons(false);
      return;
    }
    stage = res.data;
    drawMission();
    resetBoard();
    fitMap();

    // 失敗した同じステージの、もう一度挑戦するなら、前回のプログラムを復元する
    if (wantsRetry && saved && saved.stageId === stage.id) {
      program = sanitizeProgram(saved.program, 0, { n: 0 });
      target = program;
      renderProgram();
      showMessage('前回のプログラムをそのまま残したよ。直して、もう一度挑戦しよう!');
    }
  }

  start();
})();
