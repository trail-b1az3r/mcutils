# MC Utils

A [WorldEdit](https://modrinth.com/mod/worldedit) add-on for Fabric with three extra commands.

| Minecraft | Fabric Loader | Fabric API | WorldEdit | Java |
|-----------|---------------|------------|-----------|------|
| 26.2      | 0.19.5+       | any        | 7.4.5+    | 25   |
| 26.3      | 0.19.5+       | any        | 7.4.6+    | 25   |

Download the jar that matches your Minecraft version from the [releases](../../releases). Install it
on the server (or in your singleplayer `mods` folder) next to WorldEdit and Fabric API. Players don't
need it on their client. Everything is undoable with `//undo` and uses the normal WorldEdit
permissions (`worldedit.region.replace` for `//texture`, `worldedit.clipboard.paste` for the pastes).

## Commands

### `//texture <block,block,...> [only-this-block] [-s size]`
Re-textures your WorldEdit selection with the listed blocks, spread by 3D Perlin noise so
they form natural blotches rather than static.

```
//texture stone,andesite,gravel
//texture mossy_cobblestone,cobblestone,stone cobblestone     # only cobblestone is re-textured
//texture oak_stairs[facing=north],stone -s 12                # block states work; -s = blotch size
```

* By default every non-air block in the selection is replaced; air is left alone.
* The optional second argument limits the change to that block (several, comma-separated, also work).
* The noise follows the order you list the blocks in. Repeat a block to make it more common
  (`stone,stone,andesite`).
* `-s <size>` is the approximate blotch size in blocks (default 6, range 1-1024).
* Each run uses a new random seed.

### `//paste-equaly <x|y|z|a|mix> [-a]`  (also `//paste-equally`)
Pastes your clipboard at your position, **except** that the chosen axes are taken from where the
clipboard was originally copied. Combine axes freely.

```
//paste-equaly x     # same X as the original copy; Y and Z from where you stand
//paste-equaly xz    # same X and Z, your own height
//paste-equaly a     # all three: pasted back exactly where it was copied
```
`-a` skips air blocks, like `//paste -a`. Rotation and flips (`//rotate`, `//flip`) are respected.

### `//paste-on-ground [-a] [-f]`
Pastes the clipboard at your X/Z with the bottom of the copied area resting on the ground.
Ground is the nearest solid block at or below you, ignoring leaves, logs and liquids, so it also
works in caves and the Nether.

* `-a` skips air blocks.
* `-f` fits to the highest ground under the whole footprint, so nothing ends up buried in a hill.
* Rotation and flips are respected (a vertically flipped build is lifted so it still sits on the ground).

## Building

Needs JDK 25. One source tree builds a jar per Minecraft version:

```sh
./gradlew build                      # latest supported version (26.3) -> build/libs/
./gradlew build -Pmc_target=26.2     # another version
./gradlew runSelftest -Pmc_target=26.2
```

Versions live in `versions/<mc>.properties`. `runSelftest` boots a real dev server with WorldEdit and
this mod, runs every command through a fake op player and checks the resulting blocks (the test code in
`src/selftest` is never packaged into the jar).

## Releasing

`.github/workflows/build-release.yml` does everything:

* every push and pull request: build, unit-test and in-game self-test on 26.2 and 26.3;
* push to `main`: also tags `v<mod_version>` and publishes a GitHub release with both jars. Change
  `mod_version` in `gradle.properties` to cut a new release (an already-released version is skipped);
* pushing a `v*` tag by hand, or running the workflow manually with "release" ticked, works too.

To support a new Minecraft version, add `versions/<mc>.properties` and put the version in the workflow's matrix.

## License
MIT
