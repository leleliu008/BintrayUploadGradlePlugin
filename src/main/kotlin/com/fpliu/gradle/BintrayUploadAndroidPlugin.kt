package com.fpliu.gradle

import com.android.build.gradle.LibraryExtension
import com.jfrog.bintray.gradle.BintrayExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.plugins.BasePluginConvention
import org.gradle.api.plugins.MavenRepositoryHandlerConvention
import org.gradle.api.tasks.Upload
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.kotlin.dsl.NamedDomainObjectContainerScope
import org.gradle.kotlin.dsl.task
import org.gradle.kotlin.dsl.withGroovyBuilder

class BintrayUploadAndroidPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val rootProjectName = project.rootProject.name

        (project.convention.plugins["base"] as BasePluginConvention).apply {
            archivesBaseName = rootProjectName
        }

        val extension = project.extensions.create<BintrayUploadAndroidExtension>("bintrayUploadAndroidExtension", BintrayUploadAndroidExtension::class.java)

        project.afterEvaluate {
            project.logger.info("extension = $extension")

            project.convention.plugins.forEach { key, value ->
                project.logger.info("conventionPlugins: key = $key, value = $value")
            }

            project.extensions.schema.forEach {
                project.logger.info("extensions: ${it.key}, ${it.value}")
            }

            project.plugins.forEach {
                project.logger.info("plugin: $it")
            }

            if ("" == extension.projectSiteUrl) {
                extension.projectSiteUrl = "https://github.com/${extension.developerName}/$rootProjectName"
            }

            if ("" == extension.projectGitUrl) {
                extension.projectGitUrl = "https://github.com/${extension.developerName}/$rootProjectName"
            }

            NamedDomainObjectContainerScope(project.tasks).apply {
                "install"(Upload::class) {
                    configuration = project.configurations.getByName("archives")
                    DslObject(repositories).convention.getPlugin(MavenRepositoryHandlerConvention::class.java).apply {
                        mavenInstaller {
                            it.pom.project {
                                it.withGroovyBuilder {
                                    "packaging"("aar")
                                    "artifactId"(rootProjectName)
                                    "name"(rootProjectName)
                                    "url"(extension.projectSiteUrl)
                                    "licenses" {
                                        "license" {
                                            "name"(extension.licenseName)
                                            "url"(extension.licenseUrl)
                                        }
                                    }
                                    "developers" {
                                        "developer" {
                                            "id"(extension.developerName)
                                            "name"(extension.developerName)
                                            "email"(extension.developerEmail)
                                        }
                                    }
                                    "scm" {
                                        "connection"(extension.projectGitUrl)
                                        "developerConnection"(extension.projectGitUrl)
                                        "url"(extension.projectSiteUrl)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val bintray = project.extensions.getByName("bintray") as BintrayExtension
            val android = project.extensions.getByName("android") as LibraryExtension

            // 生成jar包的task
            val sourcesJarTask = project.task("sourcesJar", Jar::class) {
                from(android.sourceSets.getByName("main").java.srcDirs)
                baseName = rootProjectName
                classifier = "sources"
            }

            // 生成jarDoc的task
            val javadocTask = project.task("javadoc", Javadoc::class) {
                source(android.sourceSets.getByName("main").java.srcDirs)
                classpath += project.files(android.bootClasspath)
                isFailOnError = false
            }

            // 生成javaDoc的jar
            val javadocJarTask = project.task("javadocJar", Jar::class) {
                from(javadocTask.destinationDir)
                baseName = rootProjectName
                classifier = "javadoc"
            }.dependsOn(javadocTask)

            project.artifacts.apply {
                add("archives", javadocJarTask)
                add("archives", sourcesJarTask)
            }

            bintray.apply {
                user = extension.bintrayUserName
                key = extension.bintrayApiKey
                setConfigurations("archives")
                pkg = PackageConfig().apply {
                    userOrg = extension.bintrayOrganizationName
                    repo = extension.bintrayRepositoryName
                    name = rootProjectName
                    websiteUrl = extension.projectSiteUrl
                    vcsUrl = extension.projectGitUrl
                    setLicenses("Apache-2.0")
                    publish = true
                }
            }
        }
    }
}

fun Project.`bintrayUploadAndroidExtension`(configure: BintrayUploadAndroidExtension.() -> Unit) =
        extensions.configure("bintrayUploadAndroidExtension", configure)