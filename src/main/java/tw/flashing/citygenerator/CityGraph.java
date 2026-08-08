package tw.flashing.citygenerator;

import java.util.List;
import java.util.Map;

record CityGraph(GridPos start, Map<GridPos, List<GridPos>> adjacency, int junctionCount) {
}
