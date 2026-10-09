'use strict';

/*
 * 動作確認用の簡易画面のスクリプトです。
 *
 * 【XSS 対策のポイント】
 *   画面に文字を表示するときは、必ず textContent を使います。
 *   innerHTML は、文字列を HTML として解釈してしまい、
 *   <script> などを埋め込まれる危険があるため使いません。
 */

// 方向・種類の表示名(画面表示用)
const DIR_LABEL = { UP: '↑上', DOWN: '↓下', LEFT: '←左', RIGHT: '→右' };
const TYPE_LABEL = { MOVE: '移動', JUMP: 'ジャンプ' };

// サーバーが返すコードに対応する日本語メッセージ
const FAIL_MESSAGE = {
  FELL_INTO_PIT: '落とし穴に落ちました…',
  NOT_REACHED_GOAL: 'ゴールに着けませんでした。',
  STEP_LIMIT_EXCEEDED: '実行回数の上限(100回)を超えたので止めました。'
};
const ERROR_MESSAGE = {
  STAGE_NOT_FOUND: 'ステージが見つかりません。'
};

// 画面の部品
const gridEl = document.getElementById('grid');
const stageSelect = document.getElementById('stageSelect');
const stageNameEl = document.getElementById('stageName');
const paletteEl = document.getElementById('palette');
const programListEl = document.getElementById('programList');
const messageEl = document.getElementById('message');
const runButton = document.getElementById('runButton');
const clearButton = document.getElementById('clearButton');

// 状態
let stage = null;          // 読み込んだステージ情報
let program = [];          // ブロックの配列 [{id, type, dir}]
let nextBlockNo = 1;       // ブロックIDの連番
let playing = false;       // 再生中は操作を無効にする

/** メッセージを表示する。kind は 'ok' / 'ng' / ''(通常) */
function showMessage(text, kind) {
  messageEl.textContent = text;
  messageEl.className = kind || '';
}

/** マップを描く。antPos があればその位置にアリを表示する。 */
function drawGrid(antPos) {
  gridEl.textContent = ''; // 中身を空にする
  stage.rows.forEach((row, y) => {
    for (let x = 0; x < row.length; x++) {
      const ch = row[x];
      const cell = document.createElement('div');
      cell.className = 'cell';
      if (ch === '#') { cell.classList.add('wall'); }
      if (ch === 'P') { cell.classList.add('pit'); cell.textContent = '🕳'; }
      if (ch === 'G') { cell.classList.add('goal'); cell.textContent = '🏁'; }
      if (antPos && antPos.x === x && antPos.y === y) {
        cell.textContent = '🐜';
      }
      gridEl.appendChild(cell);
    }
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
  renderProgram();
  drawGrid(data.start);
  showMessage('', '');
}

/** プログラム(ブロックの並び)を画面に表示する。activeId のブロックを強調する。 */
function renderProgram(activeId) {
  programListEl.textContent = '';
  program.forEach((b) => {
    const li = document.createElement('li');
    li.textContent = TYPE_LABEL[b.type] + ' ' + DIR_LABEL[b.dir];
    if (b.id === activeId) { li.classList.add('active'); }
    programListEl.appendChild(li);
  });
}

/** ブロック追加ボタンを作る。 */
function buildPalette() {
  ['MOVE', 'JUMP'].forEach((type) => {
    ['UP', 'DOWN', 'LEFT', 'RIGHT'].forEach((dir) => {
      const btn = document.createElement('button');
      btn.type = 'button';
      btn.textContent = TYPE_LABEL[type] + ' ' + DIR_LABEL[dir];
      btn.addEventListener('click', () => {
        if (playing) { return; }
        program.push({ id: 'b' + nextBlockNo++, type: type, dir: dir });
        renderProgram();
      });
      paletteEl.appendChild(btn);
    });
  });
}

/** 指定ミリ秒待つ。 */
function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

/** 実行ボタン: サーバーにプログラムを送り、返ってきたトレースを順に再生する。 */
async function runProgram() {
  if (playing || !stage) { return; }
  playing = true;
  showMessage('実行中…', '');
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
    // トレースを1ステップずつ再生(ブロックのハイライトとアリの移動)
    for (const t of data.trace) {
      renderProgram(t.blockId);
      drawGrid({ x: t.x, y: t.y });
      await sleep(400);
    }
    renderProgram();
    if (data.status === 'CLEARED') {
      showMessage('クリア！ 実行回数: ' + data.stepCount + ' 回', 'ok');
    } else {
      showMessage(FAIL_MESSAGE[data.failReason] || '失敗しました。', 'ng');
      await sleep(600);
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
  renderProgram();
});

buildPalette();
loadStage(stageSelect.value);
