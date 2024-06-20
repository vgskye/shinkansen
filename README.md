# minivan

Bare-minimum Gradle plugin for putting vanilla Minecraft, remapped with official names, on the compilation classpath.

*Now with access widener support!*

# Audience

`minivan` is for people writing [jaredlll08/MultiLoader-Template](https://github.com/jaredlll08/MultiLoader-Template) -style mods who need something to fill out the compilation classpath on their `Common`/`Xplat` subproject, but don't need much else. Traditionally, this is done with the excellent [VanillaGradle](https://github.com/SpongePowered/VanillaGradle/) project.

However, while VanillaGradle is very general-purpose, `minivan` is designed specifically for the needs of Fabric and Forge modders. `minivan` does not download assets/natives, does **not** contain a `runClient`/`runServer` task, and does not contain a nice decompiler, under the expectation that your Fabric and Forge projects already have everything you need. This allows `minivan` to be simple, fast, and leave your workspace free from extraneous copies of the Minecraft assets.

`minivan` also does **not** perform reobfuscation; any code built against `minivan`ned-minecraft dependencies will be compiled and published with names as they appear in your IDE.

# Usage

## Quick start

<details><summary>Sample buildscript</summary>

Your (sub)project's `build.gradle`

```gradle
buildscript {
	repositories {
		mavenCentral()
		maven { url "https://maven.fabricmc.net/" }
		maven { url "https://repo.sleeping.town/" }
	}
	dependencies {
		classpath "agency.highlysuspect:minivan:0.4"
	}
}

apply plugin: "java"
apply plugin: "agency.highlysuspect.minivan"

minivan {
	version("1.20.1")
}
```

</details>

<details><summary>Sample buildscript using "plugins dsl"</summary>

Your root project's `settings.gradle`:

```gradle
pluginManagement {
	repositories {
		gradlePluginPortal() //i think
		maven { url "https://maven.fabricmc.net/" }
		maven { url "https://repo.sleeping.town/" }
	}
}
```

Your (sub)project's `build.gradle`:

```gradle
plugins {
	id "java"
	id "agency.highlysuspect.minivan" version "0.4"
}

minivan {
	version("1.20.1")
}
```

</details>

Either of these buildscripts will cause the Minecraft 1.20.1 client and server to be downloaded, remapped to official names, merged, and stuck onto the `compileOnly` configuration along with all its dependencies (LWJGL, etc). This'll happen in `afterEvaluate`. See `./demo` for a worked example.

To apply Fabric-style access wideners, do this: (available since `0.5`)

```gradle
minivan {
  version("1.20.1")
  accessWideners "./path/to/your.accesswidener"
}
```

Note that this will also "projectmap" your Minecraft jar, i.e. it'll get dropped into the project-specific `.gradle/cache` dir instead of the global Gradle cache.

## Nuts and bolts

Using the `minivan { }` block is optional. For a lower-level imperative experience, try the `minivan.minecraftBuilder` function (available since `0.5`) instead:

```gradle
//this object is a `agency.highlysuspect.minivan.prov.MinecraftProvider.Result`:
def mc = minivan.minecraftBuilder()
  .version("1.20.1")
  .build()
  .getMinecraft();

//java.nio.Path to the "minecraft, remapped and merged" jar on your computer
println("merged minecraft jar: ${mc.minecraft}")

//List<String>, maven-style coordinates to Minecraft's dependencies
//(psst- the plugin automatically adds Mojang's maven and mavenCentral, so these should just work) 
println("maven dependency count: ${mc.dependencies.size()}")

//let's add Minecraft to the compileOnly classpath manually:
project.dependencies.add("compileOnly", project.files(mc.minecraft))
mc.dependencies.forEach { project.dependencies.add("compileOnly", it) }
```

`.minecraftBuilder` is defined in `MinivanExt`. You can call `.version()` to set the version and `.accessWideners()` to add AW files.

After that, call `.build().getMinecraft()`, and that'll take care of the downloading/remapping/merging/AWs for you. What you do with the data is up to you. (Try grabbing a few jars and pumping them into [crossroad](https://github.com/CrackedPolishedBlackstoneBricksMC/crossroad)?)

(Older versions of the plugin, since `0.2`, support a free-standing `minivan.getMinecraft(version)` function. This is soft-deprecated, because it's still a useful function but there's no way to further configure it.)

## Other options

Useful information is logged at the `--info` level.

* If you pass `--refresh-dependencies`, pass `-Dminivan.refreshDependencies=true`, or set `minivan { refreshDependencies = true }` in-script, all derived artifacts will be deleted and recomputed.
* If you pass `--offline`, pass `-Dminivan.offline`, or set `minivan { offline = true }` in-script, `minivan` will now error-out instead of making network connections.
* If you pass `-Dminivan.explainHashes` or set `minivan { explainHashes = true }` in-script, additional files will be dumped into your Gradle cache explaining where the hashes in the cached artifact's filenames come from.

The `minivan`-specific ones only affect `minivan` and not the other things in Gradle that are controlled by those switches.

## Migrating from VanillaGradle

* Swap the plugin invocation to `agency.highlysuspect.minivan`.
* Change `minecraft {` to `minivan {`.

There are two supported functions inside the `minivan` block, `version` and `accessWideners`. No Gradle tasks are added.

Important note for IntelliJ users:

* Right click on any project or task in the Gradle Tool Window and select "Tasks Activation". There is surely an easier way to get to this dialog but I can't find it.
* Remove all task activations that refer to running a `:prepareWorkspace` task after syncing.
  * This will probably be "all of them".
  * `minivan` does not add this task, so IntelliJ sync will fail until this activation is removed.

# things that this plugin glues together

The neat thing about writing Gradle tooling for Minecraft in ~~2023~~ 2024 is that everyone has already wrote everything by now. It's just a matter of assembling other people's libraries in the right way with the right glue code. This plugin is like 5% original work by-weight. Most of the heavy lifting is done by:

* [CadixDev/Lorenz](https://github.com/CadixDev/Lorenz) and `lorenz-io-proguard` parse Mojang's official mappings.
* [FabricMC/tiny-remapper](https://github.com/FabricMC/tiny-remapper) is the jar remapper of choice.
* A modified version of a tool from [FabricMC/stitch](https://github.com/FabricMC/stitch) merges the client and server jars.
* [FabricMC/access-widener](https://github.com/FabricMC/access-widener) parses and applies access wideners.

The general approach (`prov` package) has been copied from how I ended up structuring [voldeloom](https://github.com/CrackedPolishedBlackstoneBricksMC/voldeloom/).

# todo

* that stuff i commented out in JarMergerCooler in voldeloom might need to be brought back lol (⛄)
* Parchment stuff:
  * param-name mappings
  * javadoc (which requires implementing `genSources`, doing linemapping, etc. voldeloom has mosta that stuff)
  * mh. Hard. Loom supports Parchment, if you really want Patchment you can open your Loom project's sources.
* Hmm: optional mode that tries to use a cached jar from `fabric-loom`, to save RAM

# License

MIT