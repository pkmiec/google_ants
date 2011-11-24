import java.util.*;
import java.io.IOException;

/**
* Starter bot implementation.
*/
public class MyBot extends Bot {
  Random random;

  Map<Tile, Tile> orders = new HashMap<Tile, Tile>();
  Set<Tile> enemyHills  = new HashSet<Tile>();
  Set<Tile> waterTiles  = new HashSet<Tile>();

  Tile targetEnemyHill = null;
  Map<Tile, Set<Tile>> combatAnts = null;

  enum Agent {
    EXPLORE(100);
    
    private final double value;
    Agent(double value) {
      this.value = value;
    }
    
    public double getValue() {
      return this.value;
    }
  }

  class CombatValues {
    final Map<Tile, Integer> tilesAttackers;
    final Map<Tile, Integer> antsOwners;
    
    public CombatValues() {
      this(ants.getAnts());
    }
    
    public CombatValues(Map<Tile,Integer> antsOwners) {
      this.tilesAttackers = new HashMap<Tile, Integer>();
      this.antsOwners = antsOwners;
    }
    
    public int getTileAttackers(Tile tile, int owner) {
      if (tilesAttackers.containsKey(tile)) {
        return tilesAttackers.get(tile);
      }
      
      int tileAttackers = 0;
      for (Tile offset : ants.getAggressionOffsets()) {
        Tile loc = ants.getTile(tile, offset);
        Integer locOwner = antsOwners.get(loc);
      
        if (locOwner != null && locOwner != owner) {
          tileAttackers++;
        }
      }
      tilesAttackers.put(tile, tileAttackers);
      return tileAttackers;
    }
    
    public boolean willDie(Tile tile, int owner) {
      int myAttackers = getTileAttackers(tile, owner); // ants attacking me
      int difference = myAttackers;
  
      if (myAttackers == 0) { return false; }
      
      for (Tile offset : ants.getAggressionOffsets()) {
        Tile loc = ants.getTile(tile, offset);
        Integer locOwner = antsOwners.get(loc);
    
        if (locOwner != null && locOwner != owner) {
          int locAttackers = getTileAttackers(loc, locOwner); // ants attacking them
          // System.err.println("attacking difference: " + myAttackers + " " + locAttackers);
          if (myAttackers >= locAttackers) {
            return true;
          }
        }
      }
      
      return false;
    }
    
  }

  class Square extends Tile {
    protected final double[] values;
    protected int seenOnTurn;
    
    public Square(int row, int col) {
      super(row, col);
      
      this.values = new double[Agent.values().length];
      for (int i = 0; i < values.length; i++) {
        this.values[i] = 0;
      }
      this.seenOnTurn = -1;
    }
    
    public double getValue(Agent agent) {
      if (waterTiles.contains(this)) {
        return 0;
      }
      
      switch (agent) {
        case EXPLORE:
          if (ants.getMyAnts().contains(this)) {
            if (!combatAnts.containsKey(this)) {
              values[agent.ordinal()] = 0;
            }
          } else if (ants.getEnemyAnts().contains(this)) {
            values[agent.ordinal()] = 30;
          } else if (enemyHills.contains(this)) {
            values[agent.ordinal()] = 100;
          } else if (!ants.isVisible(this)) {
              values[agent.ordinal()] = seenOnTurn == -1 ? 40 : (turn - seenOnTurn);
          } else {
            if (this.seenOnTurn == -1) {
              values[agent.ordinal()] = 0;
            }
            this.seenOnTurn = turn;
          }
          break;
      }
      return values[agent.ordinal()];
    }

    public void setValue(Agent agent, double value) {
      values[agent.ordinal()] = value;
    }
  }

  class AimValue implements Comparable<AimValue> {
    public final Aim aim;
    public final double value;
    
    public AimValue(Aim aim, double value) {
      this.aim   = aim;
      this.value = value;
    }
    
    public String toString() {
      return aim + "(" + value + ")";
    }
    
    public int compareTo(AimValue other) {
      return Double.compare(value, other.value);
    }
  }

  class Squares {
    final int rows;
    final int cols;
    final Square[][] squares;
    
    public Squares(int rows, int cols) {
      this.rows = rows;
      this.cols = cols;
      this.squares = new Square[this.rows][this.cols];
      for (int r = 0; r < rows; r = r + 1) {
        for (int c = 0; c < cols; c = c + 1) {
          this.squares[r][c] = new Square(r, c);
        }
      }
    }

    public void diffuse(Agent agent) {
      long t0 = System.currentTimeMillis();
      for (int r = 0; r < rows ; r++) {
        for (int c = 0; c < cols; c++) {
          double sum = 0.0;
          double sides = 0;
          if (!waterTiles.contains(squares[r][c])) {
            if (!waterTiles.contains(squares[(rows + r - 1) % rows][c])) {
              sum = sum + squares[(rows + r - 1) % rows][c].getValue(agent);
              sides++;
            }
            if (!waterTiles.contains(squares[(r + 1) % rows][c])) {
              sum = sum + squares[(r + 1) % rows][c].getValue(agent);
              sides++;
            }
            if (!waterTiles.contains(squares[r][(cols + c - 1) % cols])) {
              sum = sum + squares[r][(cols + c - 1) % cols].getValue(agent);
              sides++;
            }
            if (!waterTiles.contains(squares[r][(c + 1) % cols])) {            
              sum = sum + squares[r][(c + 1) % cols].getValue(agent);
              sides++;
            }
            sum = sum / sides;
          }
          squares[r][c].setValue(agent, sum);
        }
      }
      // System.err.println("diffusion time: " + (System.currentTimeMillis() - t0));
    }
    
    public void print(Agent agent) {
      for (int r = 0; r < rows ; r++) {
        for (int c = 0; c < cols; c++) {
          if (waterTiles.contains(squares[r][c])) {
            System.err.print(' ');
          } else {
            double value = squares[r][c].getValue(agent);
            if (value >= 0) {
              System.err.print(value > 100 ? '+' : ((char) ((value / 100.0 * 25) + 'A')));  
            } else {
              System.err.print(-value > 100 ? '-' : ((char) ((-value / 100.0 * 25) + 'a')));  
            }
          }
        }
        System.err.println();
      }
      System.err.println();
    }
    
    public List<AimValue> getDirections(final Agent agent, final Tile tile, final int ascending) {
      final ArrayList<AimValue> values = new ArrayList<AimValue>();
      final int r = tile.getRow();
      final int c = tile.getCol();
      
      values.add(new AimValue(Aim.NORTH, squares[(rows + r - 1) % rows][c].getValue(agent)));
      values.add(new AimValue(Aim.SOUTH, squares[(r + 1) % rows][c].getValue(agent)));
      values.add(new AimValue(Aim.WEST, squares[r][(cols + c - 1) % cols].getValue(agent)));
      values.add(new AimValue(Aim.EAST, squares[r][(c + 1) % cols].getValue(agent)));
      values.add(new AimValue(null, squares[r][c].getValue(agent)));
      
      if (ascending > 0) {
        Collections.sort(values);
      } else {
        Collections.sort(values, Collections.reverseOrder());
      }
      return values;
    }

    public List<AimValue> getProbabilisticDirections(final Agent agent, final Tile tile, final int ascending) {
      List<AimValue> values = getDirections(agent, tile, ascending);
      List<AimValue> result = new ArrayList<AimValue>(values);
      double tileValue = getValue(agent, tile);
      
      for (AimValue value : values) {
        if (random.nextInt(100) < 20) {
          // move to the end
          result.remove(value);
          result.add(value);
        }
      }
      
      return result;
    }
    
    public double getValue(final Agent agent, final Tile tile) {
      final int r = tile.getRow();
      final int c = tile.getCol();
      return squares[r][c].getValue(agent);
    }

    public double getValue(final Agent agent, final Tile tile, Aim direction) {
      return getValue(agent, direction != null ? ants.getTile(tile, direction) : tile);
    }
    
  }
  Squares squares;
  int turn = 0;

  public static void main(String[] args) throws IOException {
    new MyBot().readSystemInput();
  }
  
  public void setup(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2, int attackRadius2, int spawnRadius2) {
    super.setup(loadTime, turnTime, rows, cols, turns, viewRadius2, attackRadius2, spawnRadius2);
    
    random = new Random(1);
    squares = new Squares(rows, cols);
  }
  
  public void afterUpdate() {
    long t0 = System.currentTimeMillis();
    super.afterUpdate();

    orders.clear();
    for (Tile myHill : ants.getMyHills()) {
      orders.put(myHill, null);
    }

    // Remove hills that are no longer active
    if (targetEnemyHill != null && !enemyHills.contains(targetEnemyHill)) {
      targetEnemyHill = null;
    }
    for (Iterator<Tile> it = enemyHills.iterator(); it.hasNext(); ) {
      Tile enemyHill = it.next();
      if (ants.isVisible(enemyHill) && !ants.getEnemyHills().contains(enemyHill)) {
        it.remove();
      } else if (targetEnemyHill == null) {
        targetEnemyHill = enemyHill;
      }
    }
    
    waterTiles.addAll(ants.getWaterTiles());
    enemyHills.addAll(ants.getEnemyHills());
    
    t0 = System.currentTimeMillis();
    combatAnts = new HashMap<Tile, Set<Tile>>();
    
    Set<Tile> locs = new HashSet<Tile>();
    locs.addAll(ants.getMyAnts());
    locs.addAll(ants.getMyHills());
    
    for (Tile antLoc : locs) {
      Set<Tile> enemySet = new HashSet<Tile>();
      
      for (Tile offset : ants.getVisionOffsets()) {
        Tile loc = ants.getTile(antLoc, offset);
        if (ants.getEnemyAnts().contains(loc)) {
          enemySet.add(loc);
        }
      }
      
      if (!enemySet.isEmpty()) {
        combatAnts.put(antLoc, enemySet);
      }
    }
    // System.err.println("combatAnts: " + (System.currentTimeMillis() - t0));
  }
  
  private boolean moveDirection(Tile antLoc, Aim direction) {
    return moveDirection(antLoc, direction, orders);
  }

  private boolean moveDirection(Tile antLoc, Aim direction, Map<Tile,Tile> orders) {
    if (direction == null) {
      if (!orders.containsKey(antLoc)) {
        orders.put(antLoc, antLoc);
        return true;
      } else {
        return false;
      }
    }
    
    Ants ants = getAnts();
    
    Tile newLoc = ants.getTile(antLoc, direction);
    if (!waterTiles.contains(newLoc) && !orders.containsKey(newLoc)) {
      orders.put(newLoc, antLoc);
      return true;
    } else {
      return false;
    }
  }

  public void moveAntsOnTargets(Set<Tile> targets, int maxAnts, int maxDistance, Map<Tile,Tile> tmpOrders) {
    PriorityQueue<Route> possibleRoutes = new PriorityQueue<Route>();
    Map<Tile,Set<Route>> antsOnTarget = new HashMap<Tile,Set<Route>>();
    Route route = null;

    for (Tile foodLoc : targets) {
      for (Tile antLoc : ants.getMyAnts()) {
        if (tmpOrders.containsValue(antLoc)) { continue; }

        List<Aim> directions = new Router(antLoc, foodLoc).directions(maxDistance);
        if (directions != null) {
          route = new Route(antLoc, foodLoc, directions);
          possibleRoutes.offer(route);
        }
      }
    }
    
    while ((route = possibleRoutes.poll()) != null) {
      if (maxAnts > 0 && antsOnTarget.containsKey(route.getEnd()) && antsOnTarget.get(route.getEnd()).size() >= maxAnts) { continue; }
      if (tmpOrders.containsValue(route.getStart())) { continue; }

      if (route.getDirections().size() == 1) {
        moveDirection(route.getStart(), null, tmpOrders);
      } else {
        moveDirection(route.getStart(), route.getDirections().get(0), tmpOrders);
      }
    }
  }

  public void moveFoodAnts(Map<Tile,Tile> tmpOrders) {
    moveAntsOnTargets(ants.getFoodTiles(), 1, 10, tmpOrders);
  }

  public void moveAnts() {
    Map<Tile,Tile> tmpOrders = new HashMap<Tile,Tile>(orders);

    moveFoodAnts(tmpOrders);
    
    // Try to move ants towards highest explore goals.
    for (Tile antLoc : ants.getMyAnts()) {
      if (tmpOrders.containsValue(antLoc)) { continue; }
      
      List<AimValue> directions = squares.getDirections(Agent.EXPLORE, antLoc, -1);
      // System.err.println(antLoc + " " + directions);
      for (AimValue direction : directions) {
        if (moveDirection(antLoc, direction.aim, tmpOrders)) {
          // System.err.println(" -> " + direction.aim);
          break;
        }
      }
      if (!tmpOrders.containsValue(antLoc)) {
        // System.err.println(antLoc + " fucked");
        while (tmpOrders.containsKey(antLoc)) {
          // System.err.println("undo " + antLoc);
          antLoc = tmpOrders.put(antLoc, antLoc);
        }
      }
    }

    // Prevent ants from dying.
    while (tmpOrders.size() > 0) {
      boolean noTmpOrdersRemoved = true;

      Map<Tile,Integer> tmpAnts = new HashMap<Tile,Integer>(ants.getAnts());
      for (Map.Entry<Tile,Tile> entry: tmpOrders.entrySet()) {
        tmpAnts.remove(entry.getValue());
        tmpAnts.put(entry.getKey(), 0);
      }
      CombatValues combatValues = new CombatValues(tmpAnts);
      for (Iterator<Map.Entry<Tile,Tile>> it = tmpOrders.entrySet().iterator(); it.hasNext(); ) {
        Map.Entry<Tile,Tile> tmpOrder = it.next();
        Tile newLoc = tmpOrder.getKey();
        Tile oldLoc = tmpOrder.getValue();

        if (orders.containsValue(oldLoc)) { continue; } // skip real orders
        if (combatValues.willDie(newLoc, 0)) {
          noTmpOrdersRemoved = false;
          it.remove();
          // System.err.println(oldLoc + " ->  " + newLoc + " will die");
          break;
        }
      }
      
      if (noTmpOrdersRemoved) { break; }
    }
    
    // Try to move ants away towards lowest goals.
    for (Tile antLoc : ants.getMyAnts()) {
      if (tmpOrders.containsValue(antLoc)) { continue; }
      
      List<AimValue> directions = squares.getDirections(Agent.EXPLORE, antLoc, 1);
      // System.err.println(antLoc + " " + directions);
      for (AimValue direction : directions) {
        if (moveDirection(antLoc, direction.aim, tmpOrders)) {
          // System.err.println(" -> " + direction.aim);
          break;
        }
      }
      if (!tmpOrders.containsValue(antLoc)) {
        // System.err.println(antLoc + " fucked 2");
        while (tmpOrders.containsKey(antLoc)) {
          // System.err.println("undo " + antLoc);
          antLoc = tmpOrders.put(antLoc, antLoc);
        }
      }
    }
    
    orders = tmpOrders;
  }

  public void issueOrders() {
    for (Map.Entry<Tile,Tile> order : orders.entrySet()) {
      Tile newLoc = order.getKey();
      Tile oldLoc = order.getValue();
      
      if (newLoc != null && oldLoc != null && !newLoc.equals(oldLoc)) {
        List<Aim> aims = ants.getDirections(oldLoc, newLoc);
        ants.issueOrder(oldLoc, aims.get(0));
      }
    }
  }

  public void doTurn() {
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      squares.diffuse(Agent.EXPLORE);
    }
    // System.err.println("diffusion: " + (System.currentTimeMillis() - t0));
    // squares.print(Agent.EXPLORE);
    
    t0 = System.currentTimeMillis();
    moveAnts();
    // System.err.println("moveAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    issueOrders();
    
    // System.err.println("-------------" + ants.getTimeRemaining());
    turn++;
  }
  
  class Router {
    Tile from;
    Tile to;
    
    Set<Tile> closedSet         = new HashSet<Tile>();
    Set<Tile> openSet           = new HashSet<Tile>();
    Map<Tile,Object[]> cameFrom = new HashMap<Tile,Object[]>();

    Map<Tile,Float> gScore = new HashMap<Tile,Float>();
    Map<Tile,Float> hScore = new HashMap<Tile,Float>();
    Map<Tile,Float> fScore = new HashMap<Tile,Float>();
    TreeMap<Tile,Float> fSorted = new TreeMap<Tile, Float>(new Comparator<Tile>() {
      public int compare(Tile a, Tile b) {
        return Double.compare(fScore.get(a), fScore.get(b));
      }
    });
    
    public Router(Tile from, Tile to) {
      this.from = from;
      this.to   = to;

      openSet.add(from);
      
      gScore.put(from, 0.0f);
      hScore.put(from, (float) ants.getManhattanDistance(from, to));
      fScore.put(from, gScore.get(from) + hScore.get(from));
    }

    public List<Aim> directions() {
      return directions(-1);
    }
    
    public List<Aim> directions(int maxDistance) {
      Ants ants = getAnts();

      while (!openSet.isEmpty()) {
        fSorted.clear();
        for (Tile x: openSet) {
          if (maxDistance == -1 || fScore.get(x).intValue() <= maxDistance) { fSorted.put(x, fScore.get(x)); }
        }
        if (fSorted.isEmpty()) {
          break;
        }
        
        Tile x = fSorted.firstKey();

        // System.err.println("first sorted: " + x);
        if (x.equals(to)) { 
          return reconstructPath(cameFrom, to);
        }

        openSet.remove(x);
        closedSet.add(x);

        for (Aim direction : Aim.values()) {
          Tile y = ants.getTile(x, direction);
          if (closedSet.contains(y))        { continue; }
          if (waterTiles.contains(y))       { continue; }
          if (ants.getMyHills().contains(y)) { continue; }

          // System.err.println("considering: " + y);
          float distance = 1.0f;
          // if (ants.isVisible(y)) {
          //   Ilk ilk = ants.getIlk(y);
          //   if (orders.containsKey(y)) {
          //     distance = 2.0f;
          //   }
          // }

          float yGScore = gScore.get(x) + distance;
          if (!openSet.contains(y) || yGScore < gScore.get(y)) {
            openSet.add(y);

            Object[] fromDir = new Object[2];
            fromDir[0] = x;
            fromDir[1] = direction;
            cameFrom.put(y, fromDir);
            gScore.put(y, yGScore);
            hScore.put(y, (float) ants.getManhattanDistance(y, to));
            fScore.put(y, gScore.get(y) + hScore.get(y));
          }
        }
      }
      return null;
    }

    private List<Aim> reconstructPath(Map<Tile,Object[]> cameFrom, Tile tile) {
      Object[] from = cameFrom.get(tile);
      if (from != null) {
        List<Aim> path = reconstructPath(cameFrom, (Tile) from[0]);
        path.add((Aim) from[1]);
        return path;
      } else {
        return new ArrayList<Aim>();
      }
    }
    
  }
  
}
