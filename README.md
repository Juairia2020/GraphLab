# GraphLab

An Android app for learning, testing, visualizing, comparing and applying algorithms.
It looks like a puzzle game, but every answer is checked by a real algorithm engine.

Built with Java 17, XML views, ViewBinding and Material 3. The project has no Kotlin.
The Gradle scripts use the Groovy DSL.

## Modules

| Module | Content |
|---|---|
| `:engine` | Plain Java. No Android imports. Algorithms, step traces, graph editing rules, Arena questions and scoring, map and route logic. Tested with JUnit 4. |
| `:app` | Android. Screens, SQLite storage, Firebase, WorkManager, services and receivers. |
| `cloud/routes-proxy` | Optional small proxy in front of the Google Routes API. |
| `tools/import-testset` | Script that makes the 20 test photos for the graph-photo import. |

Engine packages: `model`, `trace`, `algo`, `edit`, `compare`, `play`, `input`, `arena`, `insight`, `io`,
`net`, `remind`, `content`, `progress`, `dynamic`, `bench`, `geo`, `preset`, `imageimport`, `effects`,
`report`, `experiment`.

## Requirements

| Tool | Version |
|---|---|
| Android Studio | A stable release that supports AGP 9.4 |
| JDK | 17 or newer (the bundled one is fine) |
| Android SDK | Platform API 37, Build-Tools 36.0.0 |
| Gradle | 9.6.0 (downloaded by the wrapper) |
| Device | Emulator or phone, Android 8.0 (API 26) or newer |

All library versions are in `gradle/libs.versions.toml`.

## Build and run

1. In Android Studio choose **File > Open** and select this folder. Wait for the Gradle sync.
2. Run the `app` configuration on an emulator or a phone.

The project has no `gradlew` script. Android Studio uses `gradle/wrapper/gradle-wrapper.properties`.
To get the script, run `gradle wrapper` once. Then, on the command line:

```
./gradlew :engine:test          # engine tests, no phone needed
./gradlew :app:assembleDebug    # debug APK
./gradlew :engine:run           # prints a sample trace (TraceDemo)
```

## Optional settings

GraphLab builds and runs with no keys. Features that need a key show a "not set up" message.
Put the values below in `local.properties` (the file is ignored by Git).
`local.properties.example` lists the same lines.

| Key | Purpose |
|---|---|
| `sampleLibraryUrl` | Address of your hosted `sample-library.json` (https only). |
| `firebase.projectId`, `firebase.apiKey`, `firebase.appId` | Cloud gallery and player progress. |
| `MAPS_API_KEY` | Google Maps key for the map screen. Without it the map is blank. |
| `routesProxyUrl` | Address of the deployed `cloud/routes-proxy`. |

### Cloud gallery (Firebase, about ten minutes)

The app does not use `google-services.json`. It reads the three values above and starts Firebase itself.

1. Open <https://console.firebase.google.com> and add a project (the free Spark plan is enough).
2. Add an Android app with the package name `com.graphlab`. Skip the other wizard steps.
3. Copy the Project ID, the Web API Key and the App ID into `local.properties`. Sync and rebuild.
4. **Build > Authentication > Sign-in method > Anonymous > Enable.**
5. **Build > Firestore Database > Create database** (production mode). Open the **Rules** tab,
   paste the content of `docs/firestore.rules` and publish.
6. Open Lab > Graphs > Cloud gallery and publish a graph.

Notes:
- Everything published is public. There is no moderation. As owner you can delete any post in the console.
- Clearing the app data creates a new anonymous user. Old posts then need to be deleted in the console.
- The API key identifies the project. It is not a password. The Firestore rules protect the data.

| Message in the app | Likely cause |
|---|---|
| The cloud is not set up in this build | A value is missing, or the project was not rebuilt. |
| Sign-in failed | Anonymous sign-in is not enabled. |
| The cloud refused the request | The rules are not published, or the post breaks a rule. |
| No connection to the cloud | The phone is offline. |

### Sample library

Lab > Graphs > Sample library reads a JSON file from `sampleLibraryUrl`. If there is no address, no
connection or the file is invalid, the app uses its built-in copy (`app/src/main/assets/sample-library.json`).
`docs/sample-library.json` is an identical copy for hosting. A test checks that the two files match.
To host it: put the file in a public GitHub repository, open it, press **Raw**, and use that address.

### Map and road distances

Road distances come from the first source that answers: cache, the Google proxy (if `routesProxyUrl` is set),
then the free OSRM service. If all fail, the app shows a straight-line estimate and the reason ("Why: ...").
Map nodes are spread out and the graph keeps only the useful links, so it stays readable.

## The four tabs

- **Lab**: Build (editor with undo and redo, typed edge lists, photo import), Run (step playback, step log,
  state tables, Insight tab, PDF export), Compare (two algorithms side by side), Graphs (library, files, sample library, cloud gallery).
- **Arena**: 34 activities, including Break the algorithm (change one edge so Dijkstra fails; Bellman-Ford checks it)
  and Fog of war (a walker sees only nearby roads and always takes the cheapest; greedy versus best trip). One activity per topic. Each activity is a ladder of Easy, Medium and Hard questions
  (10, 20 and 30 points). Stars are given by points. Only unlocked activities and the next locked one are shown.
- **Toolkit**: ready-made scenarios (route, trip, network, flow, flood, bus) on a map, plus presets.
- **Experiment** (Lab): repeats a randomized algorithm many times in a foreground service and draws a histogram.
  Algorithms: Karger min cut (on a saved graph), randomized quick sort, randomized quick select, random hashing
  (balls in bins) and the hiring rule (secretary problem).
- **Me**: theme, daily reminder, cloud sharing, effects (glow, sound, haptics, reduced motion, telemetry),
  lifecycle log, intro and Arena reset.

## Sounds

Short sounds play for a correct answer, a wrong answer, a round finished with two or more stars, the end of a Run
playback and the end of an experiment. They use the media volume. Me > Effects > Sound (or Reduce effects) turns them off.
The files are in `app/src/main/res/raw`.

## Landscape mode

Phones in landscape have little height, so the bars are folded away:

- The bottom bar is hidden. A slim button on the left edge shows and hides it. In the Lab it also shows and hides the mode bar (Graphs, Build, Run, Compare, Experiment).
- **Run**: the graph fills the screen. The step message floats on top of it. A button at the bottom right opens a tray with the playback buttons, the step slider and the speed slider. A button at the top right opens the side column (algorithm, source, Run button, Steps, State and Insight). The side column starts open until the first run. After Run it closes and the tray opens.
- **Arena question**: the graph takes the left half at full height. The question and the answers scroll on the right, with a slim header (Leave, title, count, progress).

Portrait layouts are unchanged.

## Algorithms

Graph: BFS, DFS, Dijkstra, Bellman-Ford, Kruskal, Prim, A*, and flow and DP packs.
Array: merge sort, quick sort, randomized quick sort.
Knapsack: fractional and 0/1.
Every algorithm produces a `Trace` of `Step` frames. The screens only replay the trace.
Each node or cell has one of six states, drawn with colours from the theme tokens.

## Android checklist (17 items)

| # | Item | Where |
|---|---|---|
| 1 | Multi-activity app, explicit intents | `ui/common/BaseActivity.navigateTo`, tab Activities |
| 2 | Implicit intents | `ui/common/GraphShare` (`ACTION_SEND`, `FileProvider`); `ACTION_OPEN_DOCUMENT` import |
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
  engine/        pure Java engine and its JUnit tests
  app/           Android app (java/com/graphlab: data, work, service, receiver, provider, ui)
  cloud/         optional routes proxy
  docs/          firestore.rules, sample-library.json
  tools/         import test-set generator
  gradle/libs.versions.toml
  local.properties.example
```

## Colour tokens and theme

Colours are defined once as tokens in `res/values/colors.xml` (light) and `res/values-night/colors.xml` (dark).
The six algorithm states (idle, frontier, visited, chosen, path, fail) have their own tokens.
The theme (System, Light, Dark) is saved in preferences and applied at startup by `GraphLabApp`.
Screens use `EdgeInsets.pad` so content keeps its layout padding under the system bars.

## Testing

Engine (no phone needed):

```
./gradlew :engine:test
```

Device checks (do these on a phone or emulator after a build):

1. **Lab**: build a graph, undo and redo, run each algorithm, step forward and back, open Insight, export a PDF.
   Rotate the phone on Build and Run. Landscape hides the bars (see Landscape mode); check that every control is still reachable.
2. **Photo import**: import a graph photo, confirm the detected nodes and edges, open it in the editor.
3. **Graphs**: save, rename, delete with Undo, share a file, import a file, open the sample library.
4. **Arena**: finish a ladder. Stars and unlocks must be the same in portrait and landscape.
   Check the floating explanation card and that rotation does not lose the round.
5. **Toolkit**: run each scenario. Check road distances, the "Why" note when the estimate is used, and the readable graph.
6. **Me**: switch theme, effects, reminder (permission prompt), cloud sharing, intro, Arena reset.
7. **Android parts**: lifecycle log, experiment service with notification actions, battery receiver, Compare runs (ContentProvider).
8. **Cloud**: with Firebase set up, publish and delete a graph from the gallery.

## Known limits

- The app layer was written without an Android SDK in the authoring sandbox. A first Gradle build and device test may show small errors. Fix those first.
- The OSRM road service answer format was written from its documentation and was not tried against the live server.
- The public OpenStreetMap tile server is meant for light use. A large release needs its own tile provider.
- Photo import uses on-device detection. Accuracy on real photos is not measured.

## Release

Before tagging a release: run `./gradlew test connectedAndroidTest lint`, check the device list above, then

```
git tag -a v1.0 -m "Release"
git push origin main --tags
```
