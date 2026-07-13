plugins {
    java
}

tasks.withType<JavaCompile> {
    options.release = 25
}

dependencies {
    subprojects.forEach {
        implementation(it)
    }
}

tasks.named<Jar>("jar") {
    val moduleJarTasks = subprojects.map({it.tasks.jar})
    dependsOn(moduleJarTasks)
    from({
        moduleJarTasks.map({zipTree{it.get().archiveFile}})
    })
}
