import net.labymod.labygradle.common.extension.LabyModAnnotationProcessorExtension.ReferenceType

dependencies {
    labyProcessor()
    api(project(":api"))

    // Pure-Java WebP decoder (lossy VP8, lossless VP8L and animated VP8X/ANMF).
    // 7TV only serves WEBP/AVIF, and neither Java's ImageIO nor LabyMod's texture loader can decode
    // WebP on their own. TwelveMonkeys has no native code, so it works on every OS LabyMod runs on.
    addonMavenDependency("com.twelvemonkeys.imageio:imageio-webp:3.12.0")
}

labyModAnnotationProcessor {
    referenceType = ReferenceType.DEFAULT
}
