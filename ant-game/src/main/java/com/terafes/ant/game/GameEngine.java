package com.terafes.ant.game;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * アリの移動・ジャンプとゲーム状態を管理するエンジンです。
 *
 * 【移動・ジャンプのルール】
 *   ・移動は1回につき1マス。移動先が「マップ外」「壁」「閉じたドア」ならその場から動きません。
 *   ・落とし穴へ通常移動すると、穴のマスへ移動した直後に落下 → 失敗（FAILED）になります。
 *   ・ジャンプは「前方1マスが落とし穴のときだけ」成功し、2マス先へ着地します。
 *       - 前方1マスが落とし穴でない → ジャンプ失敗（その場から動かない）
 *       - 着地点が壁／閉じたドア／マップ外 → ジャンプ失敗（その場から動かない）
 *       - 着地点が落とし穴 → 落下して失敗
 *   ・ゴールに着いた時点でクリア（CLEARED）となり、以降の行動は受け付けません。
 *
 * 【アイテムとスイッチのルール（すべて自動で行われます）】
 *   ・物資／武器のあるマスに着くと、自動で取得します。取得したアイテムはそのマスから消えます。
 *   ・持てるのは物資1つ・武器1つまで（別々のスロット）。同じ種類を持っているときは取得せず、床に残します。
 *   ・スイッチのマスに着いたとき、物資を持っていれば物資を1つ消費してスイッチが作動し、
 *     同じ記号のドアがすべて開きます。作動したスイッチは、もう一度乗っても物資を消費しません。
 *   ・物資を持っていないときは何も起きません（スイッチは通れるマスです）。
 *
 * 1回のゲームごとに new GameEngine(map) で作ります。
 * 状態（位置・持ち物・ドアの開閉など）を持つため、複数のリクエストで使い回さないでください。
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

    /** まだ床に残っているアイテム（座標 → 種類）。取得すると取り除かれる */
    private final Map<Position, ItemType> remainingItems;
    /** 作動済みのスイッチの位置 */
    private final Set<Position> usedSwitches = new HashSet<>();
    /** 開いているドアの対応記号（小文字）。この記号のドアはすべて開いている */
    private final Set<Character> openedLinks = new HashSet<>();
    /** 物資スロット: 物資を持っているか */
    private boolean hasSupply = false;
    /** 武器スロット: 武器を持っているか */
    private boolean hasWeapon = false;
    /** 直前の1回の行動で自動的に起きた出来事 */
    private List<GameEvent> lastEvents = new ArrayList<>();

    /**
     * @param map ゲームで使うマップ。アリはマップのスタート地点から始まります
     */
    public GameEngine(GameMap map) {
        this.map = map;
        this.position = map.getStart();
        this.facing = Direction.RIGHT; // 初期の向きは右
        this.status = GameStatus.PLAYING;
        // マップの初期配置をコピーして使う（元のマップは書き換えない）
        this.remainingItems = new HashMap<>(map.getInitialItems());
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

    /** 物資を持っているか。 */
    public boolean hasSupply() {
        return hasSupply;
    }

    /** 武器を持っているか。 */
    public boolean hasWeapon() {
        return hasWeapon;
    }

    /**
     * 直前の1回の行動で自動的に起きた出来事（取得・スイッチ作動・ドアが開く）を返します。
     * 次の行動をすると入れ替わります。
     */
    public List<GameEvent> getLastEvents() {
        return List.copyOf(lastEvents);
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

    /**
     * 指定したマスが「閉じているドア」かどうかを判定します。
     * ドアでないマスや、開いているドアは false です。
     */
    public boolean isClosedDoor(Position pos) {
        return map.isDoor(pos) && !openedLinks.contains(map.getLink(pos));
    }

    /** 指定したマスにまだアイテムが残っているか。 */
    public boolean hasItemAt(Position pos) {
        return remainingItems.containsKey(pos);
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
     *   3. 移動先がマップ外・壁・閉じたドアなら動かない
     *   4. 移動する。着いたマスで、アイテム取得・スイッチ作動・落下・クリアを判定する
     *
     * @param dir 移動する方向
     * @return 行動の結果
     */
    public ActionResult move(Direction dir) {
        lastEvents = new ArrayList<>();
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
        if (isClosedDoor(next)) {
            return ActionResult.BLOCKED_DOOR;
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
     *   4. 2マス先（着地点）がマップ外・壁・閉じたドアなら失敗（その場から動かない）
     *   5. 着地する。着いたマスで、アイテム取得・スイッチ作動・落下・クリアを判定する
     *
     * @param dir ジャンプする方向
     * @return 行動の結果
     */
    public ActionResult jump(Direction dir) {
        lastEvents = new ArrayList<>();
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
        if (map.isOutOfMap(landing) || map.isWall(landing) || isClosedDoor(landing)) {
            return ActionResult.JUMP_FAILED_BLOCKED;
        }

        position = landing;
        return resolveLanding(ActionResult.JUMPED);
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    /**
     * 移動／着地した直後のマスを調べ、自動処理と落下・クリアの判定をして結果を返します。
     *
     * 順番: 落下の判定 → アイテム取得 → スイッチ作動 → ゴールの判定
     *
     * @param normalResult 特別なことが起きなかった場合に返す結果（MOVED または JUMPED）
     */
    private ActionResult resolveLanding(ActionResult normalResult) {
        if (isOnPit()) {
            status = GameStatus.FAILED;
            return ActionResult.FELL_INTO_PIT;
        }
        pickUpItem();
        activateSwitch();
        if (isAtGoal()) {
            status = GameStatus.CLEARED;
            return ActionResult.REACHED_GOAL;
        }
        return normalResult;
    }

    /**
     * 現在のマスにアイテムがあれば取得します。
     * 同じ種類のアイテムをすでに持っているときは取得せず、床に残します。
     */
    private void pickUpItem() {
        ItemType item = remainingItems.get(position);
        if (item == null) {
            return;
        }
        if (item == ItemType.SUPPLY && !hasSupply) {
            hasSupply = true;
        } else if (item == ItemType.WEAPON && !hasWeapon) {
            hasWeapon = true;
        } else {
            return; // スロットが埋まっているので取得しない
        }
        remainingItems.remove(position);
        lastEvents.add(new GameEvent(EventType.PICKUP, item, position.x(), position.y()));
    }

    /**
     * 現在のマスが未作動のスイッチで、物資を持っていれば、物資を消費してスイッチを作動させます。
     * 同じ記号のドアがすべて開きます。
     */
    private void activateSwitch() {
        if (!map.isSwitch(position) || usedSwitches.contains(position) || !hasSupply) {
            return;
        }
        hasSupply = false;
        usedSwitches.add(position);
        lastEvents.add(new GameEvent(EventType.SWITCH_ON, null, position.x(), position.y()));

        char link = map.getLink(position);
        // すでに開いている記号のドアなら、新しく開く出来事は起きない
        if (openedLinks.add(link)) {
            for (Position door : map.getDoorsFor(link)) {
                lastEvents.add(new GameEvent(EventType.DOOR_OPEN, null, door.x(), door.y()));
            }
        }
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
