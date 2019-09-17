import com.jfrog.bintray.gradle.BintrayUploadTask
import java.util.Properties

plugins {
    id("java-gradle-plugin")
    
    maven

    //Kotlin Compiler
    //https://plugins.gradle.org/plugin/org.jetbrains.kotlin.jvm
    kotlin("jvm").version("1.3.50")

    //上传到Gradle Plugin Portal的插件
    //https://plugins.gradle.org/plugin/com.gradle.plugin-publish
    id("com.gradle.plugin-publish").version("0.10.1")
    
    //上传到Bintray的插件
    //https://plugins.gradle.org/plugin/com.jfrog.bintray
    id("com.jfrog.bintray").version("1.7.3")
}

sourceSets {
    getByName("main") {
        java.srcDirs("src/main/kotlin")
    }
}

gradlePlugin {
    plugins {
        create("bintrayUploadPlugin") {
            id = "com.fpliu.bintray"
            implementationClass = "com.fpliu.gradle.BintrayUploadPlugin"
        }
    }
}

repositories {
    jcenter { url = uri("https://maven.aliyun.com/repository/jcenter") }
    google()
}

dependencies {
    implementation(kotlin("stdlib"))

    compileOnly(gradleApi())

    compileOnly(gradleKotlinDsl())

    //Kotlin编译的插件
    //http://kotlinlang.org/docs/reference/using-gradle.html
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:1.2.21")

    //Android Gradle插件
    //https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html
    compileOnly("com.android.tools.build:gradle:3.0.1")

    //用于构建aar和maven包
    //https://github.com/dcendents/android-maven-gradle-plugin
    compileOnly("com.github.dcendents:android-maven-gradle-plugin:2.0")

    //用于上传maven包到jCenter中
    //https://github.com/bintray/gradle-bintray-plugin
    compileOnly("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.7.3")
}

val rootProjectName: String = project.name

// 这里是groupId,必须填写,一般填你唯一的包名
group = "com.fpliu"

//这个是版本号，必须填写
version = "1.0.8"

// 项目的主页,这个是说明，可随便填
val siteUrl = "https://github.com/leleliu008/$rootProjectName"

// GitHub仓库的URL,这个是说明，可随便填
val gitUrl = "https://github.com/leleliu008/$rootProjectName"

pluginBundle {
    website = siteUrl
    vcsUrl = gitUrl
    description = "easily upload your library to bintray."

    (plugins) {
        "bintrayUploadPlugin" {
            displayName = "Bintray Upload Gradle  plugin"
            tags = listOf("bintray", "upload", "gradle", "plugin")
        }
    }
}

// 生成${baseName}-${version}-sources.jar
val genSourcesJarTask = task("genSourcesJar", Jar::class) {
    from(sourceSets["main"].java.srcDirs)

    //https://github.com/gradle/gradle/releases?after=v5.2.1
    //https://docs.gradle.org/5.1-rc-1/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
    //https://docs.gradle.org/5.0/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
    //5.1-rc-1开始变为如下，它的前一个版本是5.0
    archiveBaseName.set(rootProjectName)
    archiveClassifier.set("sources")
}

// 生成JavDoc，docs/javadoc
val genJavadocTask = task("genJavadoc", Javadoc::class) {
    source(sourceSets["main"].java.srcDirs)
    isFailOnError = false
}

// 生成${baseName}-${version}-javadoc.jar
val genJavadocJarTask = task("genJavadocJar", Jar::class) {
    from(genJavadocTask.destinationDir)

    //https://github.com/gradle/gradle/releases?after=v5.2.1
    //https://docs.gradle.org/5.1-rc-1/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
    //https://docs.gradle.org/5.0/dsl/org.gradle.api.tasks.bundling.Jar.html#org.gradle.api.tasks.bundling.Jar
    //5.1-rc-1开始变为如下，它的前一个版本是5.0
    archiveBaseName.set(rootProjectName)
    archiveClassifier.set("javadoc")
}.dependsOn(genJavadocTask)

artifacts {
    add("archives", genJavadocJarTask)
    add("archives", genSourcesJarTask)
}

bintray {
    setConfigurations("archives")
    pkg = PackageConfig().apply {
        userOrg = "fpliu"
        repo = "newton"
        name = rootProjectName
        websiteUrl = siteUrl
        vcsUrl = gitUrl
        setLicenses("Apache-2.0")
        publish = true
    }
}

gradle.addListener(object : TaskExecutionAdapter() {
    override fun beforeExecute(task: Task) {
        super.beforeExecute(task)

        if (task is BintrayUploadTask) {
            attachBintrayUserAndKey(task)
        } else if (task is Upload) {
            task.configuration = project.configurations.getByName("archives")
            task.repositories {
                withConvention(MavenRepositoryHandlerConvention::class) {
                    mavenInstaller {
                        pom.project {
                            withGroovyBuilder {
                                "packaging"("jar")
                                "artifactId"(rootProjectName)
                                "name"(rootProjectName)
                                "url"(siteUrl)
                                "licenses" {
                                    "license" {
                                        "name"("The Apache Software License, Version 2.0")
                                        "url"("http://www.apache.org/licenses/LICENSE-2.0.txt")
                                    }
                                }
                                "developers" {
                                    "developer" {
                                        "id"("fpliu")
                                        "name"("fpliu")
                                        "email"("leleliu008@gmail.com")
                                    }
                                }
                                "scm" {
                                    "connection"(gitUrl)
                                    "developerConnection"(gitUrl)
                                    "url"(siteUrl)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
})

fun isEmpty(str: String?) = str == null || str == ""

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
