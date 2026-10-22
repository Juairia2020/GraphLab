<p align="center">
  <img src="docs/images/banner.svg" alt="GraphLab: learn, test, visualize and compare algorithms" width="100%">
</p>

<p align="center">
  <img alt="Java 17" src="https://img.shields.io/badge/Java-17-orange?logo=openjdk&logoColor=white">
  <img alt="Android 8.0+" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white">
  <img alt="Material 3" src="https://img.shields.io/badge/Material-3-6750A4?logo=materialdesign&logoColor=white">
  <img alt="Firebase" src="https://img.shields.io/badge/Firebase-Auth%20%2B%20Firestore-FFCA28?logo=firebase&logoColor=black">
  <a href="https://github.com/Juairia2020/GraphLab/actions/workflows/engine-tests.yml"><img alt="Engine tests" src="https://github.com/Juairia2020/GraphLab/actions/workflows/engine-tests.yml/badge.svg"></a>
  <img alt="Algorithms" src="https://img.shields.io/badge/algorithms-28-blue">
  <img alt="Arena activities" src="https://img.shields.io/badge/Arena%20activities-34-purple">
</p>

GraphLab is an Android app for learning, testing, visualizing, comparing and applying algorithms.
It looks like a puzzle game, but every answer is checked by a real algorithm engine.

## Features

```mermaid
mindmap
  root((GraphLab))
    Lab
      Build
        Graph editor
        Undo and redo
        Typed edge lists
        Photo import
          Camera or gallery
          Review before opening
      Run
        Step playback
        Step log
        State tables
        Pseudocode
        Insight page
        PDF export
      Compare
        Two algorithms side by side
        Saved comparison runs
      Experiment
        Karger min cut
        Randomized quick sort
        Quick select
        Balls in bins
        Secretary problem
        Histogram of results
      Graphs
        Library
        Share and import files
        Sample library
        Cloud gallery
      More
        Benchmark
        Dynamic graph
    Arena
      34 activities
        Easy, Medium, Hard ladder
        Stars and points
        Unlock order
      Specials
        Break the algorithm
        Fog of war
        Mystery cards
        Daily puzzle
      Leaderboard
    Toolkit
      Map scenarios
        Route
        Round trip
        Network
        Flow
        Flood relief
        Bus network
      Road distances
      Presets
    Me
      Theme
        System
        Light
        Dark
      Daily reminder
      Cloud sharing
      Effects
        Glow
        Sounds
        Haptics
        Reduced motion
      Lifecycle log
    Everywhere
      Landscape mode
      Edge to edge layout
```

## Algorithms

<p align="center"><img src="docs/images/algorithms.svg" alt="The 28 algorithms by topic" width="100%"></p>

Every algorithm produces a trace of steps. The screens only replay the trace, so the picture on the screen is
always what the algorithm really did.

<p align="center"><img src="docs/images/states.svg" alt="The six node states" width="100%"></p>

## How it works

```mermaid
flowchart LR
    G["Random graph"] --> R["Real algorithm run"]
    R --> K["Correct answer"]
    G --> O["Question with 4 options"]
    K --> C{"Player's choice"}
    O --> C
    C --> S["Stars, points, unlocks"]
```

```mermaid
flowchart LR
    subgraph engine [":engine (pure Java, JUnit 4)"]
        A["Algorithms and traces"] --> Q["Arena questions"]
        A --> P["Playback and compare"]
        M["Map and routes"]
        X["Experiment trials"]
    end
    subgraph app [":app (Android)"]
        U["Screens"] --> D[("SQLite")]
        U --> B["WorkManager, services, receivers"]
    end
    engine --> app
    app --> F["Firebase gallery"]
    app --> R["Routes proxy and OSRM"]
```

## Requirements

| Tool | Version |
|---|---|
| Android Studio | A stable release that supports AGP 9.4 |
| JDK | 17 or newer (the bundled one is fine) |
| Android SDK | Platform API 37, Build-Tools 36.0.0 |
| Gradle | 9.6.0 (downloaded by the wrapper) |
| Device | Emulator or phone, Android 8.0 (API 26) or newer |

Library versions are in `gradle/libs.versions.toml`.

## Run

1. Open the folder in Android Studio and wait for the Gradle sync.
2. Run the `app` configuration on an emulator or a phone.

Engine tests need no phone: `./gradlew :engine:test` (run `gradle wrapper` once to create the script).

GraphLab runs with no keys. These optional values go in `local.properties` (not tracked by Git; see `local.properties.example`):

| Key | Unlocks |
|---|---|
| `firebase.projectId`, `firebase.apiKey`, `firebase.appId` | Cloud gallery and player progress (rules in `docs/firestore.rules`) |
| `MAPS_API_KEY` | Google Maps on the Toolkit screen |
| `routesProxyUrl` | Road distances through `cloud/routes-proxy` (OSRM is the free fallback) |
| `sampleLibraryUrl` | A hosted `sample-library.json` (the app has a built-in copy) |

## Course coverage

### Algorithm topics

| Topic | Covered by |
|---|---|
| Graph traversal | BFS, DFS |
| Shortest paths | Dijkstra, Bellman-Ford, Floyd-Warshall, A* |
| Minimum spanning tree | Kruskal, Prim |
| Network flow and matching | Edmonds-Karp, Dinic, bipartite matching |
| Components and ordering | Tarjan SCC, topological sort |
| Greedy | Huffman, fractional knapsack, Kruskal, Prim |
| Dynamic programming | Coin change, edit distance, LCS, LIS, matrix-chain, 0/1 knapsack |
| Divide and conquer | Merge sort, quick sort, closest pair |
| Randomized algorithms | Randomized quick sort, quick select, Karger min cut, reservoir sampling, balls in bins, secretary problem |
| Graph colouring | Graph colouring |

### Android course checklist (17 of 17)

| # | Item | Where |
|---|---|---|
| 1 | Multi-activity app, explicit intents | `ui/common/BaseActivity.navigateTo`, the tab Activities |
| 2 | Implicit intents | `ui/common/GraphShare` (`ACTION_SEND`, `FileProvider`), `ACTION_OPEN_DOCUMENT` import |
| 3 | Lifecycle callbacks | `LabActivity`, `data/LifecycleLogger`, `LifecycleLogActivity` |
| 4 | XML layouts, ViewBinding, click listeners | `res/layout/*` |
| 5 | RecyclerView | `StepLogAdapter`, `NodeTableAdapter` (`ListAdapter`, `DiffUtil`) |
| 6 | Custom View | `ui/common/GraphView` |
| 7 | Mobile UX | Light and dark theme, edge-to-edge insets, 48 dp targets, empty states, swipe to delete with Undo |
| 8 | Service | `service/ExperimentService` (foreground service with notification actions) |
| 9 | BroadcastReceiver | `RunFinishedReceiver`, `BatteryStateReceiver`, `ConnectivityReceiver` |
| 10 | ContentProvider | `provider/GraphProvider`, client `CompareRunsActivity` |
| 11 | Permissions and component communication | `ui/me/ReminderController`, `work/Notifications` |
| 12 | HTTP and API requests | `data/net/HttpFetcher`, `data/cloud/OsrmClient` |
| 13 | JSON and XML parsing | `engine/io/MiniJson`, `GraphJson`, `GraphMl` |
| 14 | Database | `data/db/GraphDb`, `GraphRepository` (SQLite) |
| 15 | Background operations | `AppExecutors`, `LabViewModel.startRun`, `ExperimentService` |
| 16 | Advanced API | `work/ReminderScheduler`, `ReminderWorker` (WorkManager), notifications |
| 17 | Firebase | `data/cloud/FirebaseGallery` (Authentication and Firestore) |

## Project layout

```
GraphLab/
  engine/    pure Java engine and its JUnit tests (model, trace, algo, arena, geo, experiment, ...)
  app/       Android app (data, work, service, receiver, provider, ui)
  cloud/     optional routes proxy
  docs/      firestore.rules, sample-library.json, README images
  tools/     test-photo generator for the photo import
```
