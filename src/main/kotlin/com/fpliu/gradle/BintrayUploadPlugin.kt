package com.fpliu.gradle

import com.android.build.gradle.LibraryExtension
import com.android.build.gradle.tasks.BundleAar
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

class BintrayUploadPlugin : Plugin<Project>, TaskExecutionAdapter() {

    override fun apply(project: Project) {
        project.extensions.create("bintrayUploadExtension", BintrayUploadExtension::class.java)
        project.afterEvaluate(this::afterProjectEvaluate)
        project.gradle.addListener(this)
    }

    private fun afterProjectEvaluate(project: Project) {
        val bintrayUploadExtension = getBintrayUploadExtension(project)
        buildBintrayExtension(project, bintrayUploadExtension)

        val baseName = bintrayUploadExtension.archivesBaseName
        project.convention.getPlugin(BasePluginConvention::class.java).archivesBaseName = baseName

        //这里不能使用val android = project.extensions.findByType(LibraryExtension::class.java)，因为如果是基于JVM的工程的话，
        //根本就没有LibraryExtension::class.java，试图去加载它，会得到以下异常
        //java.lang.NoClassDefFoundError: com/android/build/gradle/LibraryExtension
        //下面的代码不会异常的原因是：project.extensions.findByName("android")不为null才会去加载LibraryExtension
        val android = project.extensions.findByName("android") as? LibraryExtension
        val java = project.convention.getPlugin(JavaPluginConvention::class.java)

        val src = if (android == null) {
            java.sourceSets.getByName("main").java.srcDirs
        } else {
            android.sourceSets.getByName("main").java.srcDirs
        }

        // 生成${baseName}-${version}-sources.jar
        val genSourcesJarTask = project.task("genSourcesJar", Jar::class) {
            from(src)

            // https://github.com/gradle/gradle/releases?after=v5.2.1
            // https://docs.gradle.org/5.1-rc-1/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
            // https://docs.gradle.org/5.0/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
            // 5.1-rc-1开始变为如下，它的前一个版本是5.0
            archiveBaseName.set(baseName)
            archiveClassifier.set("sources")
        }

        // 生成JavDoc，docs/javadoc
        val genJavadocTask = project.task("genJavadoc", Javadoc::class) {
            source(src)
//      classpath += project.files(android?.bootClasspath)
            isFailOnError = false
        }

        // 生成${baseName}-${version}-javadoc.jar
        val genJavadocJarTask = project.task("genJavadocJar", Jar::class) {
            from(genJavadocTask.destinationDir)

            // https://github.com/gradle/gradle/releases?after=v5.2.1
            // https://docs.gradle.org/5.1-rc-1/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
            // https://docs.gradle.org/5.0/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
            // 5.1-rc-1开始变为如下，它的前一个版本是5.0
            archiveBaseName.set(baseName)
            archiveClassifier.set("javadoc")
        }.dependsOn(genJavadocTask)

        project.artifacts.apply {
            add("archives", genJavadocJarTask)
            add("archives", genSourcesJarTask)
        }
    }

    override fun beforeExecute(task: Task) {
        super.beforeExecute(task)

        when (task) {
            is BintrayUploadTask -> attachBintrayUserAndKey(task)
            is Upload -> {
                val project = task.project
                task.configuration = project.configurations.getByName("archives")
                DslObject(task.repositories).convention.getPlugin(MavenRepositoryHandlerConvention::class.java).apply {
                    mavenInstaller {
                        it.pom.project {
                            it.withGroovyBuilder {
                                val bintrayUploadExtension = getBintrayUploadExtension(project)
                                //这里不能用project.plugins.hasPlugin(LibraryPlugin::class.java)判断
                                //因为要支持非Android工程
                                "packaging"(if (project.extensions.findByName("android") == null) "jar" else "aar")
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
            else -> {
                if (task.javaClass.canonicalName == "com.android.build.gradle.tasks.BundleAar_Decorated") {
                    task as BundleAar
                    task.archiveFileName.set("${task.archiveBaseName.get()}-${task.archiveVersion.get()}.aar")
                }
            }
        }
    }

    private fun getBintrayUploadExtension(project: Project) = project.extensions.getByType(BintrayUploadExtension::class.java).apply {
        if (isEmpty(archivesBaseName)) {
            archivesBaseName = project.rootProject.name
        }

        if (isEmpty(projectSiteUrl)) {
            projectSiteUrl = "https://github.com/$developerName/$archivesBaseName"
        }

        if (isEmpty(projectGitUrl)) {
            projectGitUrl = "https://github.com/$developerName/$archivesBaseName"
        }
    }

    private fun buildBintrayExtension(project: Project, bintrayUploadExtension: BintrayUploadExtension) = project.extensions.getByType(BintrayExtension::class.java).apply {
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

    private fun attachBintrayUserAndKey(bintrayUploadTask: BintrayUploadTask) {
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

    private fun isEmpty(str: String?) = str == null || str == ""
}