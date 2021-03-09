artifactsList = [
    "MaximumFeedIn/target/MaximumFeedIn-*-jar-with-dependencies.jar",
    "ShortCircuit/target/ShortCircuit-*-jar-with-dependencies.jar",
    "NetworkParameters/target/Customer?_NetworkParameters-*-jar-with-dependencies.jar",
    "Ingest/target/Ingest-*-jar-with-dependencies.jar",
    "Simulation/target/Simulation-*-jar-with-dependencies.jar",
].join(",")

pipeline {
    agent {
        label 'cimapp'
    }
    tools {
        maven 'maven'
    }
    options {
        buildDiscarder(logRotator(numToKeepStr: '5', artifactNumToKeepStr:'5')
    }
    parameters {
    // GridLAB-D, Ingest, MaximumFeedIn,MSCONSReader,Net,NetworkParameters,ShortCircuit,Simulation,TestUtil,Util
        booleanParam defaultValue: true, description: 'Build', name: 'BUILD'
        booleanParam defaultValue: true, description: 'GridLAB-D', name: 'GridLAB'
        booleanParam defaultValue: true, description: 'Ingest', name: 'Ingest'
        booleanParam defaultValue: true, description: 'MaximumFeedIn', name: 'MaximumFeedIn'
        booleanParam defaultValue: true, description: 'MSCONSReader', name: 'MSCONSReader'
        booleanParam defaultValue: true, description: 'Net', name: 'Net'
        booleanParam defaultValue: true, description: 'NetworkParameters', name: 'NetworkParameters'
        booleanParam defaultValue: true, description: 'ShortCircuit', name: 'ShortCircuit'
        booleanParam defaultValue: true, description: 'Simulation', name: 'Simulation'
        booleanParam defaultValue: true, description: 'TestUtil', name: 'TestUtil'
        booleanParam defaultValue: true, description: 'Util', name: 'Util'
    }
    stages {
        stage ('Build') {
            when {
                expression {
                    return params.BUILD
                }
            }
            steps {
                withMaven(
                    maven: 'maven',
                    mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                    options: [
                        junitPublisher(healthScaleFactor: 1.0),
                        artifactsPublisher(disabled: true),
                        findbugsPublisher()
                    ]
                ) {
                    sh 'mvn -B -DskipTests clean install'
                }
            }
            post {
                always {
                    archiveArtifacts artifacts: artifactsList, fingerprint: true
                }
            }
        }
        // sh 'mvn test -pl GridLAB-D, Ingest, MaximumFeedIn,MSCONSReader,Net,NetworkParameters,ShortCircuit,Simulation,TestUtil,Util'
        stage('Test') {
            parallel{
                stage("Test GridLAB-D") {
                    when {
                        expression {
                            return params.GridLAB
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl GridLAB-D'
                        }
                    }
                }
                stage("Test Ingest") {
                    when {
                        expression {
                            return params.Ingest
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl Ingest'
                        }
                    }
                }
                stage("Test MaximumFeedIn") {
                    when {
                        expression {
                            return params.MaximumFeedIn
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl MaximumFeedIn'
                        }
                    }
                }
                stage("Test MSCONSReader") {
                    when {
                        expression {
                            return params.MSCONSReader
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl MSCONSReader'
                        }
                    }
                }
                stage("Test Net") {
                    when {
                        expression {
                            return params.Net
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl Net'
                        }
                    }
                }
                stage("Test NetworkParameters") {
                    when {
                        expression {
                            return params.NetworkParameters
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl NetworkParameters'
                        }
                    }
                }
                stage("Test ShortCircuit") {
                    when {
                        expression {
                            return params.ShortCircuit
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl ShortCircuit'
                        }
                    }
                }
                stage("Test Simulation") {
                    when {
                        expression {
                            return params.Simulation
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl Simulation'
                        }
                    }
                }
                stage("Test TestUtil") {
                    when {
                        expression {
                            return params.TestUtil
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl TestUtil'
                        }
                    }
                }
                stage("Test Util") {
                    when {
                        expression {
                            return params.Util
                        }
                    }
                    steps {
                        withMaven(
                            maven: 'maven',
                            mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME,
                            options: [
                                junitPublisher(healthScaleFactor: 1.0)
                            ]
                        ) {
                            sh 'mvn test -pl Util'
                        }
                    }
                }
            }
        }
    }
}