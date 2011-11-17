import java.util.*;


/**
 * Holds all game data and current game state.
 */
public class Ants {
    /** Maximum map size. */
    public static final int MAX_MAP_SIZE = 256 * 2;

    private final int loadTime;

    private final int turnTime;

    private final int rows;

    private final int cols;

    private final int turns;

    private final int viewRadius2;
    private final Set<Tile> visionOffsets;

    private final int attackRadius2;
    private final Set<Tile> attackOffsets;
    private final Set<Tile> aggressionOffsets;

    private final int spawnRadius2;

    private final boolean visible[][];

    private long turnStartTime;

    private final Set<Tile> myAnts = new TreeSet<Tile>();
    private final Set<Tile> enemyAnts = new TreeSet<Tile>();
    private final Map<Tile, Integer> ants = new HashMap<Tile,Integer>();
    
    private final Set<Tile> myHills = new HashSet<Tile>();
    private final Set<Tile> enemyHills = new HashSet<Tile>();

    private final Set<Tile> waterTiles = new HashSet<Tile>();
    private final Set<Tile> foodTiles = new TreeSet<Tile>();

    private final Set<Order> orders = new HashSet<Order>();

    /**
     * Creates new {@link Ants} object.
     * 
     * @param loadTime timeout for initializing and setting up the bot on turn 0
     * @param turnTime timeout for a single game turn, starting with turn 1
     * @param rows game map height
     * @param cols game map width
     * @param turns maximum number of turns the game will be played
     * @param viewRadius2 squared view radius of each ant
     * @param attackRadius2 squared attack radius of each ant
     * @param spawnRadius2 squared spawn radius of each ant
     */
    public Ants(int loadTime, int turnTime, int rows, int cols, int turns, int viewRadius2,
            int attackRadius2, int spawnRadius2) {
        this.loadTime = loadTime;
        this.turnTime = turnTime;
        this.rows = rows;
        this.cols = cols;
        this.turns = turns;
        this.viewRadius2 = viewRadius2;
        this.attackRadius2 = attackRadius2;
        this.spawnRadius2 = spawnRadius2;
        visible = new boolean[rows][cols];
        for (boolean[] row : visible) {
            Arrays.fill(row, false);
        }

        // calc vision offsets
        visionOffsets = new HashSet<Tile>();
        int mx = (int)Math.sqrt(viewRadius2);
        for (int row = -mx; row <= mx; ++row) {
          for (int col = -mx; col <= mx; ++col) {
            int d = row * row + col * col;
            if (d <= viewRadius2) {
              visionOffsets.add(new Tile(row, col));
            }
          }
        }
        
        // calc attack offsets
        attackOffsets = new HashSet<Tile>();
        mx = (int)Math.sqrt(attackRadius2);
        for (int row = -mx; row <= mx; ++row) {
          for (int col = -mx; col <= mx; ++col) {
            int d = row * row + col * col;
            if (d <= attackRadius2) {
              attackOffsets.add(new Tile(row, col));
            }
          }
        }
        System.err.println("attack: " + attackOffsets);
        
        double aggressionRadius2 = Math.pow((Math.sqrt(attackRadius2) + 1), 2);
        aggressionOffsets = new HashSet<Tile>();
        mx = (int)Math.sqrt(aggressionRadius2);
        for (int row = -mx; row <= mx; ++row) {
          for (int col = -mx; col <= mx; ++col) {
            int d = row * row + col * col;
            if (d <= aggressionRadius2) {
              aggressionOffsets.add(new Tile(row, col));
            }
          }
        }
        System.err.println("aggression: " + aggressionOffsets);
    }

    /**
     * Returns timeout for initializing and setting up the bot on turn 0.
     * 
     * @return timeout for initializing and setting up the bot on turn 0
     */
    public int getLoadTime() {
        return loadTime;
    }

    /**
     * Returns timeout for a single game turn, starting with turn 1.
     * 
     * @return timeout for a single game turn, starting with turn 1
     */
    public int getTurnTime() {
        return turnTime;
    }

    /**
     * Returns game map height.
     * 
     * @return game map height
     */
    public int getRows() {
        return rows;
    }

    /**
     * Returns game map width.
     * 
     * @return game map width
     */
    public int getCols() {
        return cols;
    }

    /**
     * Returns maximum number of turns the game will be played.
     * 
     * @return maximum number of turns the game will be played
     */
    public int getTurns() {
        return turns;
    }

    /**
     * Returns squared view radius of each ant.
     * 
     * @return squared view radius of each ant
     */
    public int getViewRadius2() {
      return viewRadius2;
    }

    public Set<Tile> getVisionOffsets() {
      return visionOffsets;
    }

    /**
     * Returns squared attack radius of each ant.
     * 
     * @return squared attack radius of each ant
     */
    public int getAttackRadius2() {
        return attackRadius2;
    }

    public Set<Tile> getAttackOffsets() {
      return attackOffsets;
    }

    public Set<Tile> getAggressionOffsets() {
      return aggressionOffsets;
    }

    /**
     * Returns squared spawn radius of each ant.
     * 
     * @return squared spawn radius of each ant
     */
    public int getSpawnRadius2() {
        return spawnRadius2;
    }

    /**
     * Sets turn start time.
     * 
     * @param turnStartTime turn start time
     */
    public void setTurnStartTime(long turnStartTime) {
        this.turnStartTime = turnStartTime;
    }

    /**
     * Returns how much time the bot has still has to take its turn before timing out.
     * 
     * @return how much time the bot has still has to take its turn before timing out
     */
    public int getTimeRemaining() {
        return turnTime - (int)(System.currentTimeMillis() - turnStartTime);
    }

    /**
     * Returns location in the specified direction from the specified location.
     * 
     * @param tile location on the game map
     * @param direction direction to look up
     * 
     * @return location in <code>direction</code> from <cod>tile</code>
     */
    public Tile getTile(Tile tile, Aim direction) {
        int row = (tile.getRow() + direction.getRowDelta()) % rows;
        if (row < 0) {
            row += rows;
        }
        int col = (tile.getCol() + direction.getColDelta()) % cols;
        if (col < 0) {
            col += cols;
        }
        return new Tile(row, col);
    }

    /**
     * Returns location with the specified offset from the specified location.
     * 
     * @param tile location on the game map
     * @param offset offset to look up
     * 
     * @return location with <code>offset</code> from <cod>tile</code>
     */
    public Tile getTile(Tile tile, Tile offset) {
        int row = (tile.getRow() + offset.getRow()) % rows;
        if (row < 0) {
            row += rows;
        }
        int col = (tile.getCol() + offset.getCol()) % cols;
        if (col < 0) {
            col += cols;
        }
        return new Tile(row, col);
    }

    /**
     * Returns a set containing all my ants locations.
     * 
     * @return a set containing all my ants locations
     */
    public Set<Tile> getMyAnts() {
      return myAnts;
    }

    /**
     * Returns a set containing all enemy ants locations.
     * 
     * @return a set containing all enemy ants locations
     */
    public Set<Tile> getEnemyAnts() {
      return enemyAnts;
    }

    public Map<Tile, Integer> getAnts() { 
      return ants;
    }

    /**
     * Returns a set containing all my hills locations.
     * 
     * @return a set containing all my hills locations
     */
    public Set<Tile> getMyHills() {
        return myHills;
    }

    /**
     * Returns a set containing all enemy hills locations.
     * 
     * @return a set containing all enemy hills locations
     */
    public Set<Tile> getEnemyHills() {
        return enemyHills;
    }

    /**
     * Returns a set containing all food locations.
     * 
     * @return a set containing all food locations
     */
    public Set<Tile> getFoodTiles() {
        return foodTiles;
    }

    public Set<Tile> getWaterTiles() {
        return waterTiles;
    }

    /**
     * Returns all orders sent so far.
     * 
     * @return all orders sent so far
     */
    public Set<Order> getOrders() {
        return orders;
    }

    /**
     * Returns true if a location is visible this turn
     *
     * @param tile location on the game map
     *
     * @return true if the location is visible
     */
    public boolean isVisible(Tile tile) {
        return visible[tile.getRow()][tile.getCol()];
    }

    /**
     * Calculates distance between two locations on the game map.
     * 
     * @param t1 one location on the game map
     * @param t2 another location on the game map
     * 
     * @return distance between <code>t1</code> and <code>t2</code>
     */
    public int getManhattanDistance(Tile t1, Tile t2) {
        int rowDelta = Math.abs(t1.getRow() - t2.getRow());
        int colDelta = Math.abs(t1.getCol() - t2.getCol());
        rowDelta = Math.min(rowDelta, rows - rowDelta);
        colDelta = Math.min(colDelta, cols - colDelta);
        return rowDelta + colDelta;
    }

    public int getDistance(Tile t1, Tile t2) {
        int rowDelta = Math.abs(t1.getRow() - t2.getRow());
        int colDelta = Math.abs(t1.getCol() - t2.getCol());
        rowDelta = Math.min(rowDelta, rows - rowDelta);
        colDelta = Math.min(colDelta, cols - colDelta);
        return rowDelta * rowDelta + colDelta * colDelta;
    }
    
    /**
     * Returns one or two orthogonal directions from one location to the another.
     * 
     * @param t1 one location on the game map
     * @param t2 another location on the game map
     * 
     * @return orthogonal directions from <code>t1</code> to <code>t2</code>
     */
    public List<Aim> getDirections(Tile t1, Tile t2) {
        List<Aim> directions = new ArrayList<Aim>();
        if (t1.getRow() < t2.getRow()) {
            if (t2.getRow() - t1.getRow() >= rows / 2) {
                directions.add(Aim.NORTH);
            } else {
                directions.add(Aim.SOUTH);
            }
        } else if (t1.getRow() > t2.getRow()) {
            if (t1.getRow() - t2.getRow() >= rows / 2) {
                directions.add(Aim.SOUTH);
            } else {
                directions.add(Aim.NORTH);
            }
        }
        if (t1.getCol() < t2.getCol()) {
            if (t2.getCol() - t1.getCol() >= cols / 2) {
                directions.add(Aim.WEST);
            } else {
                directions.add(Aim.EAST);
            }
        } else if (t1.getCol() > t2.getCol()) {
            if (t1.getCol() - t2.getCol() >= cols / 2) {
                directions.add(Aim.EAST);
            } else {
                directions.add(Aim.WEST);
            }
        }
        return directions;
    }

    public void clearAnts() {
      myAnts.clear();
      enemyAnts.clear();
      ants.clear();
    }

    public void clearFood() {
      foodTiles.clear();
    }

    public void clearWater() {
      waterTiles.clear();
    }

    public void clearHills() {
      myHills.clear();
      enemyHills.clear();
    }

    public void clearVision() {
      for (int row = 0; row < rows; ++row) {
        for (int col = 0; col < cols; ++col) {
          visible[row][col] = false;
        }
      }
    }

    public void updateWater(Tile tile) {
      waterTiles.add(tile);
    }

    public void updateFood(Tile tile) {
      foodTiles.add(tile);
    }

    public void updateAnts(int owner, Tile tile) {
      if (owner > 0) {
        enemyAnts.add(tile);
        ants.put(tile, owner);
      } else {
        myAnts.add(tile);
        ants.put(tile, owner);
      }
    }

    public void updateHills(int owner, Tile tile) {
      if (owner > 0) {
        enemyHills.add(tile);
      } else {
        myHills.add(tile);
      }
    }

    public void setVision() {
      for (Tile antLoc : myAnts) {
        for (Tile locOffset : visionOffsets) {
          Tile newLoc = getTile(antLoc, locOffset);
          visible[newLoc.getRow()][newLoc.getCol()] = true;
        }
      }
    }

    /**
     * Issues an order by sending it to the system output.
     * 
     * @param myAnt map tile with my ant
     * @param direction direction in which to move my ant
     */
    public void issueOrder(Tile myAnt, Aim direction) {
        Order order = new Order(myAnt, direction);
        orders.add(order);
        System.out.println(order);
    }
}
