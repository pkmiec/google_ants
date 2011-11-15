import java.util.*;
import java.io.IOException;

/**
* Starter bot implementation.
*/
public class MyBot extends Bot {
  Random random;
  double defenseRadius2    = 2.0;
  Set<Tile> defenseOffsets = new HashSet<Tile>();

  Map<Tile, Tile> orders = new HashMap<Tile, Tile>();
  Set<Tile> enemyHills  = new HashSet<Tile>();
  Set<Tile> waterTiles  = new HashSet<Tile>();

  CombatValues combatValues = null;
  Tile targetEnemyHill = null;

  enum Agent {
    ENEMY_ANTS(100),
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
    Map<Tile, Integer> values = new HashMap<Tile, Integer>();
    
    public CombatValues() {}
    
    public int getValue(Tile tile, int owner) {
      if (!values.containsKey(tile)) {
        int value = 0;
        for (Tile offset : ants.getAttackOffsets()) {
          Tile loc = ants.getTile(tile, offset);
          Integer locOwner = ants.getAnts().get(loc);
        
          if (locOwner != null && locOwner != owner) {
            value++;
          }
        }
        values.put(tile, value);
      }
      
      return values.get(tile);
    }
    
    public boolean willDie(Tile tile, int owner) {
      int tileValue = getValue(tile, owner);
      
      if (tileValue > 0) {
        for (Tile offset : ants.getAttackOffsets()) {
          Tile loc = ants.getTile(tile, offset);
          Integer locOwner = ants.getAnts().get(loc);
      
          if (locOwner != null && locOwner != owner && tileValue >= getValue(loc, locOwner)) {
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
        case ENEMY_ANTS:
          if (ants.getEnemyAnts().contains(this)) {
            values[agent.ordinal()] = agent.getValue();
          }
          break;
        case EXPLORE:
          if (enemyHills.contains(this)) {
            values[agent.ordinal()] = targetEnemyHill == this ? 1000 : 100;
          } else if (!ants.isVisible(this)) {
              values[agent.ordinal()] = seenOnTurn == -1 ? 100 : (turn - seenOnTurn) + 50;
          } else {
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
          if (!waterTiles.contains(squares[r][c])) { 
            sum = sum + squares[(rows + r - 1) % rows][c].getValue(agent);
            sum = sum + squares[(r + 1) % rows][c].getValue(agent);
            sum = sum + squares[r][(cols + c - 1) % cols].getValue(agent);
            sum = sum + squares[r][(c + 1) % cols].getValue(agent);
            sum = 0.25 * sum;
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
              System.err.print(value > 25 ? '*' : ((char) ((value / 25.0 * 25) + 'A')));  
            } else {
              System.err.print(-value > 25 ? '*' : ((char) ((-value / 25.0 * 25) + 'a')));  
            }
          }
        }
        System.err.println();
      }
      System.err.println();
    }
    
    public List<AimValue> getDirections(final Agent agent, final Tile tile) {
      final ArrayList<AimValue> values = new ArrayList<AimValue>();
      final int r = tile.getRow();
      final int c = tile.getCol();
      
      values.add(new AimValue(Aim.NORTH, squares[(rows + r - 1) % rows][c].getValue(agent)));
      values.add(new AimValue(Aim.SOUTH, squares[(r + 1) % rows][c].getValue(agent)));
      values.add(new AimValue(Aim.WEST, squares[r][(cols + c - 1) % cols].getValue(agent)));
      values.add(new AimValue(Aim.EAST, squares[r][(c + 1) % cols].getValue(agent)));
      values.add(new AimValue(null, squares[r][c].getValue(agent)));
      
      Collections.sort(values, Collections.reverseOrder());
      return values;
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
    
    int mx = (int) Math.sqrt(defenseRadius2);
    for (int row = -mx; row <= mx; ++row) {
      for (int col = -mx; col <= mx; ++col) {
        int d = row * row + col * col;
        if (d <= defenseRadius2) {
          defenseOffsets.add(new Tile(row, col));
        }
      }
    }
    
    // System.err.println("viewRadius: " + viewRadius2);
    // System.err.println("attackRadius: " + attackRadius2);
    // System.err.println("spawnRadius: " + spawnRadius2);
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

    // System.err.println("afterUpdate: " + (System.currentTimeMillis() - t0));
  }
  
  // public void updateWater() {
  //   PriorityQueue<Tile> newWater = new PriorityQueue<Tile>();
  // 
  //   for (Tile waterTile: ants.getWaterTiles()) {
  //     if (!waterTiles.contains(waterTile)) {
  //       waterTiles.add(waterTile);
  //       newWater.offer(waterTile);
  //     }
  //   }
  //   
  //   Tile waterTile = null;
  //   while ((waterTile = newWater.poll()) != null) {
  //     for (Aim direction : Aim.values()) {
  //       Tile pseudoWater = ants.getTile(waterTile, direction);
  //       if (waterTiles.contains(pseudoWater)) { continue; }
  //       
  //       int waterSides = 0;
  //       for (Aim direction2 : Aim.values()) {
  //         Tile adjLoc = ants.getTile(pseudoWater, direction2);
  //         if (waterTiles.contains(adjLoc)) { waterSides = waterSides + 1; }
  //       }
  //       if (waterSides >= 3) {
  //         waterTiles.add(pseudoWater);
  //         newWater.offer(pseudoWater);
  //       }
  //     }
  //   }
  // }
  
  // private boolean doMoveLocation(Tile antLoc, Tile destLoc) {
  //   Ants ants = getAnts();
  //   // Track targets to prevent 2 ants to the same location
  //   List<Aim> directions = new Router(antLoc, destLoc).directions();
  //   if (directions != null) {
  //     // System.err.println("direction: " + antLoc + " -> " + destLoc);
  //     // System.err.println(Arrays.toString(directions.toArray()));
  //     return doMoveDirection(antLoc, directions.get(0)); 
  //   }
  //   return false;
  // }

  private boolean moveDirection(Tile antLoc, Aim direction) {
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
      ants.issueOrder(antLoc, direction);
      orders.put(newLoc, antLoc);
      return true;
    } else {
      return false;
    }
  }

  public void moveAntsOnTargets(Set<Tile> targets, int maxAnts, int maxDistance) {
    PriorityQueue<Route> possibleRoutes = new PriorityQueue<Route>();
    Map<Tile,Set<Route>> antsOnTarget = new HashMap<Tile,Set<Route>>();
    Route route = null;
    Set<Tile> tmpOrders = new HashSet<Tile>();

    // System.err.println("my ants: " + ants.getMyAnts());
    // System.err.println("orders: " + orders);
    for (Tile foodLoc : targets) {
      for (Tile antLoc : ants.getMyAnts()) {
        if (orders.containsValue(antLoc)) { continue; }

        List<Aim> directions = new Router(antLoc, foodLoc).directions(maxDistance);
        if (directions != null) {
          route = new Route(antLoc, foodLoc, directions);
          possibleRoutes.offer(route);
        }
      }
    }
    
    while ((route = possibleRoutes.poll()) != null) {
      if (maxAnts > 0 && antsOnTarget.containsKey(route.getEnd()) && antsOnTarget.get(route.getEnd()).size() >= maxAnts) { continue; }
      if (orders.containsValue(route.getStart())) { continue; }
      if (tmpOrders.contains(route.getStart())) { continue; }
      
      tmpOrders.add(route.getStart());
      Set<Route> routes = antsOnTarget.get(route.getEnd());
      if (routes == null) {
        routes = new HashSet<Route>();
        antsOnTarget.put(route.getEnd(), routes);
      }
      routes.add(route);
    }

    for (Iterator<Map.Entry<Tile,Set<Route>>> it = antsOnTarget.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Tile,Set<Route>> entry = it.next();
      Tile target          = entry.getKey();
      Set<Route> antRoutes = entry.getValue();
      
      // int max = 0;
      // for (Route andRoute : antRoutes) {
      //   max = Math.max(antRoutes.getDistance(), max);
      // }
      
      for (Route antRoute : antRoutes) {
        moveDirection(antRoute.getStart(), antRoute.getDirections().get(0));
      }
      //   if (route.getDistance() + 1 < max) {
      //     
      //   } else {
      //     
      //   }
      // }
      
    }
  }

  public void moveFoodAnts() {
    moveAntsOnTargets(ants.getFoodTiles(), 1, 10);
  }

  // public void moveSwarmAnts() {
  //   
  //   
  //   
  //   for (Tile antLoc : ants.getMyAnts()) {
  //     if (orders.containsValue(antLoc)) { continue; }
  //     
  //     if (squares.getValue(Agent.ENEMY_ANTS, antLoc) > 0.2) {
  //       List<AimValue> directions = squares.getDirections(Agent.ENEMY_ANTS, antLoc);
  // 
  //       Tile loc = antLoc;
  // 
  //       List<AimValue> path = new ArrayList<AimValue>();
  //       path.add(directions.get(0));
  //       while (path.get(path.size() - 1).value < Agent.ENEMY_ANTS.getValue()) {
  //         AimValue direction = squares.getDirections(Agent.ENEMY_ANTS, loc).get(0);
  //         if (direction.aim != null) {
  //           loc = ants.getTile(loc, direction.aim);
  //           if (ants.getDistance(antLoc, loc) > ants.getViewRadius2()) {
  //             return;
  //           }
  //         } else {
  //           return;
  //         }
  //         path.add(direction);
  //       }
  //       
  //       // System.err.println(antLoc + " " + squares.getValue(Agent.ENEMY_ANTS, antLoc) + " path " + path + " " + squares.getValue(Agent.MY_ANTS, loc));
  //       if (25 >= ants.getDistance(antLoc, loc) && squares.getValue(Agent.MY_ANTS, loc) < squares.getValue(Agent.ENEMY_ANTS, antLoc) * 1.6) {
  //         directions = squares.getDirections(Agent.MY_HILLS, antLoc);
  //       }
  //       for (AimValue direction : directions) {
  //         if (moveDirection(antLoc, direction.aim)) {
  //           // System.err.println(" -> " + direction.aim);
  //           break;
  //         }
  //       }
  //     }
  //   }
  // }
  
  public void moveExploreAnts() {
    for (Tile antLoc : ants.getMyAnts()) {
      if (orders.containsValue(antLoc)) { continue; }
      
      List<AimValue> directions = squares.getDirections(Agent.EXPLORE, antLoc);
      System.err.println(antLoc + " " + directions);
      for (AimValue direction : directions) {
        // would taking the first step kill me?
        Tile locTile = direction.aim == null ? antLoc : ants.getTile(antLoc, direction.aim);
        if (combatValues.willDie(locTile, 0)) {
          System.err.println("will die: " + direction.aim);
          break;
        }

        // would taking the second step kill me?
        AimValue direction2 = squares.getDirections(Agent.EXPLORE, locTile).get(0);
        Tile locTile2 = direction2.aim == null ? locTile : ants.getTile(locTile, direction2.aim);
        if (combatValues.willDie(locTile2, 0)) {
          System.err.println("will die: " + direction.aim + direction2.aim);
          break;
        }
        
        if (moveDirection(antLoc, direction.aim)) {
          System.err.println(" -> " + direction.aim);
          break;
        }
      }
      if (orders.containsValue(antLoc)) { continue; }

      Collections.reverse(directions);
      for (AimValue direction : directions) {
        if (moveDirection(antLoc, direction.aim)) {
          System.err.println(" -> " + direction.aim);
          break;
        }
      }
      
    }
  }

  public void moveDefenseAnts() {
    Set<Tile> defenseTiles  = new HashSet<Tile>();
    for (Tile myHill : ants.getMyHills()) {
      for (Tile offset : defenseOffsets) {
        Tile loc = ants.getTile(myHill, offset);
        if (!waterTiles.contains(loc)) {
          defenseTiles.add(loc);
        }
      }
    }
    System.err.println("defenseTiles: " + defenseTiles);
    
    List<Tile> vipAnts = new ArrayList<Tile>(ants.getMyHills());
    while (!vipAnts.isEmpty()) {
      Tile antLoc = vipAnts.remove(0);

      if (!ants.getMyAnts().contains(antLoc)) { continue; }
      if (orders.containsValue(antLoc)) { continue; }

      if (defenseTiles.contains(antLoc) && squares.getValue(Agent.ENEMY_ANTS, antLoc) > 0.1) {
        List<Aim> directions = Arrays.asList(Aim.values());
        Collections.shuffle(directions);
        for (Aim aim : directions) {
          Tile aimLoc = ants.getTile(antLoc, aim);
          if (defenseTiles.contains(aimLoc)) {
            if (moveDirection(antLoc, aim)) {
              vipAnts.add(ants.getTile(antLoc, aim));
              break;
            }
          }
        }
        if (orders.containsValue(antLoc)) { continue; }

        for (Aim aim : directions) {
          Tile aimLoc = ants.getTile(antLoc, aim);
          if (!ants.getMyHills().contains(aimLoc) && moveDirection(antLoc, aim)) {
            vipAnts.add(ants.getTile(antLoc, aim));
            break;
          }
        }
        if (orders.containsValue(antLoc)) { continue; }
      } else {
        for (AimValue direction : squares.getDirections(Agent.EXPLORE, antLoc)) {
          if (direction != null && moveDirection(antLoc, direction.aim)) {
            // System.err.println(" -> " + aim);
            break;
          }
          // System.err.println("");
        }
      }
    }
    
    for (Tile antLoc : defenseTiles) {
      if (!ants.getMyAnts().contains(antLoc)) { continue; }
      if (orders.containsValue(antLoc)) { continue; }
      
      if (squares.getValue(Agent.ENEMY_ANTS, antLoc) > 0.1) {
        moveDirection(antLoc, null);
      }
    }
  }

  public void doTurn() {
    combatValues = new CombatValues();
    
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      squares.diffuse(Agent.EXPLORE);
      squares.diffuse(Agent.ENEMY_ANTS);
    }
    // System.err.println("diffusion: " + (System.currentTimeMillis() - t0));
    //squares.print(Agent.ENEMY_ANTS);
    // for (int r = 0; r < ants.getRows(); r++) {
    //   for (int c = 0; c < ants.getCols(); c++) {
    //     System.err.print(ants.visible[r][c] ? '+' : ' ');
    //   }
    //   System.err.println();
    // }
    
    
    t0 = System.currentTimeMillis();
    moveDefenseAnts();
    System.err.println("moveDefenseAnts: " + (System.currentTimeMillis() - t0));

    // t0 = System.currentTimeMillis();
    // moveSwarmAnts();
    // System.err.println("moveSwarmAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    moveFoodAnts();
    System.err.println("moveFoodAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    moveExploreAnts();
    System.err.println("createExploreAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    
    System.err.println("-------------" + ants.getTimeRemaining());
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