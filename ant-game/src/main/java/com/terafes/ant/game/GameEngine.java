package com.terafes.ant.game;

/**
 * アリの移動・ジャンプとゲーム状態を管理するエンジンです。
 *
 * 【このクラスのルール】
 *   ・移動は1回につき1マス。移動先が「マップ外」「壁」ならその場から動きません。
 *   ・落とし穴へ通常移動すると、穴のマスへ移動した直後に落下 → 失敗（FAILED）になります。
 *   ・ジャンプは「前方1マスが落とし穴のときだけ」成功し、2マス先へ着地します。
 *       - 前方1マスが落とし穴でない → ジャンプ失敗（その場から動かない）
 *       - 着地点が壁／マップ外 → ジャンプ失敗（その場から動かない）
 *       - 着地点が落とし穴 → 落下して失敗
 *   ・ゴールに着いた時点でクリア（CLEARED）となり、以降の行動は受け付けません。
 *
 * 1回のゲームごとに new GameEngine(map) で作ります。
 * 状態（位置・向き）を持つため、複数のリクエストで使い回さないでください。
 * （Servlet は複数人が同時に使うので、インスタンスを共有すると他人の状態と混ざります）
 */
public final class GameEngine {

    /** 地形データ（変更しない） */
    private final GameMap map;
    /** アリの現在位置 */
    private Position position;
    /** アリの向き（直近に動こうとした方向）。後で「目の前」の条件判定に使います */
    private Direction facing;
    /** ゲームの状態 */
    private GameStatus status;

    /**
     * @param map ゲームで使うマップ。アリはマップのスタート地点から始まります
     */
    public GameEngine(GameMap map) {
        this.map = map;
        this.position = map.getStart();
        this.facing = Direction.RIGHT; // 初期の向きは右
        this.status = GameStatus.PLAYING;
    }

    // ------------------------------------------------------------------
    // 状態の取得
    // ------------------------------------------------------------------

    public Position getPosition() {
        return position;
    }

    public Direction getFacing() {
        return facing;
    }

    public GameStatus getStatus() {
        return status;
    }

    /**
     * 現在位置がゴールかどうかを判定します。
     *
     * @return ゴールにいれば true
     */
    public boolean isAtGoal() {
        return map.isGoal(position);
    }

    /**
     * 現在位置が落とし穴かどうかを判定します。
     * （落ちた時点でゲームは失敗になるため、true になるのは落下直後です）
     *
     * @return 落とし穴の上にいれば true
     */
    public boolean isOnPit() {
        return map.isPit(position);
    }

    // ------------------------------------------------------------------
    // 移動（上下左右）
    // ------------------------------------------------------------------

    public ActionResult moveUp() {
        return move(Direction.UP);
    }

    public ActionResult moveDown() {
        return move(Direction.DOWN);
    }

    public ActionResult moveLeft() {
        return move(Direction.LEFT);
    }

    public ActionResult moveRight() {
        return move(Direction.RIGHT);
    }

    /**
     * 指定した方向へ1マス移動します（上下左右の共通処理）。
     *
     * 処理の流れ:
     *   1. ゲームがすでに終わっていたら何もしない
     *   2. アリの向きを更新する（壁にぶつかる場合でも、その方向を向く）
     *   3. 移動先がマップ外なら動かない
     *   4. 移動先が壁なら動かない
     *   5. 移動する。移動先が落とし穴なら落下、ゴールならクリア
     *
     * @param dir 移動する方向
     * @return 行動の結果
     */
    public ActionResult move(Direction dir) {
        if (status != GameStatus.PLAYING) {
            return ActionResult.GAME_ALREADY_OVER;
        }
        facing = dir;

        Position next = dir.step(position, 1);

        // 先に「マップ外」を調べる（範囲外の座標で地形を調べないようにするため）
        if (map.isOutOfMap(next)) {
            return ActionResult.BLOCKED_OUT_OF_MAP;
        }
        if (map.isWall(next)) {
            return ActionResult.BLOCKED_WALL;
        }

        position = next;
        return resolveLanding(ActionResult.MOVED);
    }

    // ------------------------------------------------------------------
    // ジャンプ（上下左右）
    // ------------------------------------------------------------------

    public ActionResult jumpUp() {
        return jump(Direction.UP);
    }

    public ActionResult jumpDown() {
        return jump(Direction.DOWN);
    }

    public ActionResult jumpLeft() {
        return jump(Direction.LEFT);
    }

    public ActionResult jumpRight() {
        return jump(Direction.RIGHT);
    }

    /**
     * 指定した方向へジャンプし、前方1マスの落とし穴を飛び越えて2マス先へ着地します。
     *
     * 処理の流れ:
     *   1. ゲームがすでに終わっていたら何もしない
     *   2. アリの向きを更新する
     *   3. 前方1マスが落とし穴でなければ失敗（その場から動かない）
     *   4. 2マス先（着地点）がマップ外・壁なら失敗（その場から動かない）
     *   5. 着地する。着地点が落とし穴なら落下、ゴールならクリア
     *
     * @param dir ジャンプする方向
     * @return 行動の結果
     */
    public ActionResult jump(Direction dir) {
        if (status != GameStatus.PLAYING) {
            return ActionResult.GAME_ALREADY_OVER;
        }
        facing = dir;

        Position front = dir.step(position, 1);
        Position landing = dir.step(position, 2);

        // 前方1マスが落とし穴でなければジャンプ失敗（マップ外の場合も isPit が false になる）
        if (!map.isPit(front)) {
            return ActionResult.JUMP_FAILED_NO_PIT;
        }
        // 着地点が使えない場合もジャンプ失敗
        if (map.isOutOfMap(landing) || map.isWall(landing)) {
            return ActionResult.JUMP_FAILED_BLOCKED;
        }

        position = landing;
        return resolveLanding(ActionResult.JUMPED);
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    /**
     * 移動／着地した直後のマスを調べ、落下・クリアの判定をして結果を返します。
     *
     * @param normalResult 落とし穴でもゴールでもなかった場合に返す結果（MOVED または JUMPED）
     */
    private ActionResult resolveLanding(ActionResult normalResult) {
        if (isOnPit()) {
            status = GameStatus.FAILED;
            return ActionResult.FELL_INTO_PIT;
        }
        if (isAtGoal()) {
            status = GameStatus.CLEARED;
            return ActionResult.REACHED_GOAL;
        }
        return normalResult;
    }

    /**
     * プログラムを最後まで実行してもゴールに着けなかった場合など、
     * 外部（ProgramRunner）からゲームを失敗扱いにするために使います。
     * すでに終了している場合は何もしません。
     */
    public void markFailed() {
        if (status == GameStatus.PLAYING) {
            status = GameStatus.FAILED;
        }
    }
}
