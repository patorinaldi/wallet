plugins {
    `java-library`
}

dependencies {
    api("org.springframework.boot:spring-boot-starter-validation")
    api("org.springframework.kafka:spring-kafka")
    api("org.springframework.boot:spring-boot-starter-data-jpa")
    api("org.springframework.boot:spring-boot-starter-web")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310")
}