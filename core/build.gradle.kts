import net.labymod.labygradle.common.extension.LabyModAnnotationProcessorExtension.ReferenceType

dependencies {
    labyProcessor()
    api(project(":api"))

    addonMavenDependency("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
}

labyModAnnotationProcessor {
    referenceType = ReferenceType.DEFAULT
}
