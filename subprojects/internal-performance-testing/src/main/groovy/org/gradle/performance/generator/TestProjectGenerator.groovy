/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.performance.generator

import org.gradle.test.fixtures.dsl.GradleDsl

class TestProjectGenerator {

    TestProjectGeneratorConfiguration config
    FileContentGenerator fileContentGenerator
    BazelFileContentGenerator bazelContentGenerator

    TestProjectGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config
        this.fileContentGenerator = FileContentGenerator.forConfig(config)
        this.bazelContentGenerator = new BazelFileContentGenerator(config)
    }

    def generate(File outputBaseDir) {
        def dependencyTree = new DependencyTree()

        populateDependencyTree(dependencyTree)

        generateProjects(outputBaseDir, dependencyTree)
    }

    def populateDependencyTree(DependencyTree dependencyTree) {
        if (config.subProjects == 0) {
            dependencyTree.calculateClassDependencies(0, config.sourceFiles - 1)
        } else {
            for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
                def sourceFileRangeStart = subProjectNumber * config.sourceFiles
                def sourceFileRangeEnd = sourceFileRangeStart + config.sourceFiles - 1
                dependencyTree.calculateClassDependencies(sourceFileRangeStart, sourceFileRangeEnd)
            }
        }
        dependencyTree.calculateProjectDependencies()
    }

    def generateProjects(File outputBaseDir, DependencyTree dependencyTree) {
        def rootProjectDir = new File(outputBaseDir, config.projectName)
        rootProjectDir.mkdirs()
        generateProject(rootProjectDir, dependencyTree, null)
        if (config.pluginsBlocks) {
            generatePluginsInBuildSrc(rootProjectDir)
        }
        for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
            def subProjectDir = new File(rootProjectDir, "project$subProjectNumber")
            generateProject(subProjectDir, dependencyTree, subProjectNumber)
        }
    }

    def generateProject(File projectDir, DependencyTree dependencyTree, Integer subProjectNumber) {
        def isRoot = subProjectNumber == null

        file projectDir, config.dsl.fileNameFor('build'), fileContentGenerator.generateBuildGradle(config.language, subProjectNumber, dependencyTree)
        file projectDir, config.dsl.fileNameFor('settings'), fileContentGenerator.generateSettingsGradle(isRoot)
        file projectDir, "gradle.properties", fileContentGenerator.generateGradleProperties(isRoot)
        file projectDir, "pom.xml", fileContentGenerator.generatePomXML(subProjectNumber, dependencyTree)
        file projectDir, "BUILD.bazel", bazelContentGenerator.generateBuildFile(subProjectNumber, dependencyTree)
        if (isRoot) {
            file projectDir, "WORKSPACE", bazelContentGenerator.generateWorkspace()
            file projectDir, "junit.bzl", bazelContentGenerator.generateJunitHelper()
        }
        file projectDir, "performance.scenarios", fileContentGenerator.generatePerformanceScenarios(isRoot)

        if (!isRoot || config.subProjects == 0) {
            def sourceFileRangeStart = isRoot ? 0 : subProjectNumber * config.sourceFiles
            def sourceFileRangeEnd = sourceFileRangeStart + config.sourceFiles - 1
            println "Generating Project: $projectDir"
            (sourceFileRangeStart..sourceFileRangeEnd).each {
                def packageName = fileContentGenerator.packageName(it, subProjectNumber, '/')
                file projectDir, "src/main/${config.language.name}/${packageName}/Production${it}.${config.language.name}", fileContentGenerator.generateProductionClassFile(subProjectNumber, it, dependencyTree)
                file projectDir, "src/test/${config.language.name}/${packageName}/Test${it}.${config.language.name}", fileContentGenerator.generateTestClassFile(subProjectNumber, it, dependencyTree)
            }
        }

        if (isRoot && config.buildSrc) {
            addDummyBuildSrcProject(projectDir)
        }
    }

    /**
     * This is just to ensure we test the overhead of having a buildSrc project, e.g. snapshotting the Gradle API.
     */
    private addDummyBuildSrcProject(File projectDir) {
        file projectDir, "buildSrc/src/main/${config.language.name}/Thing.${config.language.name}", "public class Thing {}"
        if (config.pluginsBlocks) {
            if (config.dsl == GradleDsl.KOTLIN) {
                file projectDir, "buildSrc/build.gradle.kts", """
                plugins {
                    `kotlin-dsl`
                }
                repositories {
                    gradlePluginPortal()
                }
                """.stripIndent()
            } else {
                file projectDir, "buildSrc/build.gradle", """
                plugins {
                    id("groovy-gradle-plugin")
                }
                """.stripIndent()
            }
        } else {
            file projectDir, "buildSrc/build.gradle", "compileJava.options.incremental = true"
        }
    }

    private generatePluginsInBuildSrc(File rootProjectDir) {
        for (int subProjectNumber = 0; subProjectNumber < config.subProjects; subProjectNumber++) {
            generatePluginInBuildSrc(rootProjectDir, subProjectNumber)
        }
    }

    private generatePluginInBuildSrc(File rootProjectDir, int subProjectNumber) {
        String dslDirectory = config.dsl == GradleDsl.KOTLIN ? 'kotlin' : 'groovy'
        String pluginSuffix = config.dsl == GradleDsl.KOTLIN ? '.gradle.kts' : '.gradle'
        file rootProjectDir, "buildSrc/src/main/${dslDirectory}/myproject.plugin${subProjectNumber}${pluginSuffix}", "println(\"plugin${subProjectNumber}\")"
    }

    void file(File dir, String name, String content) {
        if (content == null) {
            return
        }
        def file = new File(dir, name)
        file.parentFile.mkdirs()
        file.text = content.stripIndent().trim()
    }

    static void main(String[] args) {
        def projectName = args[0]
        def outputDir = new File(args[1])

        JavaTestProject project = JavaTestProject.values().find { it.projectName == projectName }
        if (project == null) {
            throw new IllegalArgumentException("Project not defined: $projectName")
        }
        def projectDir = new File(outputDir, projectName)
        new TestProjectGenerator(project.config).generate(outputDir)

        println "Generated: $projectDir"
    }
}
