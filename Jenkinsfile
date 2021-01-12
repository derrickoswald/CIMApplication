pipeline {
    agent {
        label 'cimapp'
    }
    tools {
        maven 'maven'
    }
    stages {
        stage ('Build') {
            steps {
                sh 'mvn -B -DskipTests clean install'
            }
        }
        // sh 'mvn test -pl GridLAB-D, Ingest, MaximumFeedIn,MSCONSReader,Net,NetworkParameters,ShortCircuit,Simulation,TestUtil,Util'
        stage('Test') {
            parallel{
                stage("Test GridLAB-D") {
                    steps {
                        sh 'mvn test -pl GridLAB-D'
                    }
                }
                stage("Test Ingest") {
                    steps {
                        sh 'mvn test -pl Ingest'
                    }
                }
                stage("Test MaximumFeedIn") {
                    steps {
                        sh 'mvn test -pl MaximumFeedIn'
                    }
                }
                stage("Test MSCONSReader") {
                    steps {
                        sh 'mvn test -pl MSCONSReader'
                    }
                }
                stage("Test Net") {
                    steps {
                        sh 'mvn test -pl Net'
                    }
                }
                stage("Test NetworkParameters") {
                    steps {
                        sh 'mvn test -pl NetworkParameters'
                    }
                }
                stage("Test ShortCircuit") {
                    steps {
                        sh 'mvn test -pl ShortCircuit'
                    }
                }
                stage("Test Simulation") {
                    steps {
                        sh 'mvn test -pl Simulation'
                    }
                }
                stage("Test TestUtil") {
                    steps {
                        sh 'mvn test -pl TestUtil'
                    }
                }
                stage("Test Util") {
                    steps {
                        sh 'mvn test -pl Util'
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