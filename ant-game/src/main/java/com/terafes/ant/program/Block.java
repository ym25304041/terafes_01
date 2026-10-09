package com.terafes.ant.program;

import com.terafes.ant.game.Direction;

/**
 * プログラムを構成するブロック1つぶんです。
 *
 * @param id   ブロックのID。実行結果に含めて返し、画面側のハイライトに使います
 * @param type ブロックの種類（移動／ジャンプ）
 * @param dir  方向（上下左右）
 */
public record Block(String id, BlockType type, Direction dir) {
}
