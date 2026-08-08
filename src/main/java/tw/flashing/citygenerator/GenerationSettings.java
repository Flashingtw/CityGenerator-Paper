package tw.flashing.citygenerator;

record GenerationSettings(int minEdgeLength, int maxEdgeLength, int roadClearance,
                          int loopChance, int maxLoops) {
    GenerationSettings {
        minEdgeLength = Math.max(2, minEdgeLength);
        maxEdgeLength = Math.max(minEdgeLength, maxEdgeLength);
        roadClearance = Math.max(0, Math.min(1, roadClearance));
        loopChance = Math.max(0, Math.min(100, loopChance));
        maxLoops = Math.max(0, maxLoops);
    }
}
