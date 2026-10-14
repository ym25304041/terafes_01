package com.terafes.ant.program;

/**
 * 制御ブロックの条件を調べた結果の記録です。画面が、条件ブロックのハイライトに使います。
 *
 * @param blockId 条件を調べたブロック（IF / WHILE）のID
 * @param value   条件の結果（「〜でない」を反映した最終的な真偽）
 */
public record ConditionCheck(String blockId, boolean value) {
}
