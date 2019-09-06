package com.fpliu.gradle

open class BintrayUploadExtension {

    var developerName: String = ""

    var developerEmail: String = ""

    var licenseName: String = "The Apache Software License, Version 2.0"

    var licenseUrl: String = "http://www.apache.org/licenses/LICENSE-2.0.txt"

    // 项目的主页
    var projectSiteUrl: String = ""

    // GitHub仓库的URL
    var projectGitUrl: String = ""

    var bintrayOrganizationName: String = ""

    var bintrayRepositoryName: String = ""
}