package tw.flashing.citygenerator;

record SchematicChoice(String name, int angle) {
    static SchematicChoice fromNeighbours(GridPos position, Iterable<GridPos> neighbours) {
        int mask = 0;
        for (GridPos neighbour : neighbours) {
            if (neighbour.z() < position.z()) mask |= 1;      // Z-
            else if (neighbour.x() > position.x()) mask |= 2; // X+
            else if (neighbour.z() > position.z()) mask |= 4; // Z+
            else if (neighbour.x() < position.x()) mask |= 8; // X-
        }

        return switch (mask) {
            case 1 -> new SchematicChoice("deadend", 180);
            case 2 -> new SchematicChoice("deadend", 270);
            case 4 -> new SchematicChoice("deadend", 0);
            case 8 -> new SchematicChoice("deadend", 90);
            case 5 -> new SchematicChoice("straight", 0);
            case 10 -> new SchematicChoice("straight", 90);
            case 3 -> new SchematicChoice("corner", 270);
            case 6 -> new SchematicChoice("corner", 0);
            case 12 -> new SchematicChoice("corner", 90);
            case 9 -> new SchematicChoice("corner", 180);
            case 11 -> new SchematicChoice("t_junction", 180);
            case 7 -> new SchematicChoice("t_junction", 270);
            case 14 -> new SchematicChoice("t_junction", 0);
            case 13 -> new SchematicChoice("t_junction", 90);
            default -> new SchematicChoice("cross", 0);
        };
    }
}
