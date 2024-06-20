package agency.highlysuspect.minivan.prov;

import net.fabricmc.tinyremapper.IMappingProvider;
import net.fabricmc.tinyremapper.OutputConsumerPath;
import net.fabricmc.tinyremapper.TinyRemapper;
import org.cadixdev.lorenz.MappingSet;
import org.cadixdev.lorenz.io.MappingsReader;
import org.cadixdev.lorenz.io.proguard.ProGuardFormat;
import org.cadixdev.lorenz.model.ClassMapping;
import org.cadixdev.lorenz.model.FieldMapping;
import org.cadixdev.lorenz.model.MethodMapping;
import org.gradle.api.Project;

import java.nio.file.Path;

public class RemapperPrg extends MiniProvider {
	public RemapperPrg(Project project, Path inJar, Path mapFile, String outJarName) {
		super(project);
		this.inJar = inJar;
		this.mapFile = mapFile;
		this.outJarName = outJarName;
	}
	
	public final Path inJar;
	public final Path mapFile;
	public final String outJarName;
	
	public Path remap() throws Exception {
		Path result = getOrCreate(subst(outJarName), outJar -> {
			log.lifecycle("Remapping {} to {} using {}", inJar, outJar, mapFile);
			
			//lorenz
			MappingSet prg;
			try(MappingsReader reader = new ProGuardFormat().createReader(mapFile)) {
				log.lifecycle("\\-> Parsing mappings...");
				prg = reader.read();
				
				log.lifecycle("\\-> Flipping mappings...");
				prg = prg.reverse();
			}
			
			//tiny-remapper
			log.lifecycle("\\-> Building tiny-remapper...");
			TinyRemapper remapper = TinyRemapper.newRemapper()
				.renameInvalidLocals(true)
				.rebuildSourceFilenames(true)
				.withMappings(toIMappingProvider(prg)) 
				.build();
			
			try(OutputConsumerPath oc = new OutputConsumerPath.Builder(outJar).assumeArchive(true).build()) {
				log.lifecycle("\\-> Copying non-class files...");
				oc.addNonClassFiles(inJar);
				
				log.lifecycle("\\-> Reading jar...");
				remapper.readInputs(inJar);
				
				//log.lifecycle("\\-> Remapping...");
				remapper.apply(oc);
			} finally {
				remapper.finish();
			}
		});
		log.info("mapped: {}", result);
		return result;
	}
	
	//glue code between lorenz and tiny-remapper
	private IMappingProvider toIMappingProvider(MappingSet lorenzSet) {
		return acceptor -> {
			log.info("\\-> Loading mappings into tiny-remapper...");
			lorenzSet.getTopLevelClassMappings().forEach(tlcm -> visitClass(acceptor, tlcm));
			log.info("\\-> Done loading mappings, remapping...");
		};
	}
	
	private void visitClass(IMappingProvider.MappingAcceptor acceptor, ClassMapping<?, ?> classMapping) {
		acceptor.acceptClass(classMapping.getFullObfuscatedName(), classMapping.getFullDeobfuscatedName());
		
		for(FieldMapping fm : classMapping.getFieldMappings()) {
			acceptor.acceptField(new IMappingProvider.Member(
				classMapping.getFullObfuscatedName(),
				fm.getObfuscatedName(),
				fm.getType().map(Object::toString).orElse("Ljava/lang/Void;")
			), fm.getDeobfuscatedName());
		}
		
		for(MethodMapping mm : classMapping.getMethodMappings()) {
			acceptor.acceptMethod(new IMappingProvider.Member(
				classMapping.getFullObfuscatedName(),
				mm.getObfuscatedName(),
				mm.getObfuscatedDescriptor()
			), mm.getDeobfuscatedName());
		}
		
		//recurse into inner classes
		for(ClassMapping<?, ?> cm : classMapping.getInnerClassMappings()) {
			visitClass(acceptor, cm);
		}
	}
}
