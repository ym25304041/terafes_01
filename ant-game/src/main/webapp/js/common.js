'use strict';

/*
 * 全ての画面で共通して使う、小さな補助関数です。
 *
 * 【XSS 対策のポイント】
 *   画面に文字を表示するときは、必ず textContent を使います（el() の text 引数も textContent です）。
 *   innerHTML は、文字列を HTML として解釈してしまい、<script> などを埋め込まれる危険があるため使いません。
 *   URL のパラメータ（?stage=3 など）も外部から届く値なので、必ず intParam() / listParam() で検査してから使います。
 */
const AntUi = (() => {

  /**
   * 要素を作ります。
   * @param tag       タグ名（'div' など）
   * @param className クラス名（なければ省略）
   * @param text      表示する文字（textContent で入れるので、HTML としては解釈されない）
   */
  function el(tag, className, text) {
    const e = document.createElement(tag);
    if (className) { e.className = className; }
    if (text !== undefined && text !== null) { e.textContent = text; }
    return e;
  }

  /**
   * URL のパラメータを整数として読み取ります。整数でない・範囲外のときは fallback を返します。
   * 例: ?stage=3 → intParam('stage', 1, 9999, 1) = 3
   */
  function intParam(name, min, max, fallback) {
    const raw = new URLSearchParams(location.search).get(name);
    if (raw === null || !/^\d{1,6}$/.test(raw)) { return fallback; }
    const n = parseInt(raw, 10);
    return (n >= min && n <= max) ? n : fallback;
  }

  /**
   * URL のパラメータを、許可した値の一覧(ホワイトリスト)の中のものだけ受け付けます。
   * 一覧にない値のときは fallback を返します。
   */
  function listParam(name, allowed, fallback) {
    const raw = new URLSearchParams(location.search).get(name);
    return allowed.includes(raw) ? raw : fallback;
  }

  /** 指定ミリ秒待ちます。 */
  function sleep(ms) {
    return new Promise((resolve) => setTimeout(resolve, ms));
  }

  return { el, intParam, listParam, sleep };
})();

/*
 * 失敗したときの「やり直し用のプログラム」を、一時的に預かっておく仕組みです。
 *
 * 【なぜ必要か】
 *   ゲーム画面で失敗すると、結果画面(失敗)という「別のページ」へ移動します。
 *   ページを移動すると、画面の中のプログラムは消えてしまうため、失敗した時点のプログラムを、
 *   結果画面で「もう一度挑戦する」が押されるまで預かっておき、ゲーム画面で復元します。
 *
 * 【預かる場所と、捨てるタイミング】
 *   ・sessionStorage（ブラウザのタブの中だけに残る保存場所）を使います。タブを閉じると消えます。
 *     localStorage のような、ずっと残る保存は使いません。サーバーにも送りません。
 *   ・ゲーム画面が開いたときに、預かっているプログラムを取り出して、必ず捨てます（take）。
 *     「もう一度挑戦する」から来たときだけ、取り出したものを復元に使います。
 *   ・タイトル・ステージ選択・クリア結果の画面が開いたときも、捨てます（discard）。
 *   ・ブラウザの設定で保存が使えない場合は、何もしません（復元できないだけで、ゲームは動きます）。
 */
const AntRetry = (() => {

  const KEY = 'ant.retryProgram';

  /** 失敗したステージのプログラムを預かる。 */
  function save(stageId, program) {
    try {
      sessionStorage.setItem(KEY, JSON.stringify({ stageId: stageId, program: program }));
    } catch (e) {
      // 保存できない環境では、何もしない
    }
  }

  /**
   * 預かっているプログラムを取り出して、捨てる(次に取り出すと、何もない)。
   * @returns {{stageId:number, program:Array}|null} なければ null
   */
  function take() {
    try {
      const raw = sessionStorage.getItem(KEY);
      sessionStorage.removeItem(KEY);
      if (raw === null) { return null; }
      const data = JSON.parse(raw);
      if (data && Number.isInteger(data.stageId) && Array.isArray(data.program)) { return data; }
    } catch (e) {
      // 読み取れないときは、何もないものとして扱う
    }
    return null;
  }

  /** 預かっているプログラムを捨てる。 */
  function discard() {
    try {
      sessionStorage.removeItem(KEY);
    } catch (e) {
      // 何もしない
    }
  }

  return { save, take, discard };
})();
