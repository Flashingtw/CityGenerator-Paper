package tw.flashing.citygenerator;

record GridPos(int x, int z) {
    int manhattanDistance(GridPos other) {
        return Math.abs(x - other.x) + Math.abs(z - other.z);
    }

    GridPos move(int direction) {
        return switch (direction) {
            case 1 -> new GridPos(x + 1, z);
            case 2 -> new GridPos(x, z + 1);
            case 3 -> new GridPos(x - 1, z);
            case 4 -> new GridPos(x, z - 1);
            default -> throw new IllegalArgumentException("Unknown direction: " + direction);
        };
    }
}
