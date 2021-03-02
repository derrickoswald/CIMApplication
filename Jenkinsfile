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
    parameters {
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
            post {
                always {
                    archiveArtifacts artifacts: artifactsList, fingerprint: true
                }
            }
        }
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
                            sh 'mvn verify -pl GridLAB-D'
                        }
                    }
                    post {
                        always {
                            junit 'GridLAB-D/target/surefire-reports/*.xml'
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
                            sh 'mvn verify -pl Ingest'
                        }
                    }
                    post {
                        always {
                            junit 'Ingest/target/surefire-reports/*.xml'
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
                            sh 'mvn verify -pl MaximumFeedIn'
                        }
                    }
                    post {
                        always {
                            junit 'MaximumFeedIn/target/surefire-reports/*.xml'
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
                            sh 'mvn verify -pl MSCONSReader'
                        }
                    }
                    post {
                        always {
                            junit 'MSCONSReader/target/surefire-reports/*.xml'
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
                            sh 'mvn verify -pl Net'
                        }
                    }
                    post {
                        always {
                            junit 'Net/target/surefire-reports/*.xml'
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
                            sh 'mvn verify -pl NetworkParameters'
                        }
                    }
                    post {
                        always {
                            junit 'NetworkParameters/target/surefire-reports/*.xml'
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn verify -pl ShortCircuit'
                        }
                    }
                    post {
                        always {
                            junit 'ShortCircuit/target/surefire-reports/*.xml'
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn verify -pl Simulation'
                        }
                    }
                    post {
                        always {
                            junit 'Simulation/target/surefire-reports/*.xml'
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn verify -pl TestUtil'
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
                        withMaven(maven: 'maven', mavenLocalRepo: '../../maven_repos/'+BRANCH_NAME) {
                            sh 'mvn verify -pl Util'
                        }
                    }
                    post {
                        always {
                            junit 'Util/target/surefire-reports/*.xml'
                        }
                    }
                }
            }
        }
    }
}