/*
 * Copyright (C) 2020 Aestas/IT
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.aestasit.gradle.plugins

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.compile.GroovyCompile
import org.gradle.api.tasks.compile.JavaCompile
import org.kordamp.gradle.plugin.base.ProjectConfigurationExtension
import org.kordamp.gradle.plugin.bintray.BintrayPlugin
import org.kordamp.gradle.plugin.project.groovy.GroovyProjectPlugin

import static org.kordamp.gradle.util.StringUtils.isBlank

/**
 * @author Andres Almiray
 */
class SshoogrParentPomPlugin implements Plugin<Project> {
    private String resolveProperty(Project project, String envKey, String propKey, String defaultValue) {
        String value = System.getenv(envKey)
        if (isBlank(value)) value = project.findProperty(propKey)
        isBlank(value)? defaultValue : value
    }

    void apply(Project project) {
        project.plugins.apply(GroovyProjectPlugin)
        project.plugins.apply(BintrayPlugin)

        project.ext.bintrayUsername = resolveProperty(project, 'SSHOOGR_BINTRAY_USERNAME', 'bintrayUsername', '**undefined**')
        project.ext.bintrayApiKey = resolveProperty(project, 'SSHOOGR_BINTRAY_APIKEY', 'bintrayApiKey', '**undefined**')
        project.ext.sonatypeUsername = resolveProperty(project, 'SSHOOGR_SONATYPE_USERNAME', 'sonatypeUsername', '**undefined**')
        project.ext.sonatypePassword = resolveProperty(project, 'SSHOOGR_SONATYPE_PASSWORD', 'sonatypePassword', '**undefined**')
        project.ext.githubUsername = resolveProperty(project, 'SSHOOGR_GITHUB_USERNAME', 'githubUsername', '**undefined**')
        project.ext.githubPassword = resolveProperty(project, 'SSHOOGR_GITHUB_PASSWORD', 'githubPassword', '**undefined**')

        if (isBlank(System.getProperty('org.ajoberstar.grgit.auth.username'))) {
            System.setProperty('org.ajoberstar.grgit.auth.username', project.githubUsername)
        }
        if (isBlank(System.getProperty('org.ajoberstar.grgit.auth.password'))) {
            System.setProperty('org.ajoberstar.grgit.auth.password', project.githubPassword)
        }

        project.extensions.findByType(ProjectConfigurationExtension).with {
            release = resolveProperty(project, 'SSHOOGR_RELEASE', 'release', 'false').toBoolean()

            info {
                vendor = 'AestasIT'

                links {
                    website      = "https://github.com/sshoogr/${project.rootProject.name}"
                    issueTracker = "https://github.com/sshoogr/${project.rootProject.name}/issues"
                    scm          = "https://github.com/sshoogr/${project.rootProject.name}.git"
                }

                scm {
                    url                 = "https://github.com/sshoogr/${project.rootProject.name}"
                    connection          = "scm:git:https://github.com/sshoogr/${project.rootProject.name}.git"
                    developerConnection = "scm:git:git@github.com:sshoogr/${project.rootProject.name}.git"
                }

                people {
                    person {
                        id    = 'aadamovich'
                        name  = 'Andrey Adamovich'
                        roles = ['developer', 'author']
                    }
                }

                credentials {
                    sonatype {
                        username = project.sonatypeUsername
                        password = project.sonatypePassword
                    }
                    github {
                        username = project.githubUsername
                        password = project.githubPassword
                    }
                }

                repositories {
                    repository {
                        name = 'localRelease'
                        url  = "${project.rootProject.buildDir}/repos/local/release"
                    }
                    repository {
                        name = 'localSnapshot'
                        url  = "${project.rootProject.buildDir}/repos/local/snapshot"
                    }
                }
            }

            licensing {
                licenses {
                    license {
                        id = 'Apache-2.0'
                    }
                }
            }

            docs {
                groovydoc {
                    replaceJavadoc = true
                    excludes = ['**/*.html', 'META-INF/**']
                }
                sourceXref {
                    inputEncoding = 'UTF-8'
                }
            }

            bintray {
                enabled = true
                credentials {
                    username = project.bintrayUsername
                    password = project.bintrayApiKey
                }
                userOrg = 'sshoogr'
                repo    = 'sshoogr'
                name    = project.rootProject.name
                publish = (project.rootProject.findProperty('release') ?: false).toBoolean()
            }

            publishing {
                releasesRepository  = 'localRelease'
                snapshotsRepository = 'localSnapshot'
            }
        }

        project.allprojects {
            repositories {
                jcenter()
                mavenCentral()
            }

            normalization {
                runtimeClasspath {
                    ignore('/META-INF/MANIFEST.MF')
                }
            }

            dependencyUpdates.resolutionStrategy = {
                componentSelection { rules ->
                    rules.all { selection ->
                        boolean rejected = ['alpha', 'beta', 'rc', 'cr'].any { qualifier ->
                            selection.candidate.version ==~ /(?i).*[.-]${qualifier}[.\d-]*.*/
                        }
                        if (rejected) {
                            selection.reject('Release candidate')
                        }
                    }
                }
            }

            configurations {
                all*.exclude group: 'commons-logging'
                all*.exclude group: 'log4j'
                all*.exclude module: 'slf4j-simple'
            }

            configurations.all {
                resolutionStrategy.failOnVersionConflict()
            }
        }

        project.allprojects { Project p ->
            def scompat = project.findProperty('sourceCompatibility')
            def tcompat = project.findProperty('targetCompatibility')

            p.tasks.withType(JavaCompile) { JavaCompile c ->
                if (scompat) c.sourceCompatibility = scompat
                if (tcompat) c.targetCompatibility = tcompat
            }
            p.tasks.withType(GroovyCompile) { GroovyCompile c ->
                if (scompat) c.sourceCompatibility = scompat
                if (tcompat) c.targetCompatibility = tcompat
            }
        }
    }
}
