'use strict';

/*
 * 動作確認用の簡易画面のスクリプトです。
 *
 * 【XSS 対策のポイント】
 *   画面に文字を表示するときは、必ず textContent を使います。
 *   innerHTML は、文字列を HTML として解釈してしまい、
 *   <script> などを埋め込まれる危険があるため使いません。
 *
 * 【プログラムの持ち方】
 *   プログラムは「ブロックの配列」で、制御ブロックは中に配列を持ちます(入れ子)。
 *   この形は、そのままサーバー(api/run)へ送る JSON になります。
 *     行動ブロック : {id, type: 'MOVE'|'JUMP', dir} / {id, type: 'WAIT'}
 *     もし         : {id, type: 'IF', condition, not, then: [...], else: [...]}
 *     の間         : {id, type: 'WHILE', condition, not, body: [...]}
 *     回くり返す   : {id, type: 'REPEAT', count, body: [...]}
 */

// 方向・種類・条件の表示名(画面表示用)
const DIR_LABEL = { UP: '↑上', DOWN: '↓下', LEFT: '←左', RIGHT: '→右' };
const TYPE_LABEL = { MOVE: '移動', JUMP: 'ジャンプ', WAIT: '待つ' };
const COND_LABEL = {
  WALL_AHEAD: '目の前に壁がある',
  ENEMY_AHEAD: '目の前に敵がいる',
  PIT_AHEAD: '目の前に落とし穴がある',
  ENEMY_COMING: '目の前に敵が来る'
};

// サーバーが返すコードに対応する日本語メッセージ
const FAIL_MESSAGE = {
  FELL_INTO_PIT: '落とし穴に落ちました…',
  DEFEATED_BY_ENEMY: '武器を持たずに敵と戦い、敗北しました…',
  NOT_REACHED_GOAL: 'ゴールに着けませんでした。',
  STEP_LIMIT_EXCEEDED: '実行回数の上限(100回)を超えたので止めました。',
  LOOP_LIMIT_EXCEEDED: '処理回数の上限(1000回)を超えたので止めました(中身が空のくり返しなど)。'
};
// 実行結果のうち、画面にメッセージを出したいもの
const RESULT_NOTE = {
  BLOCKED_DOOR: 'ドアが閉じています',
  BLOCKED_WALL: '壁があります',
  JUMP_FAILED_NO_PIT: 'ジャンプできません(前が穴ではありません)',
  JUMP_FAILED_BLOCKED: 'ジャンプできません(着地点に進めません)'
};
const ERROR_MESSAGE = {
  STAGE_NOT_FOUND: 'ステージが見つかりません。',
  PROGRAM_TOO_DEEP: 'ブロックの入れ子が深すぎます(6段まで)。',
  PROGRAM_TOO_LONG: 'ブロックが多すぎます(200個まで)。'
};

// 画面の部品
const gridEl = document.getElementById('grid');
const stageSelect = document.getElementById('stageSelect');
const stageNameEl = document.getElementById('stageName');
const paletteEl = document.getElementById('palette');
const programEl = document.getElementById('program');
const messageEl = document.getElementById('message');
const runButton = document.getElementById('runButton');
const clearButton = document.getElementById('clearButton');
const undoButton = document.getElementById('undoButton');
const inventoryEl = document.getElementById('inventory');
const condSelect = document.getElementById('condSelect');
const notCheck = document.getElementById('notCheck');
const repeatCount = document.getElementById('repeatCount');

// 状態
let stage = null;          // 読み込んだステージ情報
let view = [];             // 表示用のマップ(2次元配列)。アイテム取得やドア開放で書き換える
let enemies = [];          // 今、画面に出す敵の位置 [{id, x, y}]
let inventory = { supply: false, weapon: false }; // 画面に表示する持ち物
let program = [];          // ブロックの配列(入れ子)
let target = program;      // 次にブロックを追加する先の配列(選んだ枠)
let nextBlockNo = 1;       // ブロックIDの連番
let playing = false;       // 再生中は操作を無効にする
let blockEls = new Map();  // ブロックID → 画面上の行(ハイライト用)

/** メッセージを表示する。kind は 'ok' / 'ng' / ''(通常) */
function showMessage(text, kind) {
  messageEl.textContent = text;
  messageEl.className = kind || '';
}

// ----------------------------------------------------------------------
// マップの表示
// ----------------------------------------------------------------------

/**
 * 表示用マップ(view)の記号を、画面に出す絵文字に変換する。
 *   M=物資 W=武器 a〜e=スイッチ A〜E=閉じたドア
 *   '!'=作動済みスイッチ '_'=開いたドア (この2つは画面専用の記号)
 *   敵は view ではなく、enemies の位置から別に描く。
 */
function symbolOf(ch) {
  if (ch === 'P') { return '🕳'; }
  if (ch === 'G') { return '🏁'; }
  if (ch === 'M') { return '📦'; }
  if (ch === 'W') { return '⚔'; }
  if (ch >= 'a' && ch <= 'e') { return '🔘' + ch; }
  if (ch >= 'A' && ch <= 'E') { return '🚪' + ch; }
  if (ch === '!') { return '✅'; }
  if (ch === '_') { return '▫'; }
  return '';
}

/** 表示用マップと敵と持ち物を、ステージの初期状態に戻す。 */
function resetView() {
  // 動かない敵 'X' は道として扱い、敵は enemies から描く
  view = stage.rows.map((row) => row.replace(/X/g, '.').split(''));
  enemies = stage.enemies.map((e) => ({ id: e.id, x: e.x, y: e.y }));
  inventory = { supply: false, weapon: false };
}

/** マップを描く。antPos があればその位置にアリを表示する。 */
function drawGrid(antPos) {
  gridEl.textContent = ''; // 中身を空にする
  view.forEach((row, y) => {
    row.forEach((ch, x) => {
      const cell = document.createElement('div');
      cell.className = 'cell';
      if (ch === '#') { cell.classList.add('wall'); }
      if (ch === 'P') { cell.classList.add('pit'); }
      if (ch === 'G') { cell.classList.add('goal'); }
      if (ch >= '1' && ch <= '5') { cell.classList.add('zone'); } // 接敵ゾーン
      if ((ch >= 'A' && ch <= 'E') || ch === '_') { cell.classList.add('door'); }
      cell.textContent = symbolOf(ch);

      const hasEnemy = enemies.some((e) => e.x === x && e.y === y);
      const hasAnt = antPos && antPos.x === x && antPos.y === y;
      if (hasEnemy) {
        cell.classList.add('enemy');
        cell.textContent = '🐍';
      }
      if (hasAnt) {
        cell.textContent = hasEnemy ? '💥' : '🐜'; // 同じマスで戦闘になった
      }
      gridEl.appendChild(cell);
    });
  });
  inventoryEl.textContent = '持ち物: 物資 ' + (inventory.supply ? '📦あり' : 'なし')
    + ' / 武器 ' + (inventory.weapon ? '⚔あり' : 'なし');
}

/** 自動で起きた出来事(取得・スイッチ作動・ドアが開く)を表示用マップに反映する。 */
function applyEvents(events) {
  events.forEach((e) => {
    if (e.type === 'PICKUP') { view[e.y][e.x] = '.'; }
    if (e.type === 'SWITCH_ON') { view[e.y][e.x] = '!'; }
    if (e.type === 'DOOR_OPEN') { view[e.y][e.x] = '_'; }
    // 敵の討伐は、敵の位置(enemies)が更新されて、討伐された敵が消えることで表現される
  });
}

/** サーバーからステージ情報を取得して表示する。 */
async function loadStage(id) {
  const res = await fetch('api/stage?id=' + encodeURIComponent(id));
  const data = await res.json();
  if (!res.ok) {
    showMessage(ERROR_MESSAGE[data.error] || 'エラーが発生しました。', 'ng');
    return;
  }
  stage = data;
  stageNameEl.textContent = data.name;
  program = [];
  target = program;
  renderProgram();
  resetView();
  drawGrid(data.start);
  showMessage('', '');
}

// ----------------------------------------------------------------------
// プログラム(ブロック)の編集と表示
// ----------------------------------------------------------------------

/** ブロック1つの見出し(1行ぶんの文字)を作る。 */
function labelOf(b) {
  if (b.type === 'MOVE' || b.type === 'JUMP') { return TYPE_LABEL[b.type] + ' ' + DIR_LABEL[b.dir]; }
  if (b.type === 'WAIT') { return TYPE_LABEL.WAIT; }
  const notText = b.not ? 'でない' : '';
  if (b.type === 'IF') { return 'もし [' + COND_LABEL[b.condition] + ']' + notText + ' なら'; }
  if (b.type === 'WHILE') { return '[' + COND_LABEL[b.condition] + ']' + notText + ' の間'; }
  return b.count + ' 回くり返す'; // REPEAT
}

/**
 * ブロックの配列を、入れ子の <ul> として作る。
 * @param blocks 表示するブロックの配列
 */
function buildList(blocks) {
  const ul = document.createElement('ul');
  blocks.forEach((b) => {
    const li = document.createElement('li');
    const line = document.createElement('div');
    line.className = 'block-line';
    line.textContent = labelOf(b);
    li.appendChild(line);
    blockEls.set(b.id, line);

    if (b.type === 'IF') {
      li.appendChild(buildSlot('なら', b.then));
      li.appendChild(buildSlot('そうでなければ', b.else));
    } else if (b.type === 'WHILE' || b.type === 'REPEAT') {
      li.appendChild(buildSlot('中身', b.body));
    }
    ul.appendChild(li);
  });
  return ul;
}

/**
 * 制御ブロックの「枠」(なら／そうでなければ／中身)を作る。
 * 見出しをクリックすると、そこが「ブロックの追加先」になる。
 */
function buildSlot(title, blocks) {
  const slot = document.createElement('div');
  slot.className = 'slot';
  const head = document.createElement('button');
  head.type = 'button';
  head.className = 'slot-head';
  head.textContent = '▼ ' + title + '（ここに追加）';
  if (blocks === target) { head.classList.add('selected'); }
  head.addEventListener('click', () => selectTarget(blocks));
  slot.appendChild(head);
  slot.appendChild(buildList(blocks));
  return slot;
}

/** プログラム全体を作り直して表示する。 */
function renderProgram() {
  blockEls = new Map();
  programEl.textContent = '';

  const rootHead = document.createElement('button');
  rootHead.type = 'button';
  rootHead.className = 'slot-head';
  rootHead.textContent = '▼ プログラムの最後（ここに追加）';
  if (program === target) { rootHead.classList.add('selected'); }
  rootHead.addEventListener('click', () => selectTarget(program));
  programEl.appendChild(rootHead);
  programEl.appendChild(buildList(program));
}

/** ブロックの追加先を切り替える。 */
function selectTarget(blocks) {
  if (playing) { return; }
  target = blocks;
  renderProgram();
}

/** 実行中のハイライト: 実行中の行動ブロックと、直前に調べた条件の結果を強調する。 */
function highlight(activeId, conditions) {
  blockEls.forEach((el) => el.classList.remove('active', 'cond-true', 'cond-false'));
  (conditions || []).forEach((c) => {
    const el = blockEls.get(c.blockId);
    if (el) { el.classList.add(c.value ? 'cond-true' : 'cond-false'); }
  });
  const active = blockEls.get(activeId);
  if (active) { active.classList.add('active'); }
}

/** ブロックを、今の追加先に加える。 */
function addBlock(block) {
  if (playing) { return; }
  block.id = 'b' + nextBlockNo++;
  target.push(block);
  renderProgram();
}

/** ブロック追加ボタンを作る。 */
function buildPalette() {
  const addButton = (text, makeBlock) => {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.textContent = text;
    btn.addEventListener('click', () => addBlock(makeBlock()));
    paletteEl.appendChild(btn);
  };

  ['MOVE', 'JUMP'].forEach((type) => {
    ['UP', 'DOWN', 'LEFT', 'RIGHT'].forEach((dir) => {
      addButton(TYPE_LABEL[type] + ' ' + DIR_LABEL[dir], () => ({ type: type, dir: dir }));
    });
  });
  addButton('待つ', () => ({ type: 'WAIT' }));

  // 制御ブロック: 条件の選択欄・「でない」・回数の入力を読み取って作る
  addButton('もし…なら', () => ({
    type: 'IF', condition: condSelect.value, not: notCheck.checked, then: [], else: []
  }));
  addButton('…の間', () => ({
    type: 'WHILE', condition: condSelect.value, not: notCheck.checked, body: []
  }));
  addButton('回くり返す', () => {
    // 回数は 1〜100 の整数に収める(サーバーも同じ範囲を検査する)
    const n = Math.min(100, Math.max(1, parseInt(repeatCount.value, 10) || 1));
    return { type: 'REPEAT', count: n, body: [] };
  });
}

// ----------------------------------------------------------------------
// 実行
// ----------------------------------------------------------------------

/** 指定ミリ秒待つ。 */
function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 実行ボタン: サーバーにプログラムを送り、返ってきたトレースを順に再生する。 */
async function runProgram() {
  if (playing || !stage) { return; }
  playing = true;
  showMessage('実行中…', '');
  resetView();
  drawGrid(stage.start);
  try {
    const res = await fetch('api/run', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ stageId: stage.id, program: program })
    });
    const data = await res.json();
    if (!res.ok) {
      showMessage(ERROR_MESSAGE[data.error] || 'リクエストが正しくありません(' + data.error + ')', 'ng');
      return;
    }
    // トレースを1ステップずつ再生(ブロックのハイライト・アリと敵の移動)
    for (const t of data.trace) {
      highlight(t.blockId, t.conditions);
      applyEvents(t.events);
      inventory = t.inventory;
      enemies = t.enemies;
      drawGrid({ x: t.x, y: t.y });
      if (RESULT_NOTE[t.result]) { showMessage(RESULT_NOTE[t.result], ''); }
      await sleep(400);
    }
    highlight(null, []);
    if (data.status === 'CLEARED') {
      showMessage('クリア！ 実行回数: ' + data.stepCount + ' 回', 'ok');
    } else {
      showMessage(FAIL_MESSAGE[data.failReason] || '失敗しました。', 'ng');
      await sleep(600);
      resetView();
      drawGrid(stage.start); // 失敗したら実行前の状態に戻す
    }
  } catch (e) {
    showMessage('通信に失敗しました。', 'ng');
  } finally {
    playing = false;
  }
}

// イベント登録(HTML内に onclick を書かず、ここで登録する → CSP に対応)
stageSelect.addEventListener('change', () => loadStage(stageSelect.value));
runButton.addEventListener('click', runProgram);
clearButton.addEventListener('click', () => {
  if (playing) { return; }
  program = [];
  target = program;
  renderProgram();
});
undoButton.addEventListener('click', () => {
  if (playing) { return; }
  target.pop(); // 選んでいる枠の、いちばん最後のブロックを取り除く
  renderProgram();
});

buildPalette();
loadStage(stageSelect.value);
