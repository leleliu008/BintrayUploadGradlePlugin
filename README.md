# BintrayUploadAndroidGradlePlugin
<p>
Android开发者经常发布自己的Android库到<a href="https://jcenter.bintray.com/" target=_blank>jCenter</a>，通常使用两个<a href="http://blog.fpliu.com/it/software/gradle" target="_blank">Gradle</a>插件：
</p>
<ul>
    <li>
        <a href="https://github.com/dcendents/android-maven-gradle-plugin" target="_blank">android-maven-gradle-plugin</a>
    </li>
    <li>
        <a href="https://github.com/bintray/gradle-bintray-plugin" target="_blank">gradle-bintray-plugin</a>
    </li>
</ul>
<p>
这两个插件还要配合上好几个jar包的生成，POM文件的配置等，非常繁琐，实际上，我们一般的开发者也就是关心几个小点，其他子要自动生成即可，所以，为了简化这两个插件的使用，我在他们两个的基础上进行了包装。大大简化了他们的使用。
</p>

# 如何使用
1、配置classpath：
```
buildscript {
    repositories {
        jcenter()
        google()
    }
    dependencies {
        //Android Gradle插件
        //https://developer.android.com/studio/build/gradle-plugin-3-0-0-migration.html
        classpath("com.android.tools.build:gradle:3.0.1")

        //用于构建aar和maven包
        //https://github.com/dcendents/android-maven-gradle-plugin
        classpath("com.github.dcendents:android-maven-gradle-plugin:2.0")

        //用于上传maven包到jCenter中
        //https://github.com/bintray/gradle-bintray-plugin
        classpath("com.jfrog.bintray.gradle:gradle-bintray-plugin:1.7.3")

        classpath("com.fpliu:BintrayUploadAndroidGradlePlugin:1.0.0")
    }
}
```
2、应用插件：
```
plugins {
    id("com.android.library")
    id("com.github.dcendents.android-maven")
    id("com.jfrog.bintray")
    id("com.fpliu.bintray.upload.android")
}
```
3、配置数据：
```
// 这里是groupId，必须填写,一般填你唯一的包名
group = "com.fpliu"

//这个是版本号，必须填写
version = "1.0.0"

bintrayUploadAndroidExtension {
    developerName = "leleliu008"
    developerEmail = "leleliu008@gamil.com"

    projectSiteUrl = "https://github.com/$developerName/${rootProject.name}"
    projectGitUrl = "https://github.com/$developerName/${rootProject.name}"

    bintrayUserName = "xx"
    bintrayOrganizationName = "xx"
    bintrayRepositoryName = "yy"
    bintrayApiKey = "xxxxxxxxxxxxxxxxxxxxxxxxxxxx"
}
```
## gradle任务

### ./gradlew install
用于生成必须上传到<a href="https://jcenter.bintray.com/" target="_blank">jCenter</a>必须的文件，
执行此命令后在<code>build</code>目录下生成的内容如下：
```
build
├── libs
│   ├── ${rootProjectName}-1.0.0-javadoc.jar
│   └── ${rootProjectName}-1.0.0-sources.jar
├── outputs
│   └── aar
│       └── ${rootProjectName}-release.aar
├── poms
│   └── pom-default.xml
└── ....
```
### ./gradlew :library:bintrayUpload
这个命令还可以简化成<code>./gradlew :library:bU</code>，这就是上传到<a href="https://jcenter.bintray.com/" target=_blank>jCenter</a>的命令。当然，前提是您已经有了他的账户和仓库。
