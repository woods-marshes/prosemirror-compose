import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.plugins.signing.SigningExtension

// Shared publishing configuration for the KMP library modules.
// Applied from prosemirror-compose/build.gradle.kts and
// prosemirror-compose-coil3/build.gradle.kts.

apply(plugin = "maven-publish")
apply(plugin = "signing")

group = providers.gradleProperty("GROUP").orElse("io.github.woods-marshes").get()
version = providers.gradleProperty("VERSION_NAME").orElse("0.1.0-SNAPSHOT").get()

fun gradlePropertyOrEnv(name: String, env: String): String? =
    (providers.gradleProperty(name).orNull ?: System.getenv(env))?.takeIf { it.isNotBlank() }

val pomUrl = providers.gradleProperty("POM_URL")
    .orElse("https://github.com/woods-marshes/prosemirror-compose")
    .get()
val pomInceptionYear = providers.gradleProperty("POM_INCEPTION_YEAR").orElse("2026").get()
val pomLicenseName =
    providers.gradleProperty("POM_LICENSE_NAME").orElse("Apache License 2.0").get()
val pomLicenseUrl = providers.gradleProperty("POM_LICENSE_URL")
    .orElse("https://www.apache.org/licenses/LICENSE-2.0")
    .get()
val pomLicenseDist = providers.gradleProperty("POM_LICENSE_DIST").orElse("repo").get()
val pomScmUrl = providers.gradleProperty("POM_SCM_URL")
    .orElse("https://github.com/woods-marshes/prosemirror-compose")
    .get()
val pomScmConnection = providers.gradleProperty("POM_SCM_CONNECTION")
    .orElse("scm:git:git://github.com/woods-marshes/prosemirror-compose.git")
    .get()
val pomScmDevConnection = providers.gradleProperty("POM_SCM_DEV_CONNECTION")
    .orElse("scm:git:ssh://git@github.com/woods-marshes/prosemirror-compose.git")
    .get()
val pomDeveloperId = providers.gradleProperty("POM_DEVELOPER_ID").orElse("woods-marshes").get()
val pomDeveloperName = providers.gradleProperty("POM_DEVELOPER_NAME").orElse("woods-marshes").get()
val pomDeveloperUrl = providers.gradleProperty("POM_DEVELOPER_URL")
    .orElse("https://github.com/woods-marshes")
    .get()
val pomDescription =
    (project.findProperty("POM_DESCRIPTION") as? String)
        ?: "Compose Multiplatform rich text editing backed by ProseMirror."

val publishingExtension = extensions.getByType<PublishingExtension>()

publishingExtension.publications.withType<MavenPublication>().configureEach {
    // Maven Central requires a javadoc jar on every publication. Native and
    // metadata publications have no JavaDoc sources, so an empty jar satisfies
    // the requirement without changing the KMP coordinate scheme.
    val javadocJar = tasks.register<org.gradle.api.tasks.bundling.Jar>("${name}JavadocJar") {
        archiveClassifier.set("javadoc")
    }
    artifact(javadocJar)

    pom {
        name.set(project.name)
        description.set(pomDescription)
        url.set(pomUrl)
        inceptionYear.set(pomInceptionYear)

        licenses {
            license {
                name.set(pomLicenseName)
                url.set(pomLicenseUrl)
                distribution.set(pomLicenseDist)
            }
        }

        scm {
            url.set(pomScmUrl)
            connection.set(pomScmConnection)
            developerConnection.set(pomScmDevConnection)
        }

        developers {
            developer {
                id.set(pomDeveloperId)
                name.set(pomDeveloperName)
                url.set(pomDeveloperUrl)
            }
        }
    }
}

val signingKeyId = gradlePropertyOrEnv("SIGNING_KEY_ID", "SIGNING_KEY_ID")
val signingKey = gradlePropertyOrEnv("SIGNING_KEY", "SIGNING_KEY")
val signingPassword = gradlePropertyOrEnv("SIGNING_PASSWORD", "SIGNING_PASSWORD")

if (signingKeyId != null && signingKey != null && signingPassword != null) {
    val signingExtension = extensions.getByType<SigningExtension>()
    signingExtension.useInMemoryPgpKeys(signingKeyId, signingKey, signingPassword)
    // KMP/AGP publications can be registered after this script runs, so sign
    // each publication as it is created rather than only the currently known set.
    publishingExtension.publications.withType<MavenPublication>().configureEach {
        signingExtension.sign(this)
    }
}
