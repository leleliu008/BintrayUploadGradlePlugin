package com.fpliu.gradle

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.LibraryPlugin
import com.jfrog.bintray.gradle.BintrayExtension
import com.jfrog.bintray.gradle.BintrayUploadTask
import java.io.File
import java.util.*
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.execution.TaskExecutionAdapter
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.JavaPluginConvention
import org.gradle.api.plugins.MavenRepositoryHandlerConvention
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.task
import org.gradle.kotlin.dsl.withGroovyBuilder

class BintrayUploadPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        project.extensions.create<BintrayUploadExtension>("bintrayUploadExtension", BintrayUploadExtension::class.java)
        project.afterEvaluate(Project::afterEvaluate)
        project.gradle.addListener(InstallTaskBeforeExecuteListener())
    }
}

class InstallTaskBeforeExecuteListener : TaskExecutionAdapter() {

    override fun beforeExecute(task: Task) {
        super.beforeExecute(task)

        if (task is BintrayUploadTask) {
            attachBintrayUserAndKey(task)
        } else if (task is Upload) {
            val project = task.project
            task.configuration = project.configurations.getByName("archives")
            DslObject(task.repositories).convention.getPlugin(MavenRepositoryHandlerConvention::class.java).apply {
                mavenInstaller {
                    it.pom.project {
                        it.withGroovyBuilder {
                            val bintrayUploadExtension = project.getBintrayUploadExtension()
                            "packaging"(if (project.plugins.hasPlugin(LibraryPlugin::class.java)) "aar" else "jar")
                            "artifactId"(bintrayUploadExtension.archivesBaseName)
                            "name"(bintrayUploadExtension.archivesBaseName)
                            "url"(bintrayUploadExtension.projectSiteUrl)
                            "licenses" {
                                "license" {
                                    "name"(bintrayUploadExtension.licenseName)
                                    "url"(bintrayUploadExtension.licenseUrl)
                                }
                            }
                            "developers" {
                                "developer" {
                                    "id"(bintrayUploadExtension.developerName)
                                    "name"(bintrayUploadExtension.developerName)
                                    "email"(bintrayUploadExtension.developerEmail)
                                }
                            }
                            "scm" {
                                "connection"(bintrayUploadExtension.projectGitUrl)
                                "developerConnection"(bintrayUploadExtension.projectGitUrl)
                                "url"(bintrayUploadExtension.projectSiteUrl)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun Project.getBintrayUploadExtension() = extensions.getByType(BintrayUploadExtension::class.java).apply {
    if (isEmpty(archivesBaseName)) {
        archivesBaseName = rootProject.name
    }

    if (isEmpty(projectSiteUrl)) {
        projectSiteUrl = "https://github.com/$developerName/$archivesBaseName"
    }

    if (isEmpty(projectGitUrl)) {
        projectGitUrl = "https://github.com/$developerName/$archivesBaseName"
    }
}

private fun Project.buildBintrayExtension(bintrayUploadExtension: BintrayUploadExtension) = extensions.getByType(BintrayExtension::class.java).apply {
    setConfigurations("archives")
    pkg = PackageConfig().apply {
        userOrg = bintrayUploadExtension.bintrayOrganizationName
        repo = bintrayUploadExtension.bintrayRepositoryName
        name = bintrayUploadExtension.archivesBaseName
        websiteUrl = bintrayUploadExtension.projectSiteUrl
        vcsUrl = bintrayUploadExtension.projectGitUrl
        setLicenses("Apache-2.0")
        publish = true
    }
}

fun attachBintrayUserAndKey(bintrayUploadTask: BintrayUploadTask) {
    val userHomeDir = System.getProperty("user.home")
    val bintrayPropertiesFile = File("$userHomeDir/.bintray.properties")
    if (bintrayPropertiesFile.exists()) {
        val properties = Properties().apply { load(bintrayPropertiesFile.inputStream()) }
        val user = properties.getProperty("bintray.user")
        val key = properties.getProperty("bintray.apikey")
        if (isEmpty(user) || isEmpty(key)) {
            throw RuntimeException("please config $userHomeDir/.bintray.properties first!")
        }
        bintrayUploadTask.user = user
        bintrayUploadTask.apiKey = key
    } else {
        bintrayPropertiesFile.writeText("bintray.user=\nbintray.apikey=")
        throw RuntimeException("please config $userHomeDir/.bintray.properties first!")
    }
}

private fun Project.afterEvaluate() {
    printLog("afterEvaluate()")

    val bintrayUploadExtension = getBintrayUploadExtension()
    buildBintrayExtension(bintrayUploadExtension)

    val baseName = bintrayUploadExtension.archivesBaseName
    convention.getPlugin(BasePluginConvention::class.java).archivesBaseName = baseName

    // 注意：这里很可能是null，比如，这是一个普通的基于JVM的工程，而不是Android工程
    val android = extensions.findByType(LibraryExtension::class.java)
    val java = convention.getPlugin(JavaPluginConvention::class.java)

    val src = if (android == null) {
        java.sourceSets.getByName("main").java.srcDirs
    } else {
        android.sourceSets.getByName("main").java.srcDirs
    }

    // 生成${baseName}-${version}-sources.jar
    val genSourcesJarTask = task("genSourcesJar", Jar::class) {
        from(src)

        // https://github.com/gradle/gradle/releases?after=v5.2.1
        // https://docs.gradle.org/5.1-rc-1/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
        // https://docs.gradle.org/5.0/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
        // 5.1-rc-1开始变为如下，它的前一个版本是5.0
        archiveBaseName.set(baseName)
        archiveClassifier.set("sources")
    }

    // 生成JavDoc，docs/javadoc
    val genJavadocTask = task("genJavadoc", Javadoc::class) {
        source(src)
//      classpath += project.files(android?.bootClasspath)
        isFailOnError = false
    }

    // 生成${baseName}-${version}-javadoc.jar
    val genJavadocJarTask = task("genJavadocJar", Jar::class) {
        from(genJavadocTask.destinationDir)

        // https://github.com/gradle/gradle/releases?after=v5.2.1
        // https://docs.gradle.org/5.1-rc-1/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
        // https://docs.gradle.org/5.0/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
        // 5.1-rc-1开始变为如下，它的前一个版本是5.0
        archiveBaseName.set(baseName)
        archiveClassifier.set("javadoc")
    }.dependsOn(genJavadocTask)

    artifacts.apply {
        add("archives", genJavadocJarTask)
        add("archives", genSourcesJarTask)
    }
}

private fun Project.printLog(tag: String) {
    println("---------- $tag start -----------")

    convention.plugins.forEach { (key, value) ->
        println("conventionPlugins: key = $key, value = $value")
    }

    extensions.schema.forEach {
        println("extensions: ${it.key}, ${it.value}")
    }

    plugins.forEach {
        println("plugin: $it")
    }

    println("---------- $tag end -----------")
}

private fun isEmpty(str: String?) = str == null || str == ""

fun Project.`bintrayUploadExtension`(configure: BintrayUploadExtension.() -> Unit) =
        extensions.configure("bintrayUploadExtension", configure)
