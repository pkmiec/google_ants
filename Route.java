import java.util.*;

/**
* Represents a route from one tile to another.
*/
public class Route implements Comparable<Route> {
  private Tile start;
  private Tile end;
  private List<Aim> directions;

  public Route(Tile start, Tile end, List<Aim> directions) {
    this.start      = start;
    this.end        = end;
    this.directions = directions;
  }

  public void setStart(Tile tile) {
    this.start = tile;
  }

  public Tile getStart() {
    return start;
  }

  public Tile getEnd() {
    return end;
  }

  public int getDistance() {
    return directions.size();
  }

  public List<Aim> getDirections() {
    return directions;
  }

  public Aim getAim() {
    return directions.get(0);
  }

  public Object clone() {
    return new Route(start, end, new ArrayList<Aim>(this.directions));
  }

  @Override
  public int compareTo(Route route) {
    return getDistance() - route.getDistance();
  }

  @Override
  public int hashCode() {
    return start.hashCode() * Ants.MAX_MAP_SIZE * Ants.MAX_MAP_SIZE + end.hashCode();
  }

  @Override
  public boolean equals(Object o) {
    boolean result = false;
    if (o instanceof Route) {
      Route route = (Route)o;
      result = start.equals(route.start) && end.equals(route.end);
    }
    return result;
  }
  
  public String toString() {
    return this.start + " -> " + this.end + " by " + Arrays.toString(this.directions.toArray());
  }
}


