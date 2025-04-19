package vg.skye.shinkansen.prov;

import vg.skye.shinkansen.ShinkansenPlugin;
import vg.skye.shinkansen.stitch.ClassMergerCooler;
import vg.skye.shinkansen.stitch.JarMergerCooler;
import org.gradle.api.Project;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;

public class Merger extends MiniProvider {
	public Merger(Project project, Path client, Path server, String mergedName) {
		super(project);
		this.client = client;
		this.server = server;
		this.mergedName = mergedName;
	}
	
	public final Path client, server;
	public final String mergedName;
	
	public Path merge() throws Exception {
		Path p = getOrCreate(subst(mergedName), merged -> {
			log.lifecycle("Merging {} and {} to {}", client, server, merged);
			
			try(
					FileSystem clientFs = ShinkansenPlugin.openFs(client);
					FileSystem serverFs = ShinkansenPlugin.openFs(server);
					FileSystem mergedFs = ShinkansenPlugin.createFs(merged);
					InputStream bonus1 = Merger.class.getResourceAsStream("/vg/skye/shinkansen/side/Side.class");
					InputStream bonus2 = Merger.class.getResourceAsStream("/vg/skye/shinkansen/side/SideOnly.class")
			) {
				JarMergerCooler merger = new JarMergerCooler(clientFs, serverFs, mergedFs);
				merger.merge(new ClassMergerCooler()
					.sideEnum("Lvg/skye/shinkansen/side/Side;")
					.sideDescriptorAnnotation("Lshinkansen/side/SideOnly;")
				);
				
				//Free bonus classes:
				Files.createDirectories(mergedFs.getPath("vg/skye/shinkansen/side"));
				blah(bonus1, mergedFs.getPath("/vg/skye/shinkansen/side/Side.class"));
				blah(bonus2, mergedFs.getPath("/vg/skye/shinkansen/side/SideOnly.class"));
			}
			
			log.lifecycle("\\-> Done merging.");
		});
		log.info("merged: {}", p);
		return p;
	}
	
	private static void blah(InputStream in, Path outPath) throws IOException {
		try(OutputStream out = new BufferedOutputStream(Files.newOutputStream(outPath))) {
			byte[] buf = new byte[4096];
			int read;
			while((read = in.read(buf)) > 0) out.write(buf, 0, read);
		}
	}
}
