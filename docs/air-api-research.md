# AIR API evidence and integration boundaries

Inspected the installed **AIR `262.8665.497`** artifacts with `javap` and the IDE's Kotlin metadata decompiler. These are primary binary artifacts, not inferred class names or reflection-based discovery.

## Installed evidence

Module directory:

```text
/Users/hsz/.gradle/caches/9.8.2/transforms/b062dc5921306098e8fe3459cf08d751/transformed/com.intellij.air-262.8665.497/air-plugin/lib/modules/
```

All paths below are relative to that exact directory; class paths follow `!/`.

| Artifact and class | Verified API |
| --- | --- |
| `intellij.air.frontend.session.catalog.api.jar!/com/intellij/air/frontend/session/catalog/SessionDiscoveryState.class` | `getInstance().sessions: StateFlow<SessionsSnapshot>`; also exposes provider/workspace snapshots, which are not needed for session discovery. |
| `intellij.air.frontend.session.catalog.api.jar!/com/intellij/air/frontend/session/catalog/SessionsSnapshot.class` | `projects: List<ProjectWorkspace>`, with an empty default constructor. |
| `intellij.air.frontend.session.catalog.api.jar!/com/intellij/air/frontend/session/catalog/ProjectWorkspace.class` | Nullable `projectDirectory: PathRef?`, `sessions`, `worktrees`, and `folders`. |
| `intellij.air.frontend.session.catalog.api.jar!/com/intellij/air/frontend/session/catalog/WorktreeWorkspace.class` | Separate workspace with nullable `projectDirectory`, its own `sessions` and `folders`, and `parentWorkspaceId`. |
| `intellij.air.frontend.session.catalog.api.jar!/com/intellij/air/frontend/session/catalog/SessionSummary.class` | `title`, `updatedAtMs`, nullable `cost: AgentThreadCost?`, `archived`, `activity`, `stoppedWithError`, `localSessionIdentity`, `agentId`, nullable `agentSessionId`, nullable `activityOccurrence`, and `isWaitingChainedSession`. |
| `intellij.air.shared.core.jar!/com/intellij/air/shared/core/thread/AgentThreadCost.class` | `amountUsd: BigDecimal?`, `kind: AgentThreadCostKind`, and nullable `matchedModelId: String?`; the amount is denominated in USD. |
| `intellij.air.shared.core.jar!/com/intellij/air/shared/core/thread/AgentThreadCostKind.class` | `EXACT`, `ESTIMATED`, or `UNAVAILABLE`. |
| `intellij.air.frontend.session.catalog.api.jar!/com/intellij/air/frontend/session/catalog/SessionFolder.class` | `status: SessionFolderStatus` (`IN_PROGRESS` or `DONE`), `sessions: Set<LocalSessionIdentity>`. |
| `intellij.air.shared.core.jar!/com/intellij/air/shared/core/AgentSessionActivity.class` | Exactly `READY`, `DRAFT`, `PROCESSING`, `REVIEWING`, `NEEDS_INPUT`, `UNREAD`. |
| `intellij.air.shared.core.jar!/com/intellij/air/shared/core/identity/LocalSessionIdentity.class` | `projectDirectory: String`, `localSessionId: LocalSessionId`; `LocalSessionId.value` is a `String`. |
| `intellij.air.shared.session.jar!/com/intellij/air/shared/threads/model/AgentSessionActivityOccurrence.class` | `kind: AgentSessionActivityOccurrenceKind`, `sourceRevision: String`; kind is `COMPLETED` or `NEEDS_ATTENTION`. |
| `intellij.air.frontend.session.view.api.jar!/com/intellij/air/thread/view/AgentSessionLinkNavigationService.class` | Companion `getInstanceOrNull(): AgentSessionLinkNavigationService?`; `openSession(target: AgentSessionLinkTarget, project: Project?): Unit`. |
| `intellij.air.frontend.session.view.api.jar!/com/intellij/air/thread/view/AgentSessionLinkTarget.class` | Constructor takes typed `AgentId`, `localSessionId: String`, and nullable `agentSessionId: String?`. |
| `intellij.air.frontend.session.toolwindow.jar!/com/intellij/air/frontend/session/toolwindow/actions/AgentThreadsTreeRowDoneAction.class` | AIR's individual-session Done button extends its archive action and uses `AgentEntryPoint.TREE_ROW_OVERLAY`. This is not a folder-status operation. |
| `intellij.air.frontend.session.toolwindow.jar!/com/intellij/air/frontend/session/toolwindow/ui/AgentThreadsToolWindowModelRpcFrontendService.class` | Application service; `archiveSessions(project, localIdentities, entryPoint, onComplete): Unit`. Completion supplies `AgentSessionArchiveResultDto.allRequestedArchived`; the tool window need not be open. |

`SessionSummary.id` is explicitly deprecated: its message says the legacy row ID mixes workspace, runtime route, and external/local session ID, and recommends `workspaceId` and `localSessionIdentity`. The adapter therefore derives pet IDs only from `LocalSessionIdentity`, using a length-prefixed directory plus local session value to avoid delimiter collisions. Backend session assignment, provider routing, title, and activity do not change a pet's ID.

Bytecode for `SessionSummary.isWaitingChainedSession` checks nonempty `waitingOnPredecessors` **or** `aggregateExclusion == WAITING_CHAINED_SESSION`; it is not inferred from the title or a backend session ID.

## Projection rules

- Compare the current `Project.basePath` with workspace `PathRef.value` using IntelliJ `FileUtil.pathsEqual`, never substring/prefix matching. Missing/blank paths select nothing.
- A matching project root includes that root and its worktrees. If only a worktree matches, include only that worktree, not its root or siblings. No unrelated project's sessions or folders enter the projection.
- Omit archived summaries. Deduplicate by stable local identity. Match done-folder members by the **entire** local identity, including the directory.
- Live `PROCESSING`, `NEEDS_INPUT`, and `REVIEWING` map to `WORKING`, `WAITING`, and `REVIEW` before considering stale errors or completion hints. Chained waiting maps to `WAITING`; otherwise stopped errors map to `ERROR`. A `NEEDS_ATTENTION` occurrence maps to `WAITING` before completion. `UNREAD`, a `COMPLETED` occurrence, or membership in a `DONE` folder maps to `DONE`; remaining `READY`/`DRAFT` sessions map to `RESTING`.
- The adapter maps `SessionSummary.cost` to `SessionCost` only when the amount is present, nonnegative, and the kind is `EXACT` or `ESTIMATED`; it preserves the `BigDecimal` amount and sets `estimated` for the latter. A null cost, null amount, `UNAVAILABLE`, or negative amount maps to `null`, never to zero. Zero is a valid amount. The existing `SessionDiscoveryState.sessions` flow delivers cost changes with ordinary session snapshot updates; no polling is used.
- Every discovery emission replaces its project's navigation index, even if the emitted pets compare equal. Removed/archived sessions cannot be opened from an old pet ID. `open` checks project ownership/current base path and uses the latest summary's agent, local session ID value, and optional backend session ID. Missing IDs or unavailable navigation return `false`.
- Collection is cold and does not create a scope, dispatcher, retry loop, or background job. The owning project service manages background collection, cancellation, retries, and calls `open` on the EDT. Discovery/navigation exceptions are not silently swallowed.

## Marking a session done

AIR's per-session Done action archives the session; only folder completion changes `SessionFolderStatus`. The heart control uses the same application-level archive service as the individual Done action, passing the exact indexed `LocalSessionIdentity` and `TREE_ROW_OVERLAY`. It never completes a folder, deletes a session, or opens it as a side effect.

`markDone` validates the project, current base path, latest indexed summary, and current completed state before dispatching. Its injectable archive boundary permits tests without starting an agent or altering real sessions. The project service suppresses simultaneous duplicate clicks, keeps demo completion entirely local, and reports unavailable/failed completion through the status-bar menu and tooltip.

The archive call is asynchronous. The adapter returns success only after `allRequestedArchived`, not merely after dispatch. AIR may omit its callback on an RPC error, so the wait is cancellable and bounded to 30 seconds; duplicate and late callbacks are ignored. A timeout means completion could not be confirmed, not that AIR cannot subsequently archive the session. Pets are removed only by AIR discovery's archived/removed update, never optimistically on a live click.

## Actual limitations

- These catalog, identity, navigation, and archive APIs are marked `ApiStatus.Internal`. They are pinned implementation APIs, not a supported cross-version AIR SDK; inspect and retest them when upgrading AIR or IntelliJ.
- Frontend `READY` does **not** distinguish every backend paused, idle, interrupted, or waiting substate. The adapter reports the honest coarser `RESTING` state (detail: “ready or paused”); it does not invent backend precision or scrape UI text/logs.
- `UNREAD` and completion occurrences are presentation/completion signals, not a guarantee that a task's desired outcome succeeded. `DONE` folders represent catalog organization. Current live activity takes precedence over these potentially older signals.
- Navigation returns `Unit`, so adapter success means the AIR navigation service was available and accepted the call, not proof that a particular editor became visible.
- AIR does not expose accumulated elapsed or working duration on the session catalog summary. Its session-tree time label is relative recency: `ThreadTreeThreadPresentationKt` passes `SessionSummary.displayOrderAtMs` into `ThreadTreeRelativeTimeLabels.label(timestamp, now)`, and `displayOrderAtMs` is derived from `providerUpdatedAtMs`/`lastUserPromptAtMs`. The “show time” setting toggles this presentation; it is not a duration API. The pet UI therefore uses a locally observed **working-time** counter, accumulated only over observed `WORKING` intervals, not a historical AIR session total. Those local timers reset when the project closes; intervals outside observation are not included.

## Verification handoff

`AirSessionSourceTest` constructs real typed AIR snapshots, summaries, identities, worktrees, folders, occurrence objects, and `AgentThreadCost` values. It covers exact/estimated cost mapping, precision preservation, null/unavailable/negative amounts, valid zero, and cost updates through a second snapshot for the same pet identity, in addition to all activity values, precedence, root/worktree isolation, missing paths, archives/removals, identity collisions, stable IDs across route changes, local-only navigation, unavailable navigation, and refreshed per-project targets. Archive tests additionally cover exact root/worktree identities, rejection of stale/non-completed targets, unconfirmed results, timeout/cancellation, duplicate/late callbacks, and discovery-driven removal. Only the navigation/archive endpoints and project base path are test doubles.

Final Gradle compilation and test execution belong to the parent integration task; no Gradle/model/UI/manifest changes are made by this work.