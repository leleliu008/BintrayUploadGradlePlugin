# BintrayUploadGradlePlugin

`Android`开发者经常发布自己的`Android`库到[bintray](https://bintray.com/)，通常需要使用两个[gradle](http://blog.fpliu.com/it/software/gradle)插件：

- [android-maven-gradle-plugin](https://github.com/dcendents/android-maven-gradle-plugin)
- [gradle-bintray-plugin](https://github.com/bintray/gradle-bintray-plugin)

这两个插件还要配合上好几个`task`，用于`jar`包的生成，`POM`文件的配置等，非常繁琐。

实际上，我们一般的开发者也就是关心几个小点，其他自动生成即可，所以，为了简化这两个插件的使用，我在他们两个的基础上进行了包装。大大简化了他们的使用。

|本插件的版本|需要Gradle的版本|
|-|-|
|1.0.0|<=5.0|
|1.0.7|>5.0|
<br>

## 1、在Android库工程中使用方法

1、在`settings.gradle.kts`中配置[Gradle Plugin Portal](https://plugins.gradle.org)的镜像（非必须，只是为了加快下载速度）：
```
pluginManagement {
    repositories {
        //https://maven.aliyun.com/mvn/view
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
```

2、在项目根目录中的`build.gradle.kts`中配置`Android Gradle Plugin`：
```
buildscript {
    repositories {
        google()
    }
    dependencies {
        //https://developer.android.google.cn/studio/releases/gradle-plugin.html
        classpath("com.android.tools.build:gradle:3.3.2")
    }
}
```
`Android Gradle Plugin 3.3.2`以上版本存在Bug，正在解决中。


3、在`库模块`的`build.gradle.kts`中应用插件：
```
//plugins块中加载的插件都托管在Gradle PLugin Portal中，或者已经在root build.gradle.kts中的classpath中配置好了
plugins {
    id("com.android.library")
    
    //https://github.com/leleliu008/BintrayUploadGradlePlugin
    //https://plugins.gradle.org/plugin/com.fpliu.bintray
    id("com.fpliu.bintray").version("1.0.7")

    //用于构建jar和pom
    //https://github.com/dcendents/android-maven-gradle-plugin
    id("com.github.dcendents.android-maven").version("2.0")
        
    //用于上传到jCenter中
    //https://github.com/bintray/gradle-bintray-plugin
    id("com.jfrog.bintray").version("1.7.3")
}

// 这里是groupId，必须填写,一般填你唯一的包名
group = "com.fpliu"

//这个是版本号，必须填写
version = "1.0.0"

val rootProjectName = rootProject.name

bintrayUploadExtension {
    developerName = "leleliu008"
    developerEmail = "leleliu008@gamil.com"

    projectSiteUrl = "https://github.com/$developerName/$rootProjectName"
    projectGitUrl = "https://github.com/$developerName/$rootProjectName"

    bintrayOrganizationName = "xx"
    bintrayRepositoryName = "yy"
}
```

4、在`$HOME/.bintray.properties`中设置`Bintray`的用户和`apiKey`：
```
bintray.apikey=your bintray apiKey
bintray.user=your bintray user
```
这里这两个配置没有与其他配置放在一起，而是单独放到用户`Home`目录下，目的是防止不小心提交到`GitHub`等公共平台上。

## 2、基于JVM的语言的工程使用方法

1、在`settings.gradle.kts`中配置[Gradle Plugin Portal](https://plugins.gradle.org)的镜像（非必须，只是为了加快下载速度）：
```
pluginManagement {
    repositories {
        //https://maven.aliyun.com/mvn/view
        maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
    }
}
```

2、在`库模块`中的`build.gradle.kts`中应用插件：
```
//plugins块中加载的插件都托管在Gradle PLugin Portal中
plugins {
    java
    maven
    
    //https://github.com/leleliu008/BintrayUploadGradlePlugin
    //https://plugins.gradle.org/plugin/com.fpliu.bintray
    id("com.fpliu.bintray").version("1.0.7")

    //用于上传到jCenter中
    //https://github.com/bintray/gradle-bintray-plugin
    id("com.jfrog.bintray").version("1.7.3")
}

// 这里是groupId，必须填写,一般填你唯一的包名
group = "com.fpliu"

//这个是版本号，必须填写
version = "1.0.0"

val rootProjectName = rootProject.name

bintrayUploadExtension {
    developerName = "leleliu008"
    developerEmail = "leleliu008@gamil.com"

    projectSiteUrl = "https://github.com/$developerName/$rootProjectName"
    projectGitUrl = "https://github.com/$developerName/$rootProjectName"

    bintrayOrganizationName = "xx"
    bintrayRepositoryName = "yy"
}
```

3、在`$HOME/.bintray.properties`中设置`Bintray`的用户和`apiKey`：
```
bintray.apikey=your bintray apiKey
bintray.user=your bintray user
```
这里这两个配置没有与其他配置放在一起，而是单独放到用户`Home`目录下，目的是防止不小心提交到`GitHub`等公共平台上。

## 3、gradle任务

### ./gradlew :library:install
执行此命令后在`build`目录下生成如下内容：
```
build
├── libs
│   ├── ${rootProjectName}-${version}-javadoc.jar
│   └── ${rootProjectName}-${version}-sources.jar
├── outputs
│   └── aar
│       └── ${rootProjectName}-${version}.aar
├── poms
│   └── pom-default.xml
└── ....
```
用此命令验证生成的内容是否符合您的需要。


### ./gradlew :library:bintrayUpload
这个命令还可以简化成`./gradlew :library:bU`，这就是上传到[bintray](https://bintray.com/)的命令。当然，前提是您已经有了他的账户和仓库。

### 注意
上面两个任务前面都加了`:library`，这是因为一般的工程都会包含至少两个子模块，一个一般是`app`或者是`sample`，另一个一般是`library`。从字面意思也可以知道，`library`模块就是编写我们要发布的库的，而`app`或者是`sample`是用来编写示例代码的。

您的工程如果是单模块的，那么省略`:library`即可。

## 4、使用示例
- [kotlin-ext-jdk](https://github.com/leleliu008/kotlin-ext-jdk)
- [kotlin-ext-android](https://github.com/leleliu008/kotlin-ext-android)
