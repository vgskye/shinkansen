package agency.highlysuspect.minivan.prov;

import agency.highlysuspect.minivan.MinivanPlugin;
import agency.highlysuspect.minivan.VersionManifest;
import org.gradle.api.Project;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MinecraftProvider extends MiniProvider {
	public MinecraftProvider(Project project, String version) {
		super(project);
		this.version = version;
		this.accessWideners = new ArrayList<>();
	}
	
	public MinecraftProvider(Project project, String version, List<Path> accessWideners) {
		super(project);
		this.version = version;
		this.accessWideners = new ArrayList<>(accessWideners);
	}
	
	public final String version;
	public final List<Path> accessWideners;
	
	public Result getMinecraft() throws Exception {
		log.info("getting minecraft {}", version);
		
		//Fetch vanilla jars, official mappings, version manifest
		VanillaJarFetcher vanillaJarFetcher = new VanillaJarFetcher(project, version);
		VanillaJarFetcher.Result vanillaJars = vanillaJarFetcher.fetch();
		
		//Look for third-party libraries
		List<String> libs = new ArrayList<>();
		for(VersionManifest.Library lib : vanillaJars.versionManifest.libraries) {
			if(lib.isNative() || !lib.allowed()) continue;
			
			if(lib.name.contains("mojang:logging")) {
				continue; //TODO, this one is broken on my machine for some reason
			}
			
			libs.add(lib.getArtifactName());
			
			log.info("found vanilla dependency: {}", lib.getArtifactName());
		}
		
		//Remap client and server using official names
		String minecraftPrefix = "minecraft-" + MinivanPlugin.filenameSafe(version);
		
		RemapperPrg clientMapper = new RemapperPrg(project, vanillaJars.client, vanillaJars.clientMappings, minecraftPrefix + "-client-mapped{HASH}.jar");
		RemapperPrg serverMapper = new RemapperPrg(project, vanillaJars.server, vanillaJars.serverMappings, minecraftPrefix + "-server-mapped{HASH}.jar");
		clientMapper.dependsOn(vanillaJarFetcher);
		serverMapper.dependsOn(vanillaJarFetcher);
		
		Path clientMapped = clientMapper.remap();
		Path serverMapped = serverMapper.remap();
		
		//Merge client and server
		Merger merger = new Merger(project, clientMapped, serverMapped, minecraftPrefix + "-merged{HASH}.jar");
		merger.dependsOn(clientMapper);
		merger.dependsOn(serverMapper);
		
		Path finished = merger.merge();
		
		//Apply AWs
		if(!accessWideners.isEmpty()) {
			AW aw = new AW(project, finished, minecraftPrefix + "-merged-widened{HASH}.jar", accessWideners);
			aw.dependsOn(merger);
			
			finished = aw.widen();
		}
		
		return new Result(vanillaJars, finished, libs);
	}
	
	public Result tryGetMinecraft() {
		try {
			return getMinecraft();
		} catch (Exception e) {
			log.error("Failed to get minecraft {}: {}", version, e.getMessage(), e);
			throw new RuntimeException(e);
		}
	}
	
	public static class Result {
		public Result(VanillaJarFetcher.Result vanilla, Path minecraft, List<String> dependencies) {
			this.vanilla = vanilla;
			this.minecraft = minecraft;
			this.dependencies = dependencies;
		}
		
		public final VanillaJarFetcher.Result vanilla;
		public final Path minecraft;
		public final List<String> dependencies;
		
		public void installTo(Project project, String configurationName) {
			installMinecraftTo(project, configurationName);
			installDependenciesTo(project, configurationName);
		}
		
		public void installMinecraftTo(Project project, String configurationName) {
			project.getLogger().info("adding {} to configuration '{}'", minecraft, configurationName);
			project.getDependencies().add(configurationName, project.files(minecraft));
		}
		
		public void installDependenciesTo(Project project, String configurationName) {
			dependencies.forEach(dep -> {
				project.getLogger().info("adding {} to configuration '{}'", dep, configurationName);
				project.getDependencies().add(configurationName, dep);
			});
		}
		
		@Override
		public String toString() {
			return String.format("MinecraftProvider.Result{minecraft=%s, dependencies=[\"%s\"]}", minecraft, String.join("\",\"", dependencies));
		}
	}
}
