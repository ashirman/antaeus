plugins {
    kotlin("jvm")
}

kotlinProject()

dependencies {
    implementation(project(":pleo-antaeus-data"))
    compile(project(":pleo-antaeus-models"))
    implementation("org.quartz-scheduler:quartz:2.3.1")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.9.8")
}