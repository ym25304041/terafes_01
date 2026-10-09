package com.terafes.ant.game;

/**
 * 移動・ジャンプの方向（上下左右）を表す列挙型（enum）です。
 *
 * 【座標のルール】
 *   ・左上が原点 (0, 0) です。
 *   ・x は右へ行くほど増え、y は「下へ行くほど」増えます。
 *     （数学のグラフと違い、y の向きが逆なので注意してください）
 *
 * 各方向が「x と y をどれだけ動かすか」を dx, dy として持たせることで、
 * 上下左右の4つの移動処理を1つにまとめられます（コードの重複を防げます）。
 */
public enum Direction {

    /** 上: y が 1 減る */
    UP(0, -1),
    /** 下: y が 1 増える */
    DOWN(0, 1),
    /** 左: x が 1 減る */
    LEFT(-1, 0),
    /** 右: x が 1 増える */
    RIGHT(1, 0);

    /** x方向の移動量 */
    private final int dx;
    /** y方向の移動量 */
    private final int dy;

    Direction(int dx, int dy) {
        this.dx = dx;
        this.dy = dy;
    }

    /**
     * 指定した位置から、この方向へ steps マス進んだ位置を返します。
     * 元の Position は変更されません（新しい Position を作って返します）。
     *
     * @param from  出発位置
     * @param steps 進むマス数（通常移動なら1、ジャンプの着地点なら2）
     * @return 移動後の位置（マップ外になる場合もあります。判定は GameMap 側で行います）
     */
    public Position step(Position from, int steps) {
        return new Position(from.x() + dx * steps, from.y() + dy * steps);
    }

    /**
     * 文字列から Direction を取り出します（外部入力用）。
     *
     * 外部から届いた文字列は信用せず、「UP / DOWN / LEFT / RIGHT のどれかに完全一致」
     * する場合だけ受け付けます（ホワイトリスト方式）。それ以外は例外にします。
     *
     * @param text 外部から受け取った文字列（null可）
     * @return 対応する Direction
     * @throws IllegalArgumentException 該当する方向がない場合
     */
    public static Direction parse(String text) {
        if (text != null) {
            for (Direction d : values()) {
                if (d.name().equals(text)) {
                    return d;
                }
            }
        }
        throw new IllegalArgumentException("不正な方向です");
    }
}
