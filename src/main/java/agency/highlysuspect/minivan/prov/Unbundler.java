package agency.highlysuspect.minivan.prov;


import agency.highlysuspect.minivan.Util;
import org.gradle.api.Project;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Unbundler extends MiniProvider {
	public Unbundler(Project project, Path bundledJar, String unbundledJarName) {
		super(project);
		this.bundledJar = bundledJar;
		this.unbundledJarName = unbundledJarName;
		
		//cachebusting from before the unbundler existed
		props.set("unbundlerInPipeline", "true");
	}
	
	final Path bundledJar;
	final String unbundledJarName;
	
	public Path unbundle() throws Exception {
		Path unbundled = getOrCreate(subst(unbundledJarName), unbundledJar -> {
			log.lifecycle("Unbundling {} to {}...", bundledJar, unbundledJar);
			
			try(ZipFile zf = new ZipFile(bundledJar.toFile())) {
				Optional<? extends ZipEntry> innerJar = zf.stream()
					.filter(e -> e.getName().startsWith("META-INF/versions/") && e.getName().endsWith(".jar"))
					.findFirst();
				
				if(innerJar.isPresent()) {
					log.lifecycle("\\-> Found inner jar: {}", innerJar.get());
					InputStream in = zf.getInputStream(innerJar.get());
					Util.cpy(in, unbundledJar);
				} else {
					log.lifecycle("\\-> Did not find any inner jar to unbundle.");
					//No bundling
					//TODO dont copuy the jar if it doesn't need to be unbundled
					Files.copy(bundledJar, unbundledJar);
				}
			}
		});
		
		log.info("unbundled: {}", unbundled);
		
		return unbundled;
	}
}
