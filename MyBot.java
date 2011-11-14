import java.util.*;
import java.io.IOException;

/**
* Starter bot implementation.
*/
public class MyBot extends Bot {
  Map<Tile, Tile> orders = new HashMap<Tile, Tile>();
  Set<Tile> enemyHills  = new HashSet<Tile>();
  Set<Tile> waterTiles  = new HashSet<Tile>();
  Map<Tile, DoraAnt> doraRoutes = new HashMap<Tile, DoraAnt>();
  Random random;
  List<List<Aim>> doras = new ArrayList<List<Aim>>();

  Map<Tile, List<Attacker>> enemyAttackers = new HashMap<Tile, List<Attacker>>();
  Map<Tile, List<Front>> myFronts          = new HashMap<Tile, List<Front>>();

  Set<Tile> defenseTiles = new HashSet<Tile>();
  boolean defend = false;

  enum Agent {
    MY_ANTS(90),
    ENEMY_ANTS(100),
    EXPLORE(100),
    MY_HILLS(1000),
    ENEMY_HILLS(1000);
    
    private final double value;
    Agent(double value) {
      this.value = value;
    }
    
    public double getValue() {
      return this.value;
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
          if (enemyHills.contains(this)) {
            values[agent.ordinal()] = 1000;
          } else {
            // if (enemyHills.isEmpty()) {
              if (!ants.isVisible(this)) {
                values[agent.ordinal()] = seenOnTurn == -1 ? 100 : (turn - seenOnTurn);
              } else {
                this.seenOnTurn = turn;
              }
            // }
          }
          break;
        case MY_ANTS:
          if (ants.getMyAnts().contains(this)) {
            values[agent.ordinal()] = agent.getValue();
          }
          break;
        case ENEMY_ANTS:
          if (ants.getEnemyAnts().contains(this)) {
            values[agent.ordinal()] = agent.getValue();
          }
          break;
        case MY_HILLS:
          if (ants.getMyHills().contains(this)) {
            values[agent.ordinal()] = agent.getValue();
          }
          break;
        case ENEMY_HILLS: 
          if (enemyHills.contains(this)) {
            values[agent.ordinal()] = agent.getValue();
          }
          break;
      }
      // System.err.println(agent + " " + values[agent.ordinal()]);
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
    
    doras.add(Arrays.asList(Aim.NORTH, Aim.WEST,  Aim.SOUTH, Aim.EAST));
    doras.add(Arrays.asList(Aim.WEST,  Aim.SOUTH, Aim.EAST,  Aim.NORTH));
    doras.add(Arrays.asList(Aim.SOUTH, Aim.EAST,  Aim.NORTH, Aim.WEST));
    doras.add(Arrays.asList(Aim.EAST,  Aim.NORTH, Aim.WEST,  Aim.SOUTH));
    doras.add(Arrays.asList(Aim.NORTH, Aim.EAST,  Aim.SOUTH, Aim.WEST));
    doras.add(Arrays.asList(Aim.EAST,  Aim.SOUTH, Aim.WEST,  Aim.NORTH));
    doras.add(Arrays.asList(Aim.SOUTH, Aim.WEST,  Aim.NORTH, Aim.EAST));
    doras.add(Arrays.asList(Aim.WEST,  Aim.NORTH, Aim.EAST,  Aim.SOUTH));

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
    for (Iterator<Tile> it = enemyHills.iterator(); it.hasNext(); ) {
      Tile enemyHill = it.next();
      if (ants.isVisible(enemyHill) && !ants.getEnemyHills().contains(enemyHill)) {
        it.remove();
      }
    }
    waterTiles.addAll(ants.getWaterTiles());
    enemyHills.addAll(ants.getEnemyHills());

    // updateWater();
    updateFronts();
    
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

  private boolean moveAlongRoute(Route route) {
    Tile antLoc = route.getStart();
    Aim  direction = route.getDirections().get(0);
    
    Tile newLoc = ants.getTile(antLoc, direction);
    if (waterTiles.contains(newLoc)) {
      return false;
    }

    if (ants.getIlk(newLoc).isUnoccupied() && !orders.containsKey(newLoc)) {
      ants.issueOrder(antLoc, direction);
      orders.put(newLoc, antLoc);
      
      route.setStart(newLoc);
      route.getDirections().remove(0);
    } else {
      orders.put(antLoc, antLoc);
    }
    
    return true;
  }

  // public void moveFoodAnts() {
  //   Map<Tile, Route> newFoodRoutes = new HashMap<Tile, Route>();
  //   
  //   for (Tile foodLoc : foodRoutes.keySet()) {
  //     Route route = foodRoutes.get(foodLoc);
  // 
  //     if (route.getStart() == route.getEnd()) {
  //       // System.err.println("destination reached: " + route.getStart());
  //       continue;
  //     }
  //     
  //     if (!ants.getMyAnts().contains(route.getStart())) {
  //       // System.err.println("food ant no longer exists: " + route.getStart());
  //       continue;
  //     }
  //     
  //     if (!ants.getFoodTiles().contains(route.getEnd())) {
  //       // System.err.println("food no longer exists: " + route.getEnd());
  //       continue;
  //     }
  //     
  //     if (!moveAlongRoute(route)) {
  //       // System.err.println("bad route: " + route.getStart());
  //       continue;
  //     }
  //     
  //     newFoodRoutes.put(foodLoc, route);
  //   }
  //   
  //   foodRoutes = newFoodRoutes;
  // }

  public void moveAntsOnTargets(Set<Tile> targets, int maxAnts, int maxDistance) {
    PriorityQueue<Route> possibleRoutes = new PriorityQueue<Route>();
    Map<Tile,Set<Route>> antsOnTarget = new HashMap<Tile,Set<Route>>();
    Route route = null;
    Set<Tile> tmpOrders = new HashSet<Tile>();
    
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

  class DoraAnt {
    private List<Aim> directions;
    private Tile lastLoc;
    private Tile antLoc;
    private int waiting;
    
    public DoraAnt(Tile tile) {
      this.antLoc = tile;
      this.waiting = 0;
      this.lastLoc = null;
      this.directions = doras.get(random.nextInt(doras.size()));
    }
    
    public Tile moveAnt() {
      if (!ants.getMyAnts().contains(antLoc)) {
        // System.err.println("dora ant no longer exists: " + antLoc);
        return null;
      }
      
      if (orders.containsValue(antLoc)) { 
        // System.err.println("ant no longer dora: " + antLoc);
        return null;
      }

      if (random.nextInt(10) < waiting - 1) {
        // System.err.println("abandon strategy: " + antLoc);

        this.waiting = 0;
        this.lastLoc = null;
        this.directions = doras.get(random.nextInt(doras.size()));
      }
      
      Aim lastLocDirection = null;
      for (Aim direction : directions) {
        Tile newLoc = ants.getTile(antLoc, direction);
        if (waterTiles.contains(newLoc) || ants.getMyHills().contains(newLoc)) { continue; }
        
        if (newLoc.equals(this.lastLoc)) {
          // System.err.println("avoid dora going back: " + direction);
          lastLocDirection = direction;
          continue;
        }

        if (ants.getIlk(newLoc).isUnoccupied() && !orders.containsKey(newLoc)) {
          ants.issueOrder(antLoc, direction);
          orders.put(newLoc, antLoc);
          
          lastLoc = antLoc;
          antLoc = newLoc;
          waiting = 0;
          return antLoc;
        }

        break;
      }
      
      if (lastLocDirection != null) {
        Tile newLoc = ants.getTile(antLoc, lastLocDirection);

        if (ants.getIlk(newLoc).isUnoccupied() && !orders.containsKey(newLoc)) {
          ants.issueOrder(antLoc, lastLocDirection);
          orders.put(newLoc, antLoc);

          lastLoc = antLoc;
          antLoc = newLoc;
          waiting = 0;
          return antLoc;
        }
      }
      
      waiting = waiting + 1;
      orders.put(antLoc, antLoc);
      return antLoc;
    }
    
    public String toString() {
      return antLoc + " " + Arrays.toString(directions.toArray()) + " " + lastLoc;
    }
  }

  public void createDoraAnts() {
    for (Tile antLoc : ants.getMyAnts()) {
      if (orders.containsValue(antLoc)) { continue; }
      if (doraRoutes.containsKey(antLoc)) { continue; }

      DoraAnt doraAnt = new DoraAnt(antLoc);
      // System.err.println("creating dora ant: " + doraAnt);
      doraRoutes.put(antLoc, doraAnt);
    }    
  }

  public void moveSwarmAnts() {
    for (Tile antLoc : ants.getMyAnts()) {
      if (orders.containsValue(antLoc)) { continue; }
      
      if (squares.getValue(Agent.ENEMY_ANTS, antLoc) > 0.2) {
        List<AimValue> directions = squares.getDirections(Agent.ENEMY_ANTS, antLoc);

        Tile loc = antLoc;

        List<AimValue> path = new ArrayList<AimValue>();
        path.add(directions.get(0));
        while (path.get(path.size() - 1).value < Agent.ENEMY_ANTS.getValue()) {
          AimValue direction = squares.getDirections(Agent.ENEMY_ANTS, loc).get(0);
          if (direction.aim != null) {
            loc = ants.getTile(loc, direction.aim);
            if (ants.getDistance(antLoc, loc) > ants.getViewRadius2()) {
              return;
            }
          } else {
            return;
          }
          path.add(direction);
        }
        
        // System.err.println(antLoc + " " + squares.getValue(Agent.ENEMY_ANTS, antLoc) + " path " + path + " " + squares.getValue(Agent.MY_ANTS, loc));
        if (25 >= ants.getDistance(antLoc, loc) && squares.getValue(Agent.MY_ANTS, loc) < squares.getValue(Agent.ENEMY_ANTS, antLoc) * 1.6) {
          directions = squares.getDirections(Agent.MY_HILLS, antLoc);
        }
        for (AimValue direction : directions) {
          if (moveDirection(antLoc, direction.aim)) {
            // System.err.println(" -> " + direction.aim);
            break;
          }
        }
      }
    }
  }
  
  public void moveExploreAnts() {
    for (Tile antLoc : ants.getMyAnts()) {
      if (orders.containsValue(antLoc)) { continue; }
      
      List<AimValue> directions = squares.getDirections(Agent.EXPLORE, antLoc);
      // System.err.println(antLoc + " " + directions);
      for (AimValue direction : directions) {
        if (moveDirection(antLoc, direction.aim)) {
          // System.err.println(" -> " + direction.aim);
          break;
        }
        // System.err.println("");
      }
    }
  }

  public void moveDoraAnts() {
    Map<Tile, DoraAnt> newDoraRoutes = new HashMap<Tile, DoraAnt>();
    
    for (Map.Entry<Tile,DoraAnt> entry : doraRoutes.entrySet()) {
      Tile antLoc     = entry.getKey();
      DoraAnt doraAnt = entry.getValue();
      
      Tile newLoc = doraAnt.moveAnt();
      if (newLoc != null) {
        newDoraRoutes.put(newLoc, doraAnt);
      }
    }
    
    doraRoutes = newDoraRoutes;
  }

  public void updateFronts() {
    for (Tile myHill : ants.getMyHills()) {
      if (!myFronts.containsKey(myHill)) {
        myFronts.put(myHill, new ArrayList<Front>());
        
        for (int r = -1; r <= 1; r++) {
          for (int c = -1; c <= 1; c++) {
            Tile defLoc = ants.getTile(myHill, new Tile(r, c));
            if (!waterTiles.contains(defLoc)) {
              defenseTiles.add(defLoc);
            }
          }
        }
        // System.err.println("adding front for: " + myHill);
      }
    }

    for (Iterator<Map.Entry<Tile, List<Front>>> it = myFronts.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Tile, List<Front>> entry = it.next();
      Tile myHill = entry.getKey();

      if (!ants.getMyHills().contains(myHill)) {
        it.remove();
        
        for (int r = -1; r <= 1; r++) {
          for (int c = -1; c <= 1; c++) {
            Tile defLoc = ants.getTile(myHill, new Tile(r, c));
            if (!waterTiles.contains(defLoc)) {
              defenseTiles.remove(defLoc);
            }
          }
        }
        
        continue;
      }
      
      List<Front> fronts = entry.getValue();
      for (Tile enemyHill : enemyHills) {
        boolean hasFront = false;
        for (Iterator<Front> it2 = fronts.iterator(); it2.hasNext(); ) {
          Front front = it2.next();
          if (front.getEnemyHill().equals(enemyHill)) {
            hasFront = true;
            break;
          }
        }
        if (!hasFront) {
          Front front = new Front(myHill, enemyHill);
          fronts.add(front);
          // System.err.println("adding front: " + front);
        }
      }

      for (Iterator<Front> it2 = fronts.iterator(); it2.hasNext(); ) {
        Front front = it2.next();
        
        if (!enemyHills.contains(front.getEnemyHill())) {
          it2.remove();
          // System.err.println("removing front: " + front);
          continue;
        }
      }
    }
  }

  class Attacker {
    Route route = null;
    Route initialRoute = null;
    
    public Attacker(Route route) {
      this(route, null);
    }
    
    public Attacker(Route route, Route initialRoute) {
      this.route        = route;
      this.initialRoute = initialRoute;
    }
  }

  public void createAttackAnts() {
    for (Tile myHill : ants.getMyHills()) {
      if (ants.getMyAnts().contains(myHill)) {
        List<Front> fronts = myFronts.get(myHill);
        if (fronts.isEmpty()) { continue; }
        
        PriorityQueue<Front> sortedFronts = new PriorityQueue<Front>(fronts);
        Front front = sortedFronts.peek();
        List<Attacker> attackers = enemyAttackers.get(front.getEnemyHill());
        if (attackers == null) { 
          attackers = new ArrayList<Attacker>();
          enemyAttackers.put(front.getEnemyHill(), attackers);
        }
        attackers.add(front.createAttacker());
      }
    }
  }

  public void moveAttackAnts() {
    for (Iterator<Map.Entry<Tile, List<Attacker>>> it = enemyAttackers.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Tile, List<Attacker>> entry = it.next();
      Tile enemyHill           = entry.getKey();
      List<Attacker> attackers = entry.getValue();
      
      if (!ants.getEnemyHills().contains(enemyHill)) {
        it.remove();
        continue;
      }
      
      for (Iterator<Attacker> it2 = attackers.iterator(); it2.hasNext(); ) {
        Attacker attacker = it2.next();
        
        if (!ants.getMyAnts().contains(attacker.route.getStart())) {
          it2.remove();
          continue;
        }
        
        if (!moveAlongRoute(attacker.route)) {
          if (attacker.initialRoute != null) {
            List<Front> fronts = myFronts.get(attacker.initialRoute.getStart());
            if (fronts != null) { // my hive died
              for (Front front : fronts) {
                front.invalidateRoute(attacker.initialRoute);
              }
            } else {
              // System.err.println("cannot invalidate initial route from: " + attacker.initialRoute);
            }
          }
          it2.remove();
        }
      }
    }
  }

  public void moveDefenseAnts() {
    List<Tile> vipAnts = new ArrayList<Tile>(ants.getMyHills());
    while (!vipAnts.isEmpty()) {
      Tile antLoc = vipAnts.remove(0);

      if (!ants.getMyAnts().contains(antLoc)) { continue; }
      if (orders.containsValue(antLoc)) { continue; }

      if (defenseTiles.contains(antLoc) && squares.getValue(Agent.ENEMY_ANTS, antLoc) > 0.025) {
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
      
        for (Aim aim : Aim.values()) {
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
      
      if (squares.getValue(Agent.ENEMY_ANTS, antLoc) > 0.025) {
        moveDirection(antLoc, null);
      }
    }
  }

  public void doTurn() {
    long t0 = System.currentTimeMillis();
    for (int i = 0; i < 10; i++) {
      squares.diffuse(Agent.EXPLORE);
      squares.diffuse(Agent.ENEMY_ANTS);
      squares.diffuse(Agent.MY_ANTS);
      squares.diffuse(Agent.MY_HILLS);
      // squares.diffuse(Agent.ENEMY_HILLS);
    }
    // System.err.println("diffusion: " + (System.currentTimeMillis() - t0));
    
    // squares.print(Agent.EXPLORE);
    // for (int r = 0; r < ants.getRows(); r++) {
    //   for (int c = 0; c < ants.getCols(); c++) {
    //     System.err.print(ants.visible[r][c] ? '+' : ' ');
    //   }
    //   System.err.println();
    // }
    
    
    t0 = System.currentTimeMillis();
    moveDefenseAnts();
    // System.err.println("createAttackAnts: " + (System.currentTimeMillis() - t0));

    t0 = System.currentTimeMillis();
    // moveAttackAnts();
    // System.err.println("moveAttackAnts: " + (System.currentTimeMillis() - t0));

    t0 = System.currentTimeMillis();
    moveSwarmAnts();
    // System.err.println("moveSwarmAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    moveFoodAnts();
    // System.err.println("moveFoodAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    moveExploreAnts();
    // createDoraAnts();
    // System.err.println("createExploreAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    // moveDoraAnts();
    // System.err.println("moveDoraAnts: " + (System.currentTimeMillis() - t0));
    
    // System.err.println("-------------" + ants.getTimeRemaining());
    turn++;
  }
  
  class Front implements Comparable<Front> {
    Tile myHill;
    Tile enemyHill;
    List<Route> routes = new ArrayList<Route>();
    
    public Front(Tile myHill, Tile enemyHill) {
      this.myHill    = myHill;
      this.enemyHill = enemyHill;

      addRoute();
    }
    
    public void addRoute() {
      List<Aim> directions = new Router(myHill, enemyHill).directions();
      if (directions != null) { 
        Route route = new Route(myHill, enemyHill, directions);
        // System.err.println("adding front route: " + route);
        routes.add(route);
      }
    }

    public void invalidateRoute(Route route) {
      if (routes.remove(route)) {
        addRoute();
      }
    }

    public Attacker createAttacker() {
      Route initialRoute = routes.get(random.nextInt(routes.size()));
      return new Attacker((Route) initialRoute.clone(), initialRoute);
    }
        
    public Tile getEnemyHill() {
      return this.enemyHill;
    }
    
    public Tile getMyHill() {
      return this.myHill;
    }
    
    public int compareTo(Front front) {
      if (routes.isEmpty() && front.routes.isEmpty()) { return 0; } 
      if (routes.isEmpty()) { return 1; }
      if (front.routes.isEmpty()) { return -1; }
      return routes.get(0).compareTo(front.routes.get(0));
    }
    
    public String toString() {
      return myHill + " -> " + enemyHill;
    }
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