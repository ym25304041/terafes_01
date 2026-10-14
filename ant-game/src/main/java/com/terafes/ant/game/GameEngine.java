package com.terafes.ant.game;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * アリの移動・ジャンプ・待機、敵の動き、ゲーム状態を管理するエンジンです。
 *
 * 【時間（ターン）のルール】
 *   ・アリが1回行動する（移動・ジャンプ・待つ）ごとに、時間が1ステップ進みます。
 *     壁にぶつかって動けなかった場合や、ジャンプに失敗した場合も、1ステップ進みます。
 *   ・1ステップの順番は「アリが先に行動 → そのあと敵が動く」です。
 *   ・条件を調べる(check)だけでは、時間は進みません。
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
 * 【敵のルール（すべて自動で行われます）】
 *   ・敵は動かない敵（マップの 'X'）と、巡回する敵（接敵ゾーン）の2種類です。
 *   ・巡回する敵は、アリの行動のあとに、ゾーンの上を1マス進みます。端に着いたらすぐ折り返します。
 *   ・アリと敵が同じマスになると、戦闘になります。
 *       - アリが敵のマスに着いたとき（アリの行動中）
 *       - 敵が動いて、アリのいるマスに入ったとき（敵の行動中。アリがその場で待っていても起きます）
 *   ・戦闘の結果:
 *       - 武器を持っている → 敵を討伐します。敵はマップからいなくなります。武器は消費されません。
 *       - 武器を持っていない → 敗北してゲームオーバー（FAILED）になります。
 *
 * 1回のゲームごとに new GameEngine(map) で作ります。
 * 状態（位置・持ち物・敵の位置・ドアの開閉など）を持つため、複数のリクエストで使い回さないでください。
 * （Servlet は複数人が同時に使うので、インスタンスを共有すると他人の状態と混ざります）
 */
public final class GameEngine {

    /** 地形データ（変更しない） */
    private final GameMap map;
    /** アリの現在位置 */
    private Position position;
    /** アリの向き（直近に動こうとした方向）。条件の「目の前」の判定に使う */
    private Direction facing;
    /** ゲームの状態 */
    private GameStatus status;

    /** まだ床に残っているアイテム（座標 → 種類）。取得すると取り除かれる */
    private final Map<Position, ItemType> remainingItems;
    /** 全ての敵（動かない敵と巡回する敵）。討伐されると alive が false になる */
    private final List<Enemy> enemies = new ArrayList<>();
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
     * 敵1体の状態（このクラスの中だけで使う）。
     * 動かない敵は、経路が1マスだけの敵として表します。
     */
    private static final class Enemy {
        /** 敵のID */
        final String id;
        /** 巡回する経路（動かない敵は1マスだけ） */
        final List<Position> path;
        /** 経路の中の現在位置（番号） */
        int index = 0;
        /** 進む向き（+1 または -1）。最初は先頭から末尾へ向かう（+1） */
        int direction = 1;
        /** 生きているか（討伐されたら false） */
        boolean alive = true;

        Enemy(String id, List<Position> path) {
            this.id = id;
            this.path = path;
        }

        Position position() {
            return path.get(index);
        }

        /** 次に進む向き。端に着いていたら折り返して逆向きになる。 */
        private int nextDirection() {
            int next = index + direction;
            return (next < 0 || next >= path.size()) ? -direction : direction;
        }

        /** 次のステップで敵がいるマス（動かさずに調べる）。動かない敵は今のマスのまま。 */
        Position nextPosition() {
            if (path.size() == 1) {
                return position();
            }
            return path.get(index + nextDirection());
        }

        /** 1マス進む。動かない敵は何もしない。 */
        void advance() {
            if (path.size() == 1) {
                return;
            }
            direction = nextDirection();
            index += direction;
        }
    }

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

        // 動かない敵: 画面の上から下、左から右の順に x0, x1, … とIDを付ける
        List<Position> fixed = new ArrayList<>(map.getInitialEnemies());
        fixed.sort(Comparator.comparingInt(Position::y).thenComparingInt(Position::x));
        for (int i = 0; i < fixed.size(); i++) {
            enemies.add(new Enemy("x" + i, List.of(fixed.get(i))));
        }
        // 巡回する敵: ゾーン番号を使って z1〜z5 とIDを付ける
        for (PatrolRoute route : map.getPatrols()) {
            enemies.add(new Enemy("z" + route.zoneId(), route.path()));
        }
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
     * 直前の1回の行動で自動的に起きた出来事（取得・スイッチ作動・ドアが開く・敵の討伐）を返します。
     * 次の行動をすると入れ替わります。
     */
    public List<GameEvent> getLastEvents() {
        return List.copyOf(lastEvents);
    }

    /**
     * 今、生きている全ての敵の位置を返します（敵のIDの順）。
     * 画面が敵を表示するために使います。
     */
    public List<EnemyState> getEnemyStates() {
        List<EnemyState> states = new ArrayList<>();
        for (Enemy e : enemies) {
            if (e.alive) {
                states.add(new EnemyState(e.id, e.position().x(), e.position().y()));
            }
        }
        return states;
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

    /** 指定したマスに、今、生きている敵がいるか（動かない敵・巡回する敵のどちらも）。 */
    public boolean hasEnemyAt(Position pos) {
        for (Enemy e : enemies) {
            if (e.alive && e.position().equals(pos)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // 条件の判定（時間は進まない）
    // ------------------------------------------------------------------

    /**
     * 条件が成り立っているかを調べます。「もし〜なら」「〜の間」から呼ばれます。
     * 調べるだけで、アリも敵も動きません（時間は進みません）。
     *
     * 「目の前」は、アリが今向いている方向の1マス先です。
     *
     * @param condition 調べる条件
     * @return 成り立っていれば true
     */
    public boolean check(ConditionType condition) {
        Position front = facing.step(position, 1);
        return switch (condition) {
            // マップの端も壁として扱う
            case WALL_AHEAD -> map.isOutOfMap(front) || map.isWall(front);
            case PIT_AHEAD -> map.isPit(front);
            case ENEMY_AHEAD -> hasEnemyAt(front);
            case ENEMY_COMING -> {
                // 敵が「今」目の前にいる、または「次のステップで」目の前に入ってくる
                boolean coming = false;
                for (Enemy e : enemies) {
                    if (e.alive && (e.position().equals(front) || e.nextPosition().equals(front))) {
                        coming = true;
                    }
                }
                yield coming;
            }
        };
    }

    // ------------------------------------------------------------------
    // 行動（移動・ジャンプ・待つ）
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
     * 指定した方向へ1マス移動します（上下左右の共通処理）。
     *
     * @param dir 移動する方向
     * @return 行動の結果
     */
    public ActionResult move(Direction dir) {
        return act(() -> doMove(dir));
    }

    /**
     * 指定した方向へジャンプし、前方1マスの落とし穴を飛び越えて2マス先へ着地します。
     *
     * @param dir ジャンプする方向
     * @return 行動の結果
     */
    public ActionResult jump(Direction dir) {
        return act(() -> doJump(dir));
    }

    /**
     * その場で1ステップ待ちます。アリは動きませんが、時間は進み、敵は動きます。
     *
     * @return 行動の結果（WAITED。敵に倒された場合は DEFEATED_BY_ENEMY）
     */
    public ActionResult waitOneTurn() {
        return act(() -> ActionResult.WAITED);
    }

    // ------------------------------------------------------------------
    // 内部処理
    // ------------------------------------------------------------------

    /**
     * アリの行動1回ぶんの共通処理です。移動・ジャンプ・待つは、すべてここを通ります。
     *
     * 流れ:
     *   1. 出来事の記録を空にする
     *   2. ゲームがすでに終わっていたら何もしない（時間も進まない）
     *   3. アリが行動する（action）
     *   4. ゲームが続いていれば、敵が動く（敵がアリと同じマスに入れば戦闘）
     *
     * @param action アリの行動（結果を返す処理）
     * @return 行動の結果。敵の行動中に倒された場合は DEFEATED_BY_ENEMY
     */
    private ActionResult act(Supplier<ActionResult> action) {
        lastEvents = new ArrayList<>();
        if (status != GameStatus.PLAYING) {
            return ActionResult.GAME_ALREADY_OVER;
        }
        ActionResult result = action.get();
        // アリの行動でクリア・失敗が決まったら、敵は動かない。続いていれば敵のターン。
        if (status == GameStatus.PLAYING && enemiesTurn()) {
            return ActionResult.DEFEATED_BY_ENEMY;
        }
        return result;
    }

    /**
     * 移動の本体。
     *   1. アリの向きを更新する（壁にぶつかる場合でも、その方向を向く）
     *   2. 移動先がマップ外・壁・閉じたドアなら動かない
     *   3. 移動する。着いたマスで、アイテム取得・戦闘・スイッチ作動・落下・クリアを判定する
     */
    private ActionResult doMove(Direction dir) {
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

    /**
     * ジャンプの本体。
     *   1. アリの向きを更新する
     *   2. 前方1マスが落とし穴でなければ失敗（その場から動かない）
     *   3. 2マス先（着地点）がマップ外・壁・閉じたドアなら失敗（その場から動かない）
     *   4. 着地する。着いたマスで、アイテム取得・戦闘・スイッチ作動・落下・クリアを判定する
     */
    private ActionResult doJump(Direction dir) {
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

    /**
     * 移動／着地した直後のマスを調べ、自動処理と落下・クリアの判定をして結果を返します。
     *
     * 順番: 落下の判定 → アイテム取得 → 敵との戦闘 → スイッチ作動 → ゴールの判定
     *
     * @param normalResult 特別なことが起きなかった場合に返す結果（MOVED または JUMPED）
     */
    private ActionResult resolveLanding(ActionResult normalResult) {
        if (isOnPit()) {
            status = GameStatus.FAILED;
            return ActionResult.FELL_INTO_PIT;
        }
        pickUpItem();
        if (meetEnemies()) {
            return ActionResult.DEFEATED_BY_ENEMY;
        }
        activateSwitch();
        if (isAtGoal()) {
            status = GameStatus.CLEARED;
            return ActionResult.REACHED_GOAL;
        }
        return normalResult;
    }

    /**
     * 敵のターン: 巡回する敵がそれぞれ1マス進み、アリと同じマスになれば戦闘します。
     *
     * @return アリが敗北した場合 true
     */
    private boolean enemiesTurn() {
        for (Enemy e : enemies) {
            if (e.alive) {
                e.advance();
            }
        }
        return meetEnemies();
    }

    /**
     * アリのいるマスに敵がいれば戦闘します（アリが入ったときも、敵が入ってきたときも共通）。
     *
     * 武器を持っていれば、そのマスの敵を全て討伐して取り除きます（武器は消費しません）。
     * 持っていなければ敗北し、ゲームを失敗（FAILED）にします。
     *
     * @return 敗北した場合 true（このときは以降の処理を行わない）
     */
    private boolean meetEnemies() {
        List<Enemy> here = new ArrayList<>();
        for (Enemy e : enemies) {
            if (e.alive && e.position().equals(position)) {
                here.add(e);
            }
        }
        if (here.isEmpty()) {
            return false;
        }
        if (!hasWeapon) {
            status = GameStatus.FAILED;
            return true;
        }
        for (Enemy e : here) {
            e.alive = false;
            lastEvents.add(new GameEvent(EventType.ENEMY_DEFEATED, null, position.x(), position.y()));
        }
        return false;
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
