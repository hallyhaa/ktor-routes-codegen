plugins {
    kotlin("jvm") version "2.2.0"
    `maven-publish`
}

group = "org.babelserver.ktor"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.google.devtools.ksp:symbol-processing-api:2.2.0-2.0.2") // https://central.sonatype.com/artifact/com.google.devtools.ksp/symbol-processing-api/versions
    implementation("com.squareup:kotlinpoet:2.2.0") // https://central.sonatype.com/artifact/com.squareup/kotlinpoet/versions
    implementation("com.squareup:kotlinpoet-ksp:2.2.0") // https://central.sonatype.com/artifact/com.squareup/kotlinpoet-ksp/versions
    implementation("io.ktor:ktor-server-core:3.2.3") // https://ktor.io/
    
    testImplementation("org.jetbrains.kotlin:kotlin-test:2.2.0")
    testImplementation("com.github.tschuchortdev:kotlin-compile-testing-ksp:1.6.0")
    testImplementation("io.ktor:ktor-server-test-host:3.2.3")
}

tasks.test {
    useJUnitPlatform()
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}