package com.terafes.ant.program;

import com.terafes.ant.game.ConditionType;
import com.terafes.ant.game.Direction;

import java.util.List;

/**
 * プログラムを構成するブロック1つぶんです。制御ブロックは、中にブロックを持てます（入れ子）。
 *
 * ブロックの種類によって、使う項目が違います（使わない項目は null / false / 0 / 空のリスト）。
 *   MOVE / JUMP : dir
 *   WAIT        : なし
 *   IF          : condition, negate, body（「なら」の側）, elseBody（「そうでなければ」の側）
 *   WHILE       : condition, negate, body
 *   REPEAT      : count, body
 *
 * @param id        ブロックのID。実行結果に含めて返し、画面側のハイライトに使います
 * @param type      ブロックの種類
 * @param dir       方向（MOVE / JUMP のとき）
 * @param condition 条件の種類（IF / WHILE のとき）
 * @param negate    true なら条件を反転する（「〜でない」）。IF / WHILE のとき
 * @param count     くり返す回数（REPEAT のとき）
 * @param body      中に入っているブロック
 * @param elseBody  IF の「そうでなければ」の側に入っているブロック
 */
public record Block(String id, BlockType type, Direction dir, ConditionType condition, boolean negate,
                    int count, List<Block> body, List<Block> elseBody) {

    /** 移動・ジャンプ・待つ のような、中身を持たないブロックを作る。dir は WAIT のとき null でよい。 */
    public Block(String id, BlockType type, Direction dir) {
        this(id, type, dir, null, false, 0, List.of(), List.of());
    }

    /** 「待つ」ブロックを作る。 */
    public static Block waitBlock(String id) {
        return new Block(id, BlockType.WAIT, null);
    }

    /** 「もし(条件)なら…そうでなければ…」ブロックを作る。 */
    public static Block ifBlock(String id, ConditionType condition, boolean negate,
                                List<Block> thenBody, List<Block> elseBody) {
        return new Block(id, BlockType.IF, null, condition, negate, 0, thenBody, elseBody);
    }

    /** 「(条件)の間…し続ける」ブロックを作る。 */
    public static Block whileBlock(String id, ConditionType condition, boolean negate, List<Block> body) {
        return new Block(id, BlockType.WHILE, null, condition, negate, 0, body, List.of());
    }

    /** 「(数値)回…実行する」ブロックを作る。 */
    public static Block repeatBlock(String id, int count, List<Block> body) {
        return new Block(id, BlockType.REPEAT, null, null, false, count, body, List.of());
    }
}
