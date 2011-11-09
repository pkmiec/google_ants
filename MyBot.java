import java.util.*;
import java.io.IOException;

/**
* Starter bot implementation.
*/
public class MyBot extends Bot {
  Map<Tile, Tile> orders = new HashMap<Tile, Tile>();
  Set<Tile>  enemyHills  = new HashSet<Tile>();
  Set<Tile>  waterTiles  = new HashSet<Tile>();
  Map<Tile, Route> foodRoutes  = new HashMap<Tile, Route>();
  Map<Tile, List<Aim>> doraRoutes = new HashMap<Tile, List<Aim>>();
  Random random;
  
  List<List<Aim>> doras = new ArrayList<List<Aim>>();

  public static void main(String[] args) throws IOException {
    new MyBot().readSystemInput();
  }
  
  Map<Tile, List<Front>> allFronts = new HashMap<Tile, List<Front>>();
  
  public void setup(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2, int attackRadius2, int spawnRadius2) {
    super.setup(loadTime, turnTime, rows, cols, turns, viewRadius2, attackRadius2, spawnRadius2);
    random = new Random(1);
    
    doras.add(Arrays.asList(Aim.NORTH, Aim.WEST,  Aim.SOUTH, Aim.EAST));
    doras.add(Arrays.asList(Aim.WEST,  Aim.SOUTH, Aim.EAST,  Aim.NORTH));
    doras.add(Arrays.asList(Aim.SOUTH, Aim.EAST,  Aim.NORTH, Aim.WEST));
    doras.add(Arrays.asList(Aim.EAST,  Aim.NORTH, Aim.WEST,  Aim.SOUTH));
    doras.add(Arrays.asList(Aim.NORTH, Aim.EAST,  Aim.SOUTH, Aim.WEST));
    doras.add(Arrays.asList(Aim.EAST,  Aim.SOUTH, Aim.WEST,  Aim.NORTH));
    doras.add(Arrays.asList(Aim.SOUTH, Aim.WEST,  Aim.NORTH, Aim.EAST));
    doras.add(Arrays.asList(Aim.WEST,  Aim.NORTH, Aim.EAST,  Aim.SOUTH));
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
    enemyHills.addAll(ants.getEnemyHills());
    waterTiles.addAll(ants.getWaterTiles());
    updateFronts();
    // System.err.println("afterUpdate: " + (System.currentTimeMillis() - t0));
  }
  
  private boolean doMoveLocation(Tile antLoc, Tile destLoc) {
    Ants ants = getAnts();
    // Track targets to prevent 2 ants to the same location
    List<Aim> directions = new Router(antLoc, destLoc).directions();
    if (directions != null) {
      // System.err.println("direction: " + antLoc + " -> " + destLoc);
      // System.err.println(Arrays.toString(directions.toArray()));
      return doMoveDirection(antLoc, directions.get(0)); 
    }
    return false;
  }

  private boolean doMoveDirection(Tile antLoc, Aim direction) {
    Ants ants = getAnts();
    
    Tile newLoc = ants.getTile(antLoc, direction);
    if (ants.getIlk(newLoc).isUnoccupied() && !orders.containsKey(newLoc)) {
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
    if (ants.getIlk(newLoc) == Ilk.WATER) {
      return false;
    }

    if (ants.getIlk(newLoc).isUnoccupied() && !orders.containsKey(newLoc)) {
      ants.issueOrder(antLoc, direction);
      orders.put(newLoc, antLoc);
      
      route.setStart(newLoc);
      route.getDirections().remove(0);
    }
    
    return true;
  }

  public void moveFoodAnts() {
    Map<Tile, Route> newFoodRoutes = new HashMap<Tile, Route>();
    
    for (Tile foodLoc : foodRoutes.keySet()) {
      Route route = foodRoutes.get(foodLoc);

      if (route.getStart() == route.getEnd()) {
        // System.err.println("destination reached: " + route.getStart());
        continue;
      }
      
      if (!ants.getMyAnts().contains(route.getStart())) {
        // System.err.println("food ant no longer exists: " + route.getStart());
        continue;
      }
      
      if (!ants.getFoodTiles().contains(route.getEnd())) {
        // System.err.println("food no longer exists: " + route.getEnd());
        continue;
      }
      
      if (!moveAlongRoute(route)) {
        // System.err.println("bad route: " + route.getStart());
        continue;
      }
      
      newFoodRoutes.put(foodLoc, route);
    }
    
    foodRoutes = newFoodRoutes;
  }

  public void createFoodAnts() {
    List<Route> newFoodRoutes = new ArrayList<Route>();
    for (Tile foodLoc : ants.getFoodTiles()) {
      if (foodRoutes.containsKey(foodLoc)) { continue; }
      
      for (Tile antLoc : ants.getMyAnts()) {
        if (orders.containsValue(antLoc)) { continue; }
        
        List<Aim> route = new Router(antLoc, foodLoc).directions();
        if (route != null) {
          newFoodRoutes.add(new Route(antLoc, foodLoc, route));
        }
      }
    }
    Collections.sort(newFoodRoutes);
    for (Route route : newFoodRoutes) {
      if (foodRoutes.containsKey(route.getEnd())) { continue; }
      if (orders.containsValue(route.getStart())) { continue; }
      
      if (moveAlongRoute(route)) {
        // System.err.println("adding food route: " + route);
        foodRoutes.put(route.getEnd(), route);
      }
    }
  }

  public void createDoraAnts() {
    for (Tile antLoc : ants.getMyAnts()) {
      if (orders.containsValue(antLoc)) { continue; }
      if (doraRoutes.containsKey(antLoc)) { continue; }

      int routeIndex = random.nextInt(doras.size());
      // System.err.println("adding dora route: " + antLoc + " " + Arrays.toString(((List<Aim>) DORA_ROUTES[routeIndex]).toArray()));
      doraRoutes.put(antLoc, doras.get(routeIndex));
    }    
  }

  public void moveDoraAnts() {
    Map<Tile, List<Aim>> newDoraRoutes = new HashMap<Tile, List<Aim>>();
    
    for (Map.Entry<Tile,List<Aim>> entry : doraRoutes.entrySet()) {
      Tile antLoc     = entry.getKey();
      List<Aim> route = entry.getValue();
      
      if (!ants.getMyAnts().contains(antLoc)) {
        // System.err.println("dora ant no longer exists: " + antLoc);
        continue;
      }
      
      if (orders.containsValue(antLoc)) { 
        // System.err.println("ant no longer dora: " + antLoc);
        continue; 
      }
      
      for (Aim direction : route) {
        Tile newLoc = ants.getTile(antLoc, direction);
        if (waterTiles.contains(newLoc) || ants.getMyHills().contains(newLoc)) { continue; }

        if (ants.getIlk(newLoc).isUnoccupied() && !orders.containsKey(newLoc)) {
          ants.issueOrder(antLoc, direction);
          orders.put(newLoc, antLoc);
        }

        newDoraRoutes.put(newLoc, route);
        break;
      }
    }
    
    doraRoutes = newDoraRoutes;
  }

  public void updateFronts() {
    for (Tile myHill : ants.getMyHills()) {
      if (!allFronts.containsKey(myHill)) {
        allFronts.put(myHill, new ArrayList<Front>());
        // System.err.println("adding front for: " + myHill);
      }
    }

    for (Iterator<Map.Entry<Tile, List<Front>>> it = allFronts.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Tile, List<Front>> entry = it.next();
      Tile myHill = entry.getKey();

      if (!ants.getMyHills().contains(myHill)) {
        it.remove();
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

  public void createAttackAnts() {
    // if (ants.getMyAnts().size() > 5) {
      for (Tile myHill : ants.getMyHills()) {
        if (ants.getMyAnts().contains(myHill)) {
          List<Front> fronts = allFronts.get(myHill);
          if (fronts.size() > 0) {
            Collections.sort(fronts);
            fronts.get(0).addAnt();
          }
        }
      }
    // }
  }

  public void moveAttackAnts() {
    for (Iterator<Map.Entry<Tile, List<Front>>> it = allFronts.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<Tile, List<Front>> entry = it.next();
      Tile myHill = entry.getKey();
      List<Front> fronts = entry.getValue();
      
      for (Iterator<Front> it2 = fronts.iterator(); it2.hasNext(); ) {
        Front front = it2.next();
        front.moveAnts();
      }
    }
  }

  public void doTurn() {
    long t0 = System.currentTimeMillis();
    createAttackAnts();
    // System.err.println("createAttackAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    moveFoodAnts();
    // System.err.println("moveFoodAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    moveAttackAnts();
    // System.err.println("moveAttackAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    createFoodAnts();
    // System.err.println("createFoodAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    createDoraAnts();
    // System.err.println("createDoraAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    moveDoraAnts();
    // System.err.println("moveDoraAnts: " + (System.currentTimeMillis() - t0));

    // // attack hills
    // List<Route> hillRoutes = new ArrayList<Route>();
    // for (Tile hillLoc : enemyHills) {
    //   for (Tile antLoc : sortedAnts) {
    //     if (!orders.containsValue(antLoc)) {
    //       List<Aim> route = new Router(antLoc).directionsTo(hillLoc);
    //       if (route != null) {
    //         hillRoutes.add(new Route(antLoc, hillLoc, route));
    //       }
    //     }
    //   }
    // }
    // Collections.sort(hillRoutes);
    // for (Route route : hillRoutes) {
    //   doMoveDirection(route.getStart(), route.getAim());
    // }
    // 
    // // explore unseen areas
    // for (Tile antLoc : sortedAnts) {
    //   if (!orders.containsValue(antLoc)) {
    //     // List<Route> unseenRoutes = new ArrayList<Route>();
    //     Tile unseenLoc = (Tile) unseenTiles.toArray()[((int)(Math.random() * unseenTiles.size()))];
    //     
    //     // for (Tile unseenLoc : unseenTiles) {
    //       List<Aim> route = new Router(antLoc).directionsTo(unseenLoc);
    //       if (route != null) {
    //         doMoveDirection(antLoc, route.get(0));
    //         // unseenRoutes.add(new Route(antLoc, unseenLoc, route));
    //       }
    //     // }
    //     // Collections.sort(unseenRoutes);
    //     // for (Route route : unseenRoutes) {
    //     //   if (doMoveLocation(route.getStart(), route.getEnd())) {
    //     //     break;
    //     //   }
    //     // }
    //   }
    // }
    // 
    // // unblock       
    // for (Tile myHill : ants.getMyHills()) {
    //   if (ants.getMyAnts().contains(myHill) && !orders.containsValue(myHill)) {
    //     for (Aim direction : Aim.values()) {
    //       if (doMoveDirection(myHill, direction)) {
    //         break;
    //       }
    //     }
    //   }
    // }
    
    // System.err.println("------------------------------");
  }
  
  class Front implements Comparable<Front> {
    Tile enemyHill;
    Tile myHill;
    List<Route> routes     = new ArrayList<Route>();
    Map<Route, Route> ants = new HashMap<Route, Route>();
    
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
    
    public void addAnt() {
      if (routes.isEmpty()) { return; }
      
      Route chosenRoute = routes.get(random.nextInt(routes.size()));
      ants.put((Route) chosenRoute.clone(), chosenRoute);
      // System.err.println("create attack ant: " + chosenRoute);
    }
    
    public void moveAnts() {
      Map<Route, Route> newAnts = new HashMap<Route, Route>();
      
      for (Route antRoute : ants.keySet()) {
        Route route = ants.get(antRoute);
        
        if (!getAnts().getMyAnts().contains(antRoute.getStart())) {
          // System.err.println("attack ant no longer exists: " + antRoute.getStart());
          continue;
        }

        if (!moveAlongRoute(antRoute)) {
          // System.err.println("bad attack route: " + antRoute.getStart());
          routes.remove(route);
          addRoute();
          continue;
        }
        
        newAnts.put(antRoute, route);
      }
      
      ants = newAnts;
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
      hScore.put(from, (float) ants.getDistance(from, to));
      fScore.put(from, gScore.get(from) + hScore.get(from));
    }
    
    public List<Aim> directions() {
      Ants ants = getAnts();

      while (!openSet.isEmpty()) {
        fSorted.clear();
        for (Tile x: openSet) {
          fSorted.put(x, fScore.get(x));
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
            hScore.put(y, (float) ants.getDistance(y, to));
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