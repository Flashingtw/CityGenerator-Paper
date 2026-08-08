package tw.flashing.citygenerator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

final class CityGraphGenerator {
    private record Junction(GridPos position, int arrivalDirection) {
    }

    private record LoopCandidate(GridPos source, GridPos target, int direction, int distance) {
    }

    CityGraph generate(int requestedJunctions, GenerationSettings settings, BooleanSupplier cancelled) {
        for (int attempt = 0; attempt < 8; attempt++) {
            int radius = requestedJunctions + attempt * settings.maxEdgeLength();
            CityGraph result = generateAttempt(requestedJunctions, radius, settings, cancelled);
            if (result != null) {
                return result;
            }
        }
        throw new IllegalStateException("無法在隨機布局中放入 " + requestedJunctions + " 個路口，請縮短道路長度或降低道路間距。");
    }

    private CityGraph generateAttempt(int requestedJunctions, int radius,
                                      GenerationSettings settings, BooleanSupplier cancelled) {
        int limit = radius * 2 + 1;
        GridPos start = new GridPos(radius + 1, radius + 1);
        boolean[][] occupied = new boolean[limit + 1][limit + 1];
        ArrayDeque<Junction> queue = new ArrayDeque<>();
        Map<GridPos, List<GridPos>> adjacency = new LinkedHashMap<>();
        Set<GridPos> junctionPositions = new HashSet<>();

        occupied[start.x()][start.z()] = true;
        adjacency.put(start, new ArrayList<>());
        junctionPositions.add(start);
        queue.addLast(new Junction(start, 0));

        int junctions = 1;
        int iterations = 0;
        while (!queue.isEmpty() && junctions < requestedJunctions) {
            if ((iterations++ & 63) == 0 && cancelled.getAsBoolean()) {
                throw new GenerationCancelledException();
            }

            Junction junction = queue.removeFirst();
            GridPos current = junction.position();
            int existingDegree = adjacency.getOrDefault(current, List.of()).size();
            int targetDegree = targetDegree(current, start, radius, junction.arrivalDirection() == 0);
            if (queue.isEmpty() && junctions < requestedJunctions) {
                targetDegree = Math.max(targetDegree, Math.min(4, existingDegree + 1));
            }
            int branchesNeeded = Math.min(
                    Math.max(0, targetDegree - existingDegree), requestedJunctions - junctions);
            int plannedDegree = existingDegree + branchesNeeded;

            List<Integer> directions = candidateDirections(junction.arrivalDirection(), plannedDegree);
            int branchesCreated = 0;
            for (int direction : directions) {
                if (branchesCreated >= branchesNeeded || junctions >= requestedJunctions) {
                    break;
                }

                List<GridPos> path = findPath(current, direction, limit, occupied, settings);
                if (path == null) {
                    continue;
                }

                GridPos previous = current;
                for (GridPos roadTile : path) {
                    occupied[roadTile.x()][roadTile.z()] = true;
                    link(adjacency, previous, roadTile);
                    previous = roadTile;
                }

                GridPos endpoint = path.getLast();
                junctionPositions.add(endpoint);
                queue.addLast(new Junction(endpoint, direction));
                junctions++;
                branchesCreated++;
            }
        }

        if (junctions != requestedJunctions
                || countVisualJunctions(adjacency) != requestedJunctions) {
            return null;
        }

        addNaturalLoops(limit, occupied, adjacency, junctionPositions, settings, cancelled);

        Map<GridPos, List<GridPos>> immutable = new LinkedHashMap<>();
        adjacency.forEach((key, value) -> immutable.put(key, List.copyOf(value)));
        return new CityGraph(start, Collections.unmodifiableMap(immutable), junctions);
    }

    private int countVisualJunctions(Map<GridPos, List<GridPos>> adjacency) {
        int count = 0;
        for (Map.Entry<GridPos, List<GridPos>> entry : adjacency.entrySet()) {
            List<GridPos> neighbours = entry.getValue();
            if (neighbours.size() != 2) {
                count++;
                continue;
            }
            GridPos position = entry.getKey();
            GridPos first = neighbours.get(0);
            GridPos second = neighbours.get(1);
            boolean straight = (first.x() == position.x() && second.x() == position.x())
                    || (first.z() == position.z() && second.z() == position.z());
            if (!straight) {
                count++;
            }
        }
        return count;
    }

    private void addNaturalLoops(int limit, boolean[][] occupied,
                                 Map<GridPos, List<GridPos>> adjacency,
                                 Set<GridPos> junctionPositions,
                                 GenerationSettings settings, BooleanSupplier cancelled) {
        if (settings.loopChance() <= 0 || settings.maxLoops() <= 0) {
            return;
        }

        List<LoopCandidate> candidates = findLoopCandidates(
                limit, occupied, adjacency, junctionPositions, settings);
        Collections.shuffle(candidates, ThreadLocalRandom.current());
        int loopsAdded = 0;
        LoopCandidate fallback = null;
        for (LoopCandidate candidate : candidates) {
            if (cancelled.getAsBoolean()) {
                throw new GenerationCancelledException();
            }
            if (!canAddLoop(candidate, limit, occupied, adjacency, settings)) {
                continue;
            }
            if (fallback == null) {
                fallback = candidate;
            }
            if (ThreadLocalRandom.current().nextInt(100) >= settings.loopChance()) {
                continue;
            }
            commitLoop(candidate, occupied, adjacency);
            loopsAdded++;
            if (loopsAdded >= settings.maxLoops()) {
                return;
            }
        }

        // If a valid closure exists, guarantee at least one loop for the default
        // non-zero loop setting instead of leaving the result entirely to chance.
        if (loopsAdded == 0 && fallback != null
                && canAddLoop(fallback, limit, occupied, adjacency, settings)) {
            commitLoop(fallback, occupied, adjacency);
        }
    }

    private List<LoopCandidate> findLoopCandidates(int limit, boolean[][] occupied,
                                                    Map<GridPos, List<GridPos>> adjacency,
                                                    Set<GridPos> junctionPositions,
                                                    GenerationSettings settings) {
        List<LoopCandidate> candidates = new ArrayList<>();
        for (GridPos source : junctionPositions) {
            if (adjacency.getOrDefault(source, List.of()).size() >= 4) {
                continue;
            }
            for (int direction = 1; direction <= 4; direction++) {
                GridPos cursor = source;
                for (int distance = 1; distance <= settings.maxEdgeLength(); distance++) {
                    cursor = cursor.move(direction);
                    if (!inside(cursor, limit)) {
                        break;
                    }
                    if (!occupied[cursor.x()][cursor.z()]) {
                        continue;
                    }
                    if (distance >= settings.minEdgeLength()
                            && junctionPositions.contains(cursor)
                            && adjacency.getOrDefault(cursor, List.of()).size() < 4) {
                        candidates.add(new LoopCandidate(source, cursor, direction, distance));
                    }
                    break;
                }
            }
        }
        return candidates;
    }

    private boolean canAddLoop(LoopCandidate candidate, int limit, boolean[][] occupied,
                               Map<GridPos, List<GridPos>> adjacency, GenerationSettings settings) {
        if (adjacency.getOrDefault(candidate.source(), List.of()).size() >= 4
                || adjacency.getOrDefault(candidate.target(), List.of()).size() >= 4
                || !remainsVisualJunction(candidate.source(),
                adjacency.getOrDefault(candidate.source(), List.of()), candidate.direction())
                || !remainsVisualJunction(candidate.target(),
                adjacency.getOrDefault(candidate.target(), List.of()), opposite(candidate.direction()))
                || graphDistance(candidate.source(), candidate.target(), adjacency)
                < settings.minEdgeLength() * 2 + 2) {
            return false;
        }

        GridPos cursor = candidate.source();
        for (int step = 1; step < candidate.distance(); step++) {
            cursor = cursor.move(candidate.direction());
            if (!inside(cursor, limit) || occupied[cursor.x()][cursor.z()]
                    || tooCloseToExistingRoad(cursor, candidate.source(), candidate.target(),
                    limit, occupied, settings.roadClearance())) {
                return false;
            }
        }
        return cursor.move(candidate.direction()).equals(candidate.target());
    }

    private boolean remainsVisualJunction(GridPos position, List<GridPos> neighbours,
                                          int newDirection) {
        if (neighbours.size() != 1) {
            return true;
        }
        GridPos existing = neighbours.getFirst();
        int existingDirection;
        if (existing.x() > position.x()) existingDirection = 1;
        else if (existing.z() > position.z()) existingDirection = 2;
        else if (existing.x() < position.x()) existingDirection = 3;
        else existingDirection = 4;
        return existingDirection != opposite(newDirection);
    }

    private void commitLoop(LoopCandidate candidate, boolean[][] occupied,
                            Map<GridPos, List<GridPos>> adjacency) {
        GridPos previous = candidate.source();
        for (int step = 1; step < candidate.distance(); step++) {
            GridPos roadTile = previous.move(candidate.direction());
            occupied[roadTile.x()][roadTile.z()] = true;
            link(adjacency, previous, roadTile);
            previous = roadTile;
        }
        link(adjacency, previous, candidate.target());
    }

    private int graphDistance(GridPos source, GridPos target,
                              Map<GridPos, List<GridPos>> adjacency) {
        ArrayDeque<GridPos> queue = new ArrayDeque<>();
        Map<GridPos, Integer> distances = new LinkedHashMap<>();
        queue.add(source);
        distances.put(source, 0);
        while (!queue.isEmpty()) {
            GridPos current = queue.removeFirst();
            int nextDistance = distances.get(current) + 1;
            for (GridPos neighbour : adjacency.getOrDefault(current, List.of())) {
                if (distances.putIfAbsent(neighbour, nextDistance) == null) {
                    if (neighbour.equals(target)) {
                        return nextDistance;
                    }
                    queue.addLast(neighbour);
                }
            }
        }
        return Integer.MAX_VALUE;
    }

    private List<Integer> candidateDirections(int arrivalDirection, int plannedDegree) {
        List<Integer> directions = new ArrayList<>(List.of(1, 2, 3, 4));
        Collections.shuffle(directions, ThreadLocalRandom.current());
        if (arrivalDirection == 0) {
            if (directions.size() >= 2 && directions.get(1) == opposite(directions.getFirst())) {
                for (int index = 2; index < directions.size(); index++) {
                    if (directions.get(index) != opposite(directions.getFirst())) {
                        Collections.swap(directions, 1, index);
                        break;
                    }
                }
            }
            return directions;
        }

        // The direction back to the parent is already occupied.
        directions.remove(Integer.valueOf(opposite(arrivalDirection)));

        // A degree-two junction should be a corner, never an artificial point
        // in the middle of a straight road.
        if (plannedDegree == 2) {
            directions.remove(Integer.valueOf(arrivalDirection));
        } else {
            directions.remove(Integer.valueOf(arrivalDirection));
            directions.add(arrivalDirection);
        }
        return directions;
    }

    private List<GridPos> findPath(GridPos start, int direction, int limit,
                                   boolean[][] occupied, GenerationSettings settings) {
        int effectiveMaximum = Math.min(settings.maxEdgeLength(), limit - 1);
        int effectiveMinimum = Math.min(settings.minEdgeLength(), effectiveMaximum);
        List<Integer> lengths = new ArrayList<>();
        for (int length = effectiveMinimum; length <= effectiveMaximum; length++) {
            lengths.add(length);
        }
        Collections.shuffle(lengths, ThreadLocalRandom.current());

        for (int length : lengths) {
            List<GridPos> path = new ArrayList<>(length);
            GridPos cursor = start;
            boolean valid = true;
            for (int step = 0; step < length; step++) {
                cursor = cursor.move(direction);
                if (!inside(cursor, limit)
                        || occupied[cursor.x()][cursor.z()]
                        || tooCloseToExistingRoad(cursor, start, limit, occupied, settings.roadClearance())) {
                    valid = false;
                    break;
                }
                path.add(cursor);
            }
            if (valid) {
                return path;
            }
        }
        return null;
    }

    private boolean tooCloseToExistingRoad(GridPos candidate, GridPos source, int limit,
                                           boolean[][] occupied, int clearance) {
        return tooCloseToExistingRoad(candidate, source, null, limit, occupied, clearance);
    }

    private boolean tooCloseToExistingRoad(GridPos candidate, GridPos source, GridPos target,
                                           int limit, boolean[][] occupied, int clearance) {
        for (int dx = -clearance; dx <= clearance; dx++) {
            int remaining = clearance - Math.abs(dx);
            for (int dz = -remaining; dz <= remaining; dz++) {
                int x = candidate.x() + dx;
                int z = candidate.z() + dz;
                if (x < 1 || z < 1 || x > limit || z > limit) {
                    continue;
                }
                boolean isSource = x == source.x() && z == source.z();
                boolean isTarget = target != null && x == target.x() && z == target.z();
                if (occupied[x][z] && !isSource && !isTarget) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean inside(GridPos position, int limit) {
        return position.x() >= 1 && position.z() >= 1
                && position.x() <= limit && position.z() <= limit;
    }

    private int targetDegree(GridPos position, GridPos start, int radius, boolean root) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        if (root) {
            return random.nextInt(100) < 35 ? 4 : 3;
        }

        double distanceRatio = position.manhattanDistance(start) / (double) Math.max(1, radius * 2);
        int roll = random.nextInt(100);
        if (distanceRatio < 0.35) {
            if (roll < 18) return 4;
            if (roll < 58) return 3;
            if (roll < 88) return 2;
            return 1;
        }
        if (distanceRatio < 0.70) {
            if (roll < 7) return 4;
            if (roll < 35) return 3;
            if (roll < 72) return 2;
            return 1;
        }
        if (roll < 2) return 4;
        if (roll < 16) return 3;
        if (roll < 48) return 2;
        return 1;
    }

    private int opposite(int direction) {
        return switch (direction) {
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 1;
            case 4 -> 2;
            default -> throw new IllegalArgumentException("Unknown direction: " + direction);
        };
    }

    private void link(Map<GridPos, List<GridPos>> adjacency, GridPos first, GridPos second) {
        adjacency.computeIfAbsent(first, ignored -> new ArrayList<>()).add(second);
        adjacency.computeIfAbsent(second, ignored -> new ArrayList<>()).add(first);
    }

    static final class GenerationCancelledException extends RuntimeException {
    }
}
