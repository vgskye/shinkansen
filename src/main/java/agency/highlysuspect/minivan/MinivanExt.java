package agency.highlysuspect.minivan;

import agency.highlysuspect.minivan.prov.MinecraftProvider;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;

import java.io.File;
import java.util.stream.Collectors;

public class MinivanExt {
	public MinivanExt(Project project) {
		this.project = project;
		
		this.offline = project.getGradle().getStartParameter().isOffline() ||
			project.hasProperty("minivan.offline") ||
			System.getProperty("minivan.offline") != null;
		
		this.refreshDependencies = project.getGradle().getStartParameter().isRefreshDependencies() ||
			project.hasProperty("minivan.refreshDependencies") ||
			System.getProperty("minivan.refreshDependencies") != null;
		
		this.explainHashes = project.hasProperty("minivan.explainHashes") ||
			System.getProperty("minivan.explainHashes") != null;
		
		this.accessWideners = project.getObjects().fileCollection();
	}
	
	private final Project project;
	public boolean offline, refreshDependencies, explainHashes;
	
	/// VanillaGradle-ish API ///
	
	public String version = null;
	public ConfigurableFileCollection accessWideners;
	
	@SuppressWarnings("unused")
	public MinivanExt version(String v) {
		version = v;
		return this;
	}
	
	@SuppressWarnings("unused")
	public MinivanExt accessWideners(Object... aws) {
		accessWideners.from(aws);
		return this;
	}
	
	void setupAfterEvaluate() {
		//this part is where the magic happens:
		project.afterEvaluate(__ -> {
			if(version == null) return; //didnt set any version
			
			//TODO is this right?
			// the goal is to kick the cache's tires a little when you change the AW file
			Task build = project.getTasks().findByPath("build");
			if(build != null) build.dependsOn(accessWideners);
			
			minecraftBuilder()
				.version(version)
				.accessWidener(accessWideners)
				.build()
				.tryGetMinecraft()
				.installTo(project, "compileOnly");
		});
	}
	
	/// Lower-level, direct API ///
	
	public MinecraftBuilder minecraftBuilder() {
		return new MinecraftBuilder(project);
	}
	
	public MinecraftBuilder minecraftBuilder(String version) {
		return minecraftBuilder().version(version);
	}
	
	public static class MinecraftBuilder {
		private MinecraftBuilder(Project project) {
			this.project = project;
			this.accessWideners = project.getObjects().fileCollection();
		}
		
		private final Project project;
		public String version;
		public ConfigurableFileCollection accessWideners;
		
		public MinecraftBuilder version(String version) {
			this.version = version;
			return this;
		}
		
		public MinecraftBuilder accessWideners(Object... aws) {
			this.accessWideners.from(aws);
			return this;
		}
		
		//singular alias
		public MinecraftBuilder accessWidener(Object aw) {
			return accessWideners(aw);
		}
		
		public MinecraftProvider build() {
			return new MinecraftProvider(project, version, accessWideners
				.getFiles()
				.stream()
				.map(File::toPath)
				.collect(Collectors.toList())
			);
		}
	}
	
	/// Deprecated API ///
	
	/**
	 * @apiNote This API isn't expandable and doesn't provide any way to select access wideners.
	 *          It will still be supported, but minecraftBuilder() is more flexible.
	 * @see MinivanExt#minecraftBuilder()
	 */
	@SuppressWarnings("unused")
	public MinecraftProvider.Result getMinecraft(String version) throws Exception {
		return minecraftBuilder().version(version).build().getMinecraft();
	}
	
	/**
	 * @apiNote This API isn't expandable and doesn't provide any way to select access wideners.
	 *          It will still be supported, but minecraftBuilder() is more flexible.
	 * @see MinivanExt#minecraftBuilder()
	 */
	public MinecraftProvider.Result tryGetMinecraft(String version) {
		return minecraftBuilder().version(version).build().tryGetMinecraft();
	}
}
