# GraphLab

An Android app for learning, testing and comparing algorithms. This repository holds the work finished so far.

Built with Java 17 and Gradle (Groovy DSL). The project has two modules.

## What is done

### `:engine` (plain Java, no Android code)

**Algorithms** (each one produces a step-by-step trace that a screen can replay):
- Graph: BFS, DFS, Dijkstra, Bellman-Ford, Floyd-Warshall, A*, Kruskal, Prim, Edmonds-Karp, Dinic, Tarjan SCC,
  topological sort, bipartite matching, graph colouring, Karger min cut, closest pair.
- Sorting: merge sort, quick sort, randomized quick sort.
- Knapsack: fractional and 0/1.
- Dynamic programming: coin change, edit distance, LCS, LIS, matrix-chain.
- Others: Huffman, reservoir sampling.

**Supporting code:**
- `model`, `trace`: graph model and the step trace that every algorithm fills.
- `edit`: graph editing rules, undo and redo.
- `play`: playback of a trace.
- `input`, `io`: input parsing and graph file formats (JSON, GraphML, edge lists).
- `compare`: two algorithms side by side.
- `insight`: the teaching page of each algorithm.
- `net`: sample library of ready-made graphs.
- `preset`: Toolkit presets.
- `report`: run report.
- `effects`: settings for glow, sound and haptics.
- `remind`: daily reminder planning.
- `progress`: player progress, mastery and streaks.

**Arena engine** (34 activities):
- Questions come from random problems. The right answer is always read from a real algorithm run.
- Levels, stars and points, unlock order, session planner, mystery cards, daily puzzle and leaderboard.
- Includes Break the algorithm (make one edge negative so Dijkstra fails) and Fog of war (a walker that always takes the cheapest visible road).
- Content files are in `engine/src/main/resources/content`.

**Experiment engine** (randomized algorithms):
- Runs many random trials of an algorithm and collects the results in a histogram.
- Algorithms: randomized quick sort, quick select, balls in bins, secretary problem and Karger min cut.

**Map engine** (the Toolkit map):
- Road distances come from a cache, then a route service, then OSRM, and last a straight-line estimate.
- City graphs with readable node spacing, A* routes and a round trip (TSP) over chosen places.
- Map tools and flow scenarios (flood relief, bus network).

### `:app` (early Android version)

- Four tabs: Arena, Lab, Toolkit and Me, sharing one base screen and bottom bar.
- Light, dark and system theme.
- The graph canvas and editor in the Lab, with a weight dialog.
- Portrait and landscape layouts for the Lab.

More of the app is still being added.

## Build

| Tool | Version |
|---|---|
| Android Studio | A stable release that supports AGP 9.1 (see `gradle/libs.versions.toml`) |
| JDK | 17 or newer |
| Android SDK | Platform API 37 |
| Device | Android 8.0 (API 26) or newer |

Open the folder in Android Studio and let Gradle sync, then run the `app` configuration.
Library versions are in `gradle/libs.versions.toml`.

## Optional settings

Everything builds without keys. See `local.properties.example` for the optional values (sample library address,
Firebase project, map key). `local.properties` is ignored by Git.
