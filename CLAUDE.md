# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

FlipsDisguises is a Paper (Minecraft 1.21.10, Java 21) plugin that lets players be disguised either as
another player's skin, or as a mob via raw packet rewriting. It exposes both in-game commands and a
programmatic API for other plugins.

## Build & Test

Maven is the build tool.

```
mvn compile              # compile
mvn test                 # run all unit tests
mvn test -Dtest=DisguiseServiceTest#rateLimitBlocksSecondCommand   # run a single test method
mvn package              # build the shaded plugin jar (shade runs at the `package` phase)
```

There is no linter configured. Tests use JUnit 5 + Mockito (`mockito-junit-jupiter`); keep mocking light —
only mock the ports/collaborators a unit actually needs, don't mock Bukkit/Paper internals beyond that.

`FlipsLoader` is consumed as a `ch.eneas:FlipsLoader:1.0-SNAPSHOT` Maven dependency resolved from the local
repo — it is not publicly published, so a from-scratch build requires that artifact to already be installed
locally.

## Architecture

Hexagonal/clean architecture, organized **by feature**, not by technical layer. The core invariant: domain
and application code never import Bukkit/Paper/PacketEvents types directly except through a `port` interface
— all framework-specific code lives in `adapter` packages.

```
ch.eneas.PluginMain                        — JavaPlugin entry point
ch.eneas.flipsdisguises/
  api/              — FlipsDisguises (static service-locator accessor), FlipsDisguisesApi (public interface),
                      FlipsDisguisesApiImpl (delegates to DisguiseService)
  command/          — CommandAPI command trees (DisguiseCommands, AdminCommands) and custom Argument types
  disguise/
    application/    — DisguiseService: the orchestrator; DisguiseResult: sealed ok/failure result
    domain/         — framework-free models: MobDisguiseOptions, PlayerLookBackup, SkinSnapshot, SkinProperty
    port/           — interfaces the application layer depends on: MobDisguisePort, PlayerAppearancePort, SkinCatalog
    adapter/paper/  — Paper implementations of ports (PaperPlayerAppearance, PaperSkinCatalog w/ Caffeine cache)
    adapter/packet/ — PacketEvents-based implementation of MobDisguisePort (PacketMobDisguisePort) plus
                      entity-type mapping, synthetic metadata, and packet tracing helpers
    meta/           — small shared enums/annotations describing disguise argument types
  listener/         — Bukkit event listeners (join/quit lifecycle)
  plugin/           — FlipsLoader-managed services: SyncTaskRunner port + PaperSyncTaskRunner, ApiRegistrationService,
                      PacketEventsMobHookService
```

### Dependency injection — FlipsLoader

This project uses `FlipsLoader` (a separate first-party library, `ch.eneas.flips.loader.api`) as a small
CDI-like DI/registration framework instead of manual wiring:

- `@Injectable` on a class makes it available for injection and lets FlipsLoader instantiate it.
- `@Inject` on a constructor marks it for constructor injection; `DisguiseService` and most adapters take
  their ports this way.
- Classes implementing `Service` (with `startup(JavaPlugin)` / `shutdown(JavaPlugin)`) are started/stopped
  automatically — this is how the Bukkit `ServicesManager` registration (`ApiRegistrationService`) and the
  PacketEvents packet listener (`PacketEventsMobHookService`) are wired into the plugin lifecycle instead of
  being called directly from `onEnable`/`onDisable`.
- Classes implementing `CommandRegister` (`DisguiseCommands`, `AdminCommands`) return the CommandAPI command
  trees to register via `registerAll()`.
- `PluginMain.onEnable()` bootstraps PacketEvents *first* (it must be loaded/initialized before any
  PacketEvents-dependent `@Injectable` is constructed), then calls `FlipsLoader.getLoader().initPluginClasses(...)`
  to scan and wire everything else. `onDisable()` calls `FlipsLoader.deinitPlugin(this)` before terminating
  PacketEvents, in reverse order.
- When adding a new service/port implementation/command group, follow the existing pattern: annotate the
  class `@Injectable`, inject dependencies via an `@Inject` constructor, and implement `Service`/`CommandRegister`
  only if it needs lifecycle hooks or registers commands — don't wire things manually in `PluginMain`.

### Commands — CommandAPI

All commands are built with CommandAPI's fluent `CommandAPICommand` builder (see `DisguiseCommands`,
`AdminCommands`), not Bukkit's `CommandExecutor`. Custom argument types live under `command/args/` (e.g.
`MobDisguiseOptionsArgument` for parsing `--size`/`--name`/`--variant` style flags via
`MobDisguiseFlagTokanizer`). Command handlers call into `DisguiseService` and then reply with Adventure
`Component` messages — never raw strings or legacy color codes.

### Mob disguise mechanism (packet rewriting)

`PacketMobDisguisePort` is the most complex adapter: the server-side entity stays a real `Player` (so damage,
knockback, and collision all work normally), but outgoing packets are rewritten per-observer so the player
appears as a mob:

- PacketEvents is shaded into the plugin jar (relocated under `ch.eneas.flipsdisguises.shaded.pe*` in the
  shade plugin config) and bootstrapped manually in `PluginMain` — do not add a standalone PacketEvents
  plugin alongside this build (double-injector risk).
- An outgoing `PacketListener` intercepts `SPAWN_ENTITY`/`SPAWN_LIVING_ENTITY`/`ENTITY_METADATA`/
  `ENTITY_EQUIPMENT`/player-info packets to replace the entity type and strip player-only metadata for every
  observer except the subject.
- The subject sees their own mob model via the same rewrite path (no self-`hideEntity` special-casing).
- `MobDisguiseEntityMapping` defines which Bukkit `EntityType`s are eligible: must be `isAlive()`, have a
  matching PacketEvents `EntityTypes` constant, and not be on the blocklist (`PLAYER`, `UNKNOWN`, display/
  interaction/marker entities, non-living entities like boats/minecarts/arrows).
- `MobDisguiseSyntheticMetadata` builds a safe, small metadata subset (name, variant, size, etc.) so player
  metadata indices are never sent under a mob's metadata schema (mismatches here cause client disconnects).
- Respawn, world change, and new-observer joins require re-syncing entity ids and resending
  spawn/metadata/equipment packets — see `syncNewObserver`, `detachDisconnected`, and the respawn/world-change
  listeners in `PacketMobDisguisePort`.

### Player (skin) disguise

`PlayerAppearancePort`/`PaperPlayerAppearance` capture and restore the subject's real profile/skin
(`PlayerLookBackup`), then apply impersonation by copying only the target's **texture properties** (never
their Mojang UUID — the live `Player`'s connection UUID must always remain the real player's own UUID).
`SkinCatalog`/`PaperSkinCatalog` resolve a skin by username (online player profile first, Mojang lookup
otherwise) and cache results with Caffeine.

### Result handling

`DisguiseService` methods return `DisguiseResult` (a sealed ok/failure type carrying an Adventure `Component`
on failure) or a `CompletableFuture<DisguiseResult>` for operations that resolve a skin asynchronously and
then must hop back onto the main thread via `SyncTaskRunner` before touching Bukkit entities.
