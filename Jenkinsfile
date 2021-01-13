pipeline {
    agent {
        label 'cimapp'
    }
    tools {
        maven 'maven'
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
                withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                    sh 'mvn -B -DskipTests clean install'
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn test -pl NetworkParameters'
                        }
                    }
                }
                stage("Test ShortCircuit") {
                    when {
                        expression {return params.ShortCircuit}
                    }
                    steps {
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn test -pl ShortCircuit'
                        }
                    }
                }
                stage("Test Simulation") {
                    when {
                        expression {return params.Simulation}
                    }
                    steps {
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn test -pl Simulation'
                        }
                    }
                }
                stage("Test TestUtil") {
                    when {
                        expression {return params.TestUtil}
                    }
                    steps {
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn test -pl TestUtil'
                        }
                    }
                }
                stage("Test Util") {
                    when {
                        expression {return params.Util}
                    }
                    steps {
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn test -pl Util'
                        }
                    }
                }
            }
            post {
                always {
                    junit '**/target/surefire-reports/*.xml'
                }
            }
        }
    }
    post {
        always {
            archiveArtifacts artifacts: '**/target/*.jar', fingerprint: true
            junit '**/target/surefire-reports/*.xml'
        }
    }
}