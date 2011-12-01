import java.util.*;
import java.io.IOException;
import java.util.logging.Level;

/**
* Starter bot implementation.
*/
public class MyBot extends Bot {
  public static Level logLevel = Level.OFF;
  
  public void logFine(String str) {
    if (logLevel.intValue() <= Level.FINE.intValue()) {
      System.err.println("FINE: " + str);
    } 
  }

  public void logFiner(String str) {
    if (logLevel.intValue() <= Level.FINER.intValue()) {
      System.err.println("FINER: " + str);
    } 
  }

  public void logFinest(String str) {
    if (logLevel.intValue() <= Level.FINEST.intValue()) {
      System.err.println("FINEST: " + str);
    } 
  }
  
  Random random;

  Map<Tile, Tile> orders = new HashMap<Tile, Tile>();
  Set<Tile> waterTiles  = new HashSet<Tile>();
  Set<Tile> enemyHills  = new HashSet<Tile>();
  Set<Tile> enemyDeadHills  = new HashSet<Tile>();
  Set<Tile> attackAnts = new HashSet<Tile>();
  int maxExploreAnts = 0;
  
  Map<Tile, Set<Tile>> combatAnts = null;
  
  enum Agent {
    EXPLORE,
    ATTACK,
    TACTICS;
  }

  class CombatValues {
    final Map<Tile, Integer> tilesAttackers;
    final Map<Tile, Integer> tilesDefenders;
    final Map<Tile, Integer> antsOwners;
    
    public CombatValues() {
      this(ants.getAnts());
    }
    
    public CombatValues(Map<Tile,Integer> antsOwners) {
      this.tilesAttackers = new HashMap<Tile, Integer>();
      this.tilesDefenders = new HashMap<Tile, Integer>();
      this.antsOwners = antsOwners;
    }
    
    public int getTileAttackers(Tile tile, int owner, Set<Tile> offsets) {
      if (tilesAttackers.containsKey(tile)) {
        return tilesAttackers.get(tile);
      }
      
      int tileAttackers = 0;
      for (Tile offset : offsets) {
        Tile loc = ants.getTile(tile, offset);
        Integer locOwner = antsOwners.get(loc);
            
        if (locOwner != null && locOwner != owner) {

          // An ant is only an attacker if it is in attack radius or can move into attack radius in one 
          // move (i.e. it is not blocked by water). This should allow me to sometimes attack through 
          // water.
          if (ants.getAttackOffsets().contains(offset) || canAntMoveToAttackTargetAnt(loc, tile)) {
            tileAttackers++;
          }
        }
      }
      tilesAttackers.put(tile, tileAttackers);
      return tileAttackers;
    }

    private boolean canAntMoveToAttackTargetAnt(Tile ant, Tile targetAnt) {
      Set<Tile> attackTiles = ants.getTiles(targetAnt, ants.getAttackOffsets());
      
      for (Aim aim: Aim.values()) {
        Tile loc = ants.getTile(ant, aim);
        if (attackTiles.contains(loc) && !waterTiles.contains(loc)) {
          return true;
        }
      }

      logFiner(ant + " cannot move to attack " + targetAnt);
      return false;
    }

    public int getTileDefenders(Tile tile, int owner, Set<Tile> offsets) {
      if (tilesDefenders.containsKey(tile)) {
        return tilesDefenders.get(tile);
      }
      
      int tileDefenders = 0;
      for (Tile offset : offsets) {
        Tile loc = ants.getTile(tile, offset);
        Integer locOwner = antsOwners.get(loc);
      
        if (locOwner != null && locOwner == owner) {
          tileDefenders++;
        }
      }
      tilesDefenders.put(tile, tileDefenders);
      return tileDefenders;
    }

    //
    //
    //
    //
    public boolean willDie(Tile tile, int owner) {
      return willDie(tile, owner, false);
    }
    
    public boolean willDie(Tile tile, int owner, boolean kamikaze) {
      int myAttackers = getTileAttackers(tile, owner, ants.getAggressionOffsets()); // ants attacking me
      int difference = myAttackers;
  
      if (myAttackers == 0) { return false; }
      
      for (Tile offset : ants.getAggressionOffsets()) {
        Tile loc = ants.getTile(tile, offset);
        Integer locOwner = antsOwners.get(loc);
    
        if (locOwner != null && locOwner != owner) {
          int locAttackers = getTileAttackers(loc, locOwner, ants.getAggressionOffsets()); // ants attacking them
          logFinest("attacking difference: " + myAttackers + " " + locAttackers);
          if (kamikaze) {
            if (myAttackers > locAttackers) { return true; }
          } else {
            if (myAttackers >= locAttackers) { return true; }
          }
        }
      }
      
      return false;
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

  class Square extends Tile {
    protected final double[] values;
    protected final boolean[] initValues;
    protected int seenOnTurn;
    
    public Square(int row, int col) {
      super(row, col);
      
      this.values     = new double[Agent.values().length];
      this.initValues = new boolean[Agent.values().length];
      this.seenOnTurn = -1;

      setValue(Agent.values(), 0.0);
    }

    public void initValue(Agent agent) {
      initValues[agent.ordinal()] = false;
      switch (agent) {
        case EXPLORE:
          if (!ants.isVisible(this)) {
            double value = 30;
            if (seenOnTurn == -1) {
              value = 50;
              if (enemyHills.size() == 0) {
                value += (turn / 10);
              }
            } else {
              value += (turn - seenOnTurn) * 2;
            }
            
            setInitValue(agent, value);
          } else {
            if (ants.getEnemyAnts().contains(this)) {
              setInitValue(agent, 25);
              
              for (Tile myHill: ants.getMyHills()) {
                if (combatAnts.get(myHill) != null && combatAnts.get(myHill).contains(this)) {
                  values[agent.ordinal()] += 20;
                }
              }
            } else if (ants.getMyAnts().contains(this)) {
              if (!combatAnts.containsKey(this)) {
                setInitValue(agent, 0);
              }
            }
            seenOnTurn = turn;
          }
          break;
        case ATTACK:
          if (enemyHills.contains(this)) {
            setInitValue(agent, 200);
          } else if (ants.getMyHills().contains(this)) {
            setInitValue(agent, 0);
          } else if (enemyDeadHills.contains(this)) {
            setInitValue(agent, 0);
          }
          
          seenOnTurn = turn;
          break;
        case TACTICS:
          // ?a?  ?..  ..?  ...
          // .x.  ax.  .xa  .x.
          // ...  ?..  ..?  ?a?
          //
          // .a.  .?.  .?.  .?.  
          // ?x?  ax?  ?xa  ?x?
          // .?.  .?.  .?.  .a.
          
          // blockage detection
          // ???  .??  .x.  ??.
          // ???  x??  ???  ??x
          // .x.  .??  ???  ??.

          if (!ants.getEnemyAnts().contains(this)) {
            if (ants.getEnemyAnts().contains(ants.getTile(this, Aim.WEST))) {
              if (!ants.getEnemyAnts().contains(ants.getTile(this, Aim.EAST))
                  && !ants.getEnemyAnts().contains(ants.getTile(this, Aim.NORTH))
                  && !ants.getEnemyAnts().contains(ants.getTile(this, Aim.SOUTH))) {
                Tile westTile = ants.getTile(this, Aim.WEST);
                if (ants.getEnemyAnts().contains(ants.getTile(westTile, Aim.NORTH)) 
                    || ants.getEnemyAnts().contains(ants.getTile(westTile, Aim.SOUTH))) {
                  setInitValue(agent, -12);
                } else {
                  setInitValue(agent, 25);
                }
              } else {
                setInitValue(agent, -12);
              }
            } else if (ants.getEnemyAnts().contains(ants.getTile(this, Aim.EAST))) {
              if (!ants.getEnemyAnts().contains(ants.getTile(this, Aim.WEST))
                  && !ants.getEnemyAnts().contains(ants.getTile(this, Aim.NORTH))
                  && !ants.getEnemyAnts().contains(ants.getTile(this, Aim.SOUTH))) { 
                Tile eastTile = ants.getTile(this, Aim.EAST);
                if (ants.getEnemyAnts().contains(ants.getTile(eastTile, Aim.NORTH)) 
                   || ants.getEnemyAnts().contains(ants.getTile(eastTile, Aim.SOUTH))) {
                  setInitValue(agent, -12);
                } else {
                  setInitValue(agent, 25);
                }
              } else {
                setInitValue(agent, -12);
              }
            } else if (ants.getEnemyAnts().contains(ants.getTile(this, Aim.NORTH))) {
              if (!ants.getEnemyAnts().contains(ants.getTile(this, Aim.EAST))
                  && !ants.getEnemyAnts().contains(ants.getTile(this, Aim.WEST))
                  && !ants.getEnemyAnts().contains(ants.getTile(this, Aim.SOUTH))) {
                Tile northTile = ants.getTile(this, Aim.NORTH);
                if (ants.getEnemyAnts().contains(ants.getTile(northTile, Aim.WEST)) 
                    || ants.getEnemyAnts().contains(ants.getTile(northTile, Aim.EAST))) {
                  setInitValue(agent, -12);
                } else {
                  setInitValue(agent, 25);
                
                }
              } else {
                setInitValue(agent, -12);
              }
            } else if (ants.getEnemyAnts().contains(ants.getTile(this, Aim.SOUTH))) {
              if (!ants.getEnemyAnts().contains(ants.getTile(this, Aim.EAST))
                  && !ants.getEnemyAnts().contains(ants.getTile(this, Aim.NORTH))
                  && !ants.getEnemyAnts().contains(ants.getTile(this, Aim.WEST))) {
                Tile southTile = ants.getTile(this, Aim.SOUTH);
                if (ants.getEnemyAnts().contains(ants.getTile(southTile, Aim.WEST)) 
                   || ants.getEnemyAnts().contains(ants.getTile(southTile, Aim.EAST))) {
                  setInitValue(agent, -12);
                } else {
                  setInitValue(agent, 25);
                }
              } else {
                setInitValue(agent, -12);
              }
            } else if (enemyHills.contains(this)) {
              setInitValue(agent, 50);
            }
          }
        
          // if (ants.getEnemyAnts().contains(this)) {
          //   // int numDefenders = defenders.getTileDefenders(this, ants.getAnts().get(this), ants.getAggressionOffsets());
          //   // if (numDefenders == 1) {
          //   //   setInitValue(agent, 35);
          //   // } else {
          //   //   setInitValue(agent, 10);
          //   // }
          //   for (Tile myHill: ants.getMyHills()) {
          //     if (combatAnts.get(myHill) != null && combatAnts.get(myHill).contains(this)) {
          //       setInitValue(agent, 20);
          //     }
          //   }
          // }
          break;

          
        case ENEMY_HILLS:
          if (enemyHills.contains(this)) {
            setInitValue(agent, 50);
          } else if (enemyDeadHills.contains(this)) {
            setInitValue(agent, 0);
          } else if (ants.getMyHills().contains(this)) {
            setInitValue(agent, 0);
          }
          break;
      }
    }
    
    public double getValue(final Agent agent) {
      return values[agent.ordinal()];
    }
    
    public double getValue(final Agent[] agent) {
      if (agent == null) {
        return getValue(Agent.values());
      } else {
        double sum = 0.0;
        for (Agent tmpAgent : agent) {
          sum += values[tmpAgent.ordinal()];
        }
        return sum;
      }
    }

    public void setValue(final Agent agent, final double value) {
      if (initValues[agent.ordinal()] == false) {
        values[agent.ordinal()] = value;
      }
    }

    public void setValue(final Agent[] agent, final double value) {
      if (agent == null) {
        setValue(Agent.values(), value);
      } else {
        for (Agent tmpAgent : agent) {
          if (initValues[tmpAgent.ordinal()] == false) {
            values[tmpAgent.ordinal()] = value;
          }
        }
      }
    }

    private void setInitValue(final Agent agent, final double value) {
      values[agent.ordinal()]     = value;
      initValues[agent.ordinal()] = true;
    }
  }


  class Squares {
    final int rows;
    final int cols;
    final Square[][] squares;
    final double[][] tmpValues;
    final Set<Tile> deadEnds;
    
    public Squares(int rows, int cols) {
      this.rows = rows;
      this.cols = cols;
      this.squares = new Square[this.rows][this.cols];
      for (int r = 0; r < rows; r = r + 1) {
        for (int c = 0; c < cols; c = c + 1) {
          squares[r][c] = new Square(r, c);
        }
      }
      
      this.tmpValues = new double[this.rows][this.cols];
      this.deadEnds = new HashSet<Tile>();
    }

    public void clear() {
      for (int r = 0; r < rows ; r++) {
        for (int c = 0; c < cols; c++) {
          // squares[r][c].setValue(Agent.EXPLORE, 0.0);
          // squares[r][c].setValue(Agent.MY_ANTS, 0.0);
          squares[r][c].setValue(Agent.ENEMY_ANTS, 0.0);
        }
      }
    }

    public void diffuse() {
      for (Agent agent : Agent.values()) {
        diffuse(agent);
      }
    }

    public void diffuse(Agent agent) {
      if (agent == null) {
        diffuse();
        return;
      }
      
      // long t0 = System.currentTimeMillis();

      for (int r = 0; r < rows ; r++) {
        for (int c = 0; c < cols; c++) {
          squares[r][c].initValue(agent);
        }
      }
      
      for (int r = 0; r < rows ; r++) {
        for (int c = 0; c < cols; c++) {
          double sum = 0.0;
          double sides = 0;
          if (shouldDiffuse(squares[r][c])) {
            if (shouldDiffuse(squares[(rows + r - 1) % rows][c])) {
              sum = sum + squares[(rows + r - 1) % rows][c].getValue(agent);
              sides++;
            }
            if (shouldDiffuse(squares[(r + 1) % rows][c])) {
              sum = sum + squares[(r + 1) % rows][c].getValue(agent);
              sides++;
            }
            if (shouldDiffuse(squares[r][(cols + c - 1) % cols])) {
              sum = sum + squares[r][(cols + c - 1) % cols].getValue(agent);
              sides++;
            }
            if (shouldDiffuse(squares[r][(c + 1) % cols])) {            
              sum = sum + squares[r][(c + 1) % cols].getValue(agent);
              sides++;
            }
            if (sides <= 1) {
              deadEnds.add(squares[r][c]);
            }
            sum = sum / sides;
          }
          tmpValues[r][c] = sum;
        }
      }
      
      for (int r = 0; r < rows ; r++) {
        for (int c = 0; c < cols; c++) {
          squares[r][c].setValue(agent, tmpValues[r][c]);
        }
      }
      // System.err.println("diffusion time: " + (System.currentTimeMillis() - t0));
    }
    
    private boolean shouldDiffuse(Square square) {
      return !waterTiles.contains(square) && !deadEnds.contains(square);
    }
    
    public void print(Agent[] agent) {
      for (int r = 0; r < rows ; r++) {
        for (int c = 0; c < cols; c++) {
          if (waterTiles.contains(squares[r][c])) {
            System.err.print(' ');
          } else if (deadEnds.contains(squares[r][c])) {
            System.err.print('-');
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
    
    public void printRaw(Agent[] agent) {
      for (int r = 0; r < rows ; r++) {
        for (int c = 0; c < cols; c++) {
          if (waterTiles.contains(squares[r][c])) {
            System.err.print("    ");
          } else if (deadEnds.contains(squares[r][c])) {
            System.err.print(" -- ");
          } else {
            double value = squares[r][c].getValue(agent);
            System.err.print(String.format("%4.0f", value));
          }
        }
        System.err.println();
      }
      System.err.println();
    }
    
    public List<AimValue> getAttackDirections(final Tile tile) {
      return getDirections(new Agent[] { Agent.ATTACK, Agent.TACTICS }, tile, -1);
    }
    
    public List<AimValue> getExploreDirections(final Tile tile) {
      return getDirections(new Agent[] { Agent.EXPLORE, Agent.TACTICS }, tile, -1);
    }
    
    public List<AimValue> getDirections(final Agent[] agent, final Tile tile, final int ascending) {
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

    // public List<AimValue> getProbabilisticDirections(final Agent agent, final Tile tile, final int ascending) {
    //   List<AimValue> values = getDirections(agent, tile, ascending);
    //   List<AimValue> result = new ArrayList<AimValue>(values);
    //   double tileValue = getValue(agent, tile);
    //   
    //   for (AimValue value : values) {
    //     if (random.nextInt(100) < 20) {
    //       // move to the end
    //       result.remove(value);
    //       result.add(value);
    //     }
    //   }
    //   
    //   return result;
    // }
    
    // public double getValue(final Agent agent, final Tile tile) {
    //   final int r = tile.getRow();
    //   final int c = tile.getCol();
    //   return squares[r][c].getValue(agent);
    // }
    // 
    // public double getValue(final Agent agent, final Tile tile, Aim direction) {
    //   return getValue(agent, direction != null ? ants.getTile(tile, direction) : tile);
    // }
    
  }
  Squares squares;
  int turn = 0;

  public static void main(String[] args) throws IOException {
    if (System.getProperty("logLevel") != null) {
      try {
        logLevel = Level.parse(System.getProperty("logLevel"));
      } catch (IllegalArgumentException e) {  
      } catch (NullPointerException e) {
      }
    }
    
    new MyBot().readSystemInput();
  }
  
  public void setup(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2, int attackRadius2, int spawnRadius2) {
    super.setup(loadTime, turnTime, rows, cols, turns, viewRadius2, attackRadius2, spawnRadius2);

    random = new Random(1);
    squares = new Squares(rows, cols);
    
    maxExploreAnts = (int) (rows * cols / viewRadius2 / 3);
    logFine("maxExploreAnts: " + maxExploreAnts);
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
        enemyDeadHills.add(enemyHill);
        it.remove();
      }
    }
    
    waterTiles.addAll(ants.getWaterTiles());
    enemyHills.addAll(ants.getEnemyHills());
    
    combatAnts = new HashMap<Tile, Set<Tile>>();
    
    Set<Tile> locs = new HashSet<Tile>();
    locs.addAll(ants.getMyAnts());
    locs.addAll(ants.getMyHills());
    
    for (Tile antLoc : locs) {
      Set<Tile> enemySet = new HashSet<Tile>();
      
      for (Tile offset : ants.getVisionOffsets()) {
        Tile loc = ants.getTile(antLoc, offset);
        if (ants.getEnemyAnts().contains(loc) && new Router(antLoc, loc).directions(10) != null) {
          enemySet.add(loc);
        }
      }
      
      if (!enemySet.isEmpty()) {
        combatAnts.put(antLoc, enemySet);
      }
    }

    attackAnts.retainAll(ants.getMyAnts());

    logFine("afterUpdate: " + (System.currentTimeMillis() - t0));
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

  public void moveAntsOnTargets(Set<Tile> targets, int maxDistance, Map<Tile,Tile> tmpOrders) {
    PriorityQueue<Route> possibleRoutes = new PriorityQueue<Route>();
    Set<Tile> antsOnTarget = new HashSet<Tile>();
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
      if (antsOnTarget.contains(route.getEnd())) { continue; }
      if (tmpOrders.containsValue(route.getStart())) { continue; }

      Aim aim = route.getDirections().size() == 1 ? null : route.getDirections().get(0);
      if (moveDirection(route.getStart(), aim, tmpOrders)) {
        antsOnTarget.add(route.getEnd());
        logFiner(route.getStart() + " -> " + route.getEnd() + " FOOD " + aim);
      }
    }
  }

  public void moveFoodAnts(Map<Tile,Tile> tmpOrders) {
    moveAntsOnTargets(ants.getFoodTiles(), 10, tmpOrders);
  }

  public void moveAnts() {
    Map<Tile,Tile> tmpOrders = new HashMap<Tile,Tile>(orders);
    Map<Tile, List<AimValue>> antsDirections = new HashMap<Tile, List<AimValue>>();
    Set<Tile> kamikazeAnts = new HashSet<Tile>();

    for (Tile antLoc : ants.getMyHills()) {
      if (!ants.getMyAnts().contains(antLoc)) { continue; }
      
      if ((ants.getMyAnts().size() - attackAnts.size() > maxExploreAnts) || (ants.getMyAnts().size() / 3 > attackAnts.size())) {
        attackAnts.add(antLoc);
      }
    }

    moveFoodAnts(tmpOrders);
    
    // Try to move ants towards highest explore goals.
    for (Tile antLoc : ants.getMyAnts()) {
      if (tmpOrders.containsValue(antLoc)) { continue; }
      
      // For ants close to my hills be more aggressive about attacking enemy ants (even if it means dying) to prevent
      // a lucky enemy ant from killing my hive.
      for (Tile myHill: ants.getMyHills()) {
        if (ants.getDistance(myHill, antLoc) <= ants.getViewRadius2()) {
          kamikazeAnts.add(antLoc);
          break;
        }
      }
      
      List<AimValue> directions = enemyHills.size() > 0 && attackAnts.contains(antLoc) ? squares.getAttackDirections(antLoc) : squares.getExploreDirections(antLoc);
      antsDirections.put(antLoc, directions);
      
      logFiner(antLoc + " " + directions + (attackAnts.contains(antLoc) ? " attack" : "") + (kamikazeAnts.contains(antLoc) ? " kamikaze" : ""));
      for (Iterator<AimValue> it = directions.iterator(); it.hasNext(); ) {
        AimValue aimValue = it.next();
        it.remove();
        
        if (moveDirection(antLoc, aimValue.aim, tmpOrders)) {
          logFiner(" -> " + aimValue.aim);
          break;
        }
      }

      // to -> from
      if (!tmpOrders.containsValue(antLoc)) { // haven't moved antLoc
        while (antLoc != null) {
          antLoc = tmpOrders.put(antLoc, antLoc); // move antLoc onto itself .. is there another ant?
        }
      }
    }

    // Prevent ants from dying.
    while (tmpOrders.size() > 0) {
      Tile antLoc = null;

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
        
        if (combatValues.willDie(newLoc, 0, kamikazeAnts.contains(oldLoc))) {
          antLoc = oldLoc;
          it.remove();
          logFiner(oldLoc + " ->  " + newLoc + " will die");
          break;
        }
      }

      if (antLoc == null) { break; }

      List<AimValue> directions = antsDirections.get(antLoc);
      if (directions == null) {
        directions = enemyHills.size() > 0 && attackAnts.contains(antLoc) ? squares.getAttackDirections(antLoc) : squares.getExploreDirections(antLoc);
        antsDirections.put(antLoc, directions);
      }
      logFiner(antLoc + " " + directions);
      
      for (Iterator<AimValue> it =  directions.iterator(); it.hasNext(); ) {
        AimValue aimValue = it.next();
        it.remove();

        if (moveDirection(antLoc, aimValue.aim, tmpOrders)) {
          logFiner(" -> " + aimValue.aim);
          break;
        }
      }
      
      // to -> from
      if (!tmpOrders.containsValue(antLoc)) { // haven't moved antLoc
        while (antLoc != null) {
          orders.put(antLoc, antLoc);
          antLoc = tmpOrders.put(antLoc, antLoc); // move antLoc onto itself .. is there another ant?
        }
      }
    }
    
    orders = tmpOrders;
  }

  public void issueOrders() {
    Set<Tile> newAttackAnts = new HashSet<Tile>();
    
    for (Map.Entry<Tile,Tile> order : orders.entrySet()) {
      Tile newLoc = order.getKey();
      Tile oldLoc = order.getValue();
      
      if (newLoc != null && oldLoc != null && !newLoc.equals(oldLoc)) {
        List<Aim> aims = ants.getDirections(oldLoc, newLoc);
        ants.issueOrder(oldLoc, aims.get(0));
      }
      
      if (attackAnts.contains(oldLoc)) {
        newAttackAnts.add(newLoc);
      }
    }
    
    attackAnts = newAttackAnts;
  }

  public void doTurn() {
    long t0;
    
    t0 = System.currentTimeMillis();
    squares.clear();
    for (int i = 0; i < Math.min(turn, 10); i++) {
      squares.diffuse(null);
    }
    logFine("diffusion: " + (System.currentTimeMillis() - t0));

    squares.printRaw(new Agent[] { Agent.ATTACK, Agent.TACTICS });
    
    t0 = System.currentTimeMillis();
    moveAnts();
    logFine("moveAnts: " + (System.currentTimeMillis() - t0));
    
    t0 = System.currentTimeMillis();
    issueOrders();
    logFine("issueOrders: " + (System.currentTimeMillis() - t0));
    
    logFine("ants died: " + ants.getMyDeadAnts().size());
    logFine("attack ants: " + attackAnts.size() + " enemy hills: " + enemyHills.size());
    logFine("----- done ----- " + ants.getTimeRemaining());
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
