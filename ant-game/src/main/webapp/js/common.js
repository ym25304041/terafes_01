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
