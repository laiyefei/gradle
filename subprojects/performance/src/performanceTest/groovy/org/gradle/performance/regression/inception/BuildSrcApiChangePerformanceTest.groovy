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
package org.gradle.performance.regression.inception

import org.gradle.performance.AbstractCrossVersionPerformanceTest
import org.gradle.performance.categories.SlowPerformanceRegressionTest
import org.gradle.profiler.BuildContext
import org.gradle.profiler.BuildMutator
import org.gradle.util.GradleVersion
import org.junit.experimental.categories.Category
import spock.lang.Unroll

import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.createMirrorInitScript
import static org.gradle.integtests.fixtures.RepoScriptBlockUtil.gradlePluginRepositoryMirrorUrl
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT
import static org.gradle.performance.generator.JavaTestProject.LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL
import static org.gradle.performance.generator.JavaTestProject.MEDIUM_MONOLITHIC_JAVA_PROJECT
import static org.gradle.test.fixtures.server.http.MavenHttpPluginRepository.PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY

@Category(SlowPerformanceRegressionTest)
class BuildSrcApiChangePerformanceTest extends AbstractCrossVersionPerformanceTest {

    static List<String> extraGradleBuildArguments() {
        ["-D${PLUGIN_PORTAL_OVERRIDE_URL_PROPERTY}=${gradlePluginRepositoryMirrorUrl()}",
         "-Dorg.gradle.ignoreBuildJavaVersionCheck=true",
         "-PbuildSrcCheck=false",
         "-I", createMirrorInitScript().absolutePath]
    }

    def setup() {
        def targetVersion = "6.7-20200812220226+0000"
        runner.targetVersions = [targetVersion]
        runner.minimumBaseVersion = GradleVersion.version(targetVersion).baseVersion.version
    }

    @Unroll
    def "buildSrc api change in #testProject comparing gradle"() {
        given:
        runner.testProject = testProject
        runner.tasksToRun = ['help']
        runner.runs = runs
        runner.args = extraGradleBuildArguments()
        def buildSrcMutator = new BuildSrcMutator()

        and:
        runner.addBuildMutator(buildSrcMutator)

        when:
        def result = runner.run()

        then:
        result.assertCurrentVersionHasNotRegressed()

        where:
        testProject                         | runs
        MEDIUM_MONOLITHIC_JAVA_PROJECT      | 40
        LARGE_JAVA_MULTI_PROJECT            | 20
        LARGE_JAVA_MULTI_PROJECT_KOTLIN_DSL | 10
    }
}
