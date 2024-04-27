import groovy.util.Node
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    java apply true
    alias(libs.plugins.springboot.plugin) apply false
    alias(libs.plugins.spring.dependency.management)
    kotlin("jvm")
}


plugins.withType<JavaPlugin> {
    extensions.configure<JavaPluginExtension> {
    }
    val springBootVersion: String by project
    dependencies {
        implementation(platform(libs.springboot.dependencies))
        implementation(libs.springboot.logging)
    }
}

extensions.configure<PublishingExtension> {
    publications {
        create<MavenPublication>("parentJava") {
            from(components["java"])


            pom.withXml {
                asNode()
                    .appendNode("parent")
                    .apply {
                        appendNode("groupId", libs.springboot.parent.get().group)
                        appendNode("artifactId", libs.springboot.parent.get().name)
                        appendNode("version", libs.versions.springboot.version.get())
                        appendNode("relativePath")
                    }
            }
            pom.packaging = "pom"
            pom.properties.put("lg5.version", "\${project.parent.version}")
            pom.withXml {
                asNode()
                    .appendNode("build").apply {
                        appendNode("pluginManagement")
                            .appendNode("plugins").apply {
                                avroPlugin()
                                jibMavenPlugin()
                                springBootMavenBuildImagePlugin()
                            }
                        appendNode("plugins").apply {
                            mavenCompilerPlugin()
                            jacocoPlugin()
                            mavenCheckstylePlugin()

                        }
                    }
                asNode()
                    .profiles()
            }
        }
    }
    tasks.withType<PublishToMavenLocal> {
        onlyIf {
            publication == publishing.publications["parentJava"]
        }
    }
    tasks.withType<PublishToMavenRepository> {
        onlyIf {
            publication == publishing.publications["parentJava"]
        }
    }
}


tasks.withType<KotlinCompile> {
    kotlinOptions {
        freeCompilerArgs = listOf(
            "-Xjsr305=strict"
        )
        jvmTarget = "21"
    }
}
tasks.jar {
    enabled = true
    from("checkstyle.xml")
}
dependencies {
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(21)
}

fun Node.mavenCompilerPlugin() {

    appendNode("plugin").apply {
        appendNode("groupId", "org.apache.maven.plugins")
        appendNode("artifactId", "maven-compiler-plugin")
        appendNode("version", libs.versions.maven.compiler.plugin.version.get())
        appendNode("configuration")
            .appendNode("release", 21)

    }
}

fun Node.jacocoPlugin() {
    appendNode("plugin").apply {
        appendNode("groupId", "org.jacoco")
        appendNode("artifactId", "jacoco-maven-plugin")
        appendNode("version", libs.versions.jacoco.version.get())

        appendNode("executions").apply {

            appendNode("execution").apply {
                appendNode("id", "prepare-agent").children().apply {
                    appendNode("goals")
                        .appendNode("goal", "prepare-agent")
                }
            }
        }
        appendNode("configuration")
            .appendNode("excludes").apply {
                appendNode("exclude", "**/AvroModel.")
                appendNode("exclude", "**/**.kafka.*")
                appendNode("exclude", "infrastructure/kafka/*")
            }
    }
}

fun Node.avroPlugin() {
    appendNode("plugin").apply {
        appendNode("groupId", libs.avro.plugin.get().group)
        appendNode("artifactId", libs.avro.plugin.get().name)
        appendNode("version", libs.versions.avro.version.get())


        appendNode("configuration").apply {
            appendNode("stringType", "String")
            appendNode("enableDecimalLogicalType", "true")
        }

        appendNode("executions").apply {
            appendNode("execution").apply {

                appendNode("phase", "generate-resources")
                appendNode("goals")
                    .appendNode("goal", "schema")


                appendNode("configuration").apply {
                    appendNode("sourceDirectory", "src/main/resources/avro")
                    appendNode("outputDirectory", "src/main/java")
                }
            }
        }
    }
}

fun Node.mavenCheckstylePlugin() {
    appendNode("plugin").apply {
        appendNode("groupId", libs.checkstyle.plugin.get().group)
        appendNode("artifactId", libs.checkstyle.plugin.get().name)
        appendNode("version", libs.checkstyle.plugin.get().version)

        appendNode("dependencies")
            .appendNode("dependency").apply {
                appendNode("groupId", libs.puppycrawl.tools.get().group)
                appendNode("artifactId", libs.puppycrawl.tools.get().name)
                appendNode("version", libs.puppycrawl.tools.get().version)
            }

        appendNode("executions").apply {
            appendNode("execution").apply {

                appendNode("id", "validate")
                appendNode("phase", "validate")
                appendNode("goals")
                    .appendNode("goal", "check")


                appendNode("configuration").apply {
                    appendNode("configLocation", "./checkstyle.xml")
                    appendNode("consoleOutput", "true")
                    appendNode("failsOnError", "true")
                    appendNode("violationSeverity", "warning")
                }
            }
        }
    }
}

fun Node.jibMavenPlugin() {
    appendNode("plugin").apply {
        appendNode("groupId", libs.jib.plugin.get().group)
        appendNode("artifactId", libs.jib.plugin.get().name)
        appendNode("version", libs.jib.plugin.get().version)

        appendNode("configuration")
            .appendNode("from").apply {
                appendNode("image", "gcr.io/distroless/java17-debian12")
                appendNode("platforms")
                    .appendNode("platform").apply {
                        appendNode("architecture", "\${docker.from.image.platform.architecture}")
                        appendNode("os", "\${docker.from.image.platform.os}")
                    }
            }

        appendNode("executions")
            .appendNode("execution").apply {

                appendNode("id", "build-image")
                appendNode("phase", "post-integration-test")
                appendNode("goals")
                    .appendNode("goal", "dockerBuild")


                appendNode("configuration").apply {
                    appendNode("to").appendNode(
                        "image",
                        "\${project.groupId}/\${parent.artifactId}:\${project.version}"
                    )
                    appendNode("container").apply {
                        appendNode("creationTime", "USE_CURRENT_TIMESTAMP")
                        appendNode("jvmFlags").apply {
                            appendNode("jvmFlag", "-Duser.timezone=UTC")
                            appendNode("jvmFlag", "-XX:+PrintFlagsFinal")
                            appendNode("jvmFlag", "-XX:MaxRAMPercentage=50")
                        }
                    }
                }
            }
    }
}

fun Node.springBootMavenBuildImagePlugin() {
    appendNode("plugin").apply {
        appendNode("groupId", libs.springboot.parent.get().group)
        appendNode("artifactId", "spring-boot-maven-plugin")

        appendNode("configuration").apply {
            appendNode("image")
                .appendNode("name", "\${project.groupId}/\${project.parent.artifactId}:\${project.version}")
            appendNode("createdDate", "now")
            appendNode("skip", "false")
        }

        appendNode("executions")
            .appendNode("execution").apply {
                appendNode("phase", "install")
                appendNode("goals")
                    .appendNode("goal", "build-image")
            }

    }
}

fun Node.profiles() {
    appendNode("profiles")
        .appendNode("profile").apply {

            appendNode("id", "arch-aarch64")

            appendNode("activation")
                .appendNode("os")
                .appendNode("arch", "aarch64")
            appendNode("properties")
                .appendNode("docker.from.image.platform.architecture", "arm64")
        }
}