//"Jenkins Pipeline is a suite of plugins which supports implementing and integrating continuous delivery pipelines into Jenkins. Pipeline provides an extensible set of tools for modeling delivery pipelines "as code" via the Pipeline DSL."
//More information can be found on the Jenkins Documentation page https://jenkins.io/doc/
pipeline {
    agent { label 'linux-small' }
    parameters {
            booleanParam(name: 'RELEASE', defaultValue: false, description: 'Perform Release?')
            string(name: 'RELEASE_VERSION', defaultValue: null, description: 'The version to release. Empty value will release the current version')
            string(name: 'RELEASE_TAG', defaultValue: null, description: 'The release tag for this version. Empty value will result in replication-RELEASE_VERSION')
            string(name: 'NEXT_VERSION', defaultValue: null, description: 'The next development version. Empty value will increment the patch version')
    }
    options {
        buildDiscarder(logRotator(numToKeepStr:'25'))
        disableConcurrentBuilds()
        timestamps()
    }
    triggers {
        /*
          Restrict nightly builds to master branch, all others will be built on change only.
          Note: The BRANCH_NAME will only work with a multi-branch job using the github-branch-source
        */
        cron(BRANCH_NAME == "master" ? "H H(21-23) * * *" : "")
    }
    environment {
        LINUX_MVN_RANDOM = '-Djava.security.egd=file:/dev/./urandom'
        COVERAGE_EXCLUSIONS = '**/test/**/*,**/itests/**/*,**/*Test*,**/sdk/**/*,**/*.js,**/node_modules/**/*,**/jaxb/**/*,**/wsdl/**/*,**/nces/sws/**/*,**/*.adoc,**/*.txt,**/*.xml'
        DISABLE_DOWNLOAD_PROGRESS_OPTS = '-Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn '
    }
    stages {
        stage('Calculating build parameters'){
            steps {
                script {
                    if(params.RELEASE == true) {
                        if(params.RELEASE_VERSION){
                            env.RELEASE_VERSION = params.RELEASE_VERSION
                         } else {
                            echo ("Setting release version to ${getBaseVersion()}")
                            env.RELEASE_VERSION = getBaseVersion()
                        }

                        if(params.RELEASE_TAG){
                            env.RELEASE_TAG = params.RELEASE_TAG
                        } else {
                            echo("Setting release tag")
                            env.RELEASE_TAG = "replication-${env.RELEASE_VERSION}"
                        }

                        if(params.NEXT_VERSION){
                            env.NEXT_VERSION = params.NEXT_VERSION
                        } else {
                            echo("Setting next version")
                            env.NEXT_VERSION = getDevelopmentVersion()
                        }
                        echo("Release parameters: release-version: ${env.RELEASE_VERSION} release-tag: ${env.RELEASE_TAG} next-version: ${env.NEXT_VERSION}")
                    }
                }
            }
        }
        // The incremental build will be triggered only for PRs. It will build the differences between the PR and the target branch
        stage('Incremental Build') {
            when {
                allOf {
                    expression { env.CHANGE_ID != null }
                    expression { env.CHANGE_TARGET != null }
                }
            }
            parallel {
                stage ('Linux') {
                    steps {
                        // TODO: Maven downgraded to work around a linux build issue. Falling back to system java to work around a linux build issue. re-investigate upgrading later
                        withMaven(maven: 'Maven 3.5.3', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings', mavenOpts: '${LINUX_MVN_RANDOM}') {
                            sh 'mvn install -B -DskipStatic=true -DskipTests=true $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                            sh 'mvn install -B -Dgib.enabled=true -Dgib.referenceBranch=/refs/remotes/origin/$CHANGE_TARGET $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                        }
                    }
                }
                //Commenting this out for now as the windows build is currently failing due to external environment issues and we may no longer need windows builds going forward
                //stage ('Windows') {
                //    agent { label 'server-2016-small' }
                //    steps {
                //        withMaven(maven: 'Maven 3.5.3', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings') {
                //             bat 'mvn install -B -DskipStatic=true -DskipTests=true  %DISABLE_DOWNLOAD_PROGRESS_OPTS%'
                //             bat 'mvn install -B -Dgib.enabled=true -Dgib.referenceBranch=/refs/remotes/origin/%CHANGE_TARGET% %DISABLE_DOWNLOAD_PROGRESS_OPTS%'
                //        }
                //    }
                //}
            }
        }
        stage('Full Build') {
            when { expression { env.CHANGE_ID == null } }
            parallel {
                stage ('Linux') {
                    steps {
                        // TODO: Maven downgraded to work around a linux build issue. Falling back to system java to work around a linux build issue. re-investigate upgrading later
                        withMaven(maven: 'Maven 3.5.3', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings', mavenOpts: '${LINUX_MVN_RANDOM}') {
                            script {
                                if(params.RELEASE == true) {
                                    sh "mvn -B -Dtag=${env.RELEASE_TAG} -DreleaseVersion=${env.RELEASE_VERSION} -DdevelopmentVersion=${env.NEXT_VERSION} release:prepare"
                                } else {
                                    sh 'mvn clean install -B $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                                }
                            }
                        }
                    }
                }
                //stage ('Windows') {
                //    agent { label 'server-2016-small' }
                //    steps {
                //        withMaven(maven: 'Maven 3.5.3', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings') {
                //            bat 'mvn clean install -B %DISABLE_DOWNLOAD_PROGRESS_OPTS%'
                //        }
                //    }
                //}
            }
        }
        stage('Security Analysis') {
            parallel {
                stage ('Owasp') {
                    steps {
                        withMaven(maven: 'Maven 3.5.3', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings', mavenOpts: '${LINUX_MVN_RANDOM}') {
                            // If this build is not a pull request, run full owasp scan. Otherwise run incremental scan
                            script {
                                if(params.RELEASE == true) {
                                    sh 'git co env.RELEASE_TAG'
                                }
                                if (env.CHANGE_ID == null) {
                                    sh 'mvn install -B -Powasp -DskipTests=true -nsu $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                                } else {
                                    sh 'mvn install -B -Powasp -DskipTests=true  -Dgib.enabled=true -Dgib.referenceBranch=/refs/remotes/origin/$CHANGE_TARGET -nsu $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                                }
                            }
                        }
                    }
                }
            }
        }
        stage('Quality Analysis') {
            parallel {
                // Sonar stage only runs against master
                stage ('SonarCloud') {
                    when {
                        allOf {
                            expression { env.CHANGE_ID == null }
                            branch 'master'
                            environment name: 'JENKINS_ENV', value: 'prod'
                        }
                    }
                    steps {
                        script {
                            if(params.RELEASE == true) {
                                sh 'git co env.RELEASE_TAG'
                            }
                        }
                        withMaven(maven: 'M35', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings', mavenOpts: '${LINUX_MVN_RANDOM}') {
                            withCredentials([string(credentialsId: 'SonarQubeGithubToken', variable: 'SONARQUBE_GITHUB_TOKEN'), string(credentialsId: 'cxbot-sonarcloud', variable: 'SONAR_TOKEN')]) {
                                script {
                                    sh 'mvn -q -B -Dcheckstyle.skip=true org.jacoco:jacoco-maven-plugin:prepare-agent install sonar:sonar -Dsonar.host.url=https://sonarcloud.io -Dsonar.login=$SONAR_TOKEN  -Dsonar.organization=cx -Dsonar.projectKey=replication -Dsonar.exclusions=${COVERAGE_EXCLUSIONS} $DISABLE_DOWNLOAD_PROGRESS_OPTS'

                                 }
                            }
                        }
                    }
                }
            }
        }
        stage('Release Tag'){
            when { expression { params.RELEASE == true } }
            steps {
                echo("Pushing release tags and commits")
                //sh "git push origin && git push origin ${env.RELEASE_TAG}"
                git push origin ${env.RELEASE_TAG}
            }
        }
        /*
         Deploy stage will only be executed for deployable branches. These include master and any patch branch matching M.m.x format (i.e. 2.10.x, 2.9.x, etc...).
         It will also only deploy in the presence of an environment variable JENKINS_ENV = 'prod'. This can be passed in globally from the jenkins master node settings.
        */
        stage('Deploy') {
            when {
                allOf {
                    expression { env.CHANGE_ID == null }
                    expression { env.BRANCH_NAME ==~ /((?:\d*\.)?\d.x|master)/ }
                    environment name: 'JENKINS_ENV', value: 'prod'
                }
            }
            steps{
                script {
                    if(params.RELEASE == true) {
                        //sh 'git co params.TAG'
                        echo( "Would checkout tag for deploy")
                    }
                }
                //withMaven(maven: 'Maven 3.5.3', jdk: 'jdk8-latest', globalMavenSettingsConfig: 'default-global-settings', mavenSettingsConfig: 'codice-maven-settings', mavenOpts: '${LINUX_MVN_RANDOM}') {
                //    sh 'mvn javadoc:aggregate -B -DskipStatic=true -DskipTests=true -nsu $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                //    sh 'mvn deploy -B -DskipStatic=true -DskipTests=true -DretryFailedDeploymentCount=10 -nsu $DISABLE_DOWNLOAD_PROGRESS_OPTS'
                //}
            }
        }
    }
}

def getCurrentVersion() {
    def pom = readMavenPom file: 'pom.xml'
    return pom.getVersion()
}

def getBaseVersion() {
    return getCurrentVersion().split('-')[0]
}

def getDevelopmentVersion() {
    def baseVersion = getBaseVersion()
    def patch = baseVersion.substring(baseVersion.lastIndexOf('.')+1).toInteger()+1
    def majMin = baseVersion.substring(0, baseVersion.lastIndexOf('.'))
    def developmentVersion = "${majMin}.${patch}-SNAPSHOT"
    return developmentVersion
}
