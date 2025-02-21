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
}

repositories {
    mavenCentral()
}

dependencies {
    api(project(":contexts-core"))

    api(project(":contexts-module-events"))
    api(project(":contexts-module-filters"))

    implementation(libs.byte.buddy)

    testImplementation(libs.junit.jupiter)
}

tasks.test {
    useJUnitPlatform()
}