package test;

// spotless:off
import java.util.function.Function;
// spotless:on
import java.util.function.Function;

public class Test {
    int i;

    @Deprecated
    public void test() {}

    private static void configureResolvedVersionsWithVersionMapping(Project project) {
        project.getPluginManager()
            .withPlugin(
                "maven-publish",
                plugin -> {
                    project.getExtensions()
                        .getByType(PublishingExtension.class)
                        .getPublications()
                        .withType(MavenPublication.class)
                        .configureEach(
                            publication ->
                                publication.versionMapping(
                                    mapping -> {
                                        mapping.allVariants(
                                            VariantVersionMappingStrategy
                                                ::fromResolutionResult);
                                    }));
                });
    }
}
