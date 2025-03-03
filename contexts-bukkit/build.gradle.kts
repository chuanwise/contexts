/*
 * Copyright 2025 Chuanwise and contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.blocking.bridge)
    alias(libs.plugins.shadow)
    `maven-publish`
}

repositories {
    mavenCentral()

    maven("https://hub.spigotmc.org/nexus/content/repositories/public/")
    maven("https://hub.spigotmc.org/nexus/content/repositories/snapshots/")
}

dependencies {
    api(project(":contexts-core"))
    api(project(":contexts-events"))
    api(project(":contexts-filters"))

    compileOnly(libs.spigot.api)
    implementation(libs.byte.buddy)

    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}

tasks.processResources {
    filesMatching("plugin.yml") {
        expand(
            mapOf(
                "authors" to listOf("Chuanwise"),
                "version" to project.version
            )
        )
    }
}

tasks.shadowJar {
    mergeServiceFiles()
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifactId = project.name

            artifact(tasks.kotlinSourcesJar)

            from(components["java"])
        }
    }
}