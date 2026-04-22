plugins {
    java
    war
}

repositories {
    mavenCentral()
}

dependencies {
    // Jakarta EE (provided by TomEE at runtime)
    compileOnly("jakarta.platform:jakarta.jakartaee-api:10.0.0")

    // MariaDB JDBC driver (MUST be included in WAR)
    implementation("org.mariadb.jdbc:mariadb-java-client:3.3.3")

    // Password hashing
    implementation("at.favre.lib:bcrypt:0.10.2")

    // Optional (you can remove if not used)
    implementation("com.google.guava:guava:33.2.1-jre")

    // Testing
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(19))
    }
}

tasks.test {
    useJUnitPlatform()
}

/**
 * 🔥 IMPORTANT: ensures ALL runtime dependencies
 * (including MariaDB driver) go into WEB-INF/lib
 */
tasks.war {
    from("src/main/webapp")

    // This line fixes your driver issue
    classpath(configurations.runtimeClasspath)
}