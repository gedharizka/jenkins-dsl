import groovy.json.JsonSlurper

def inputFile = readFileFromWorkspace("data.json")
def InputJSON = new JsonSlurper().parseText(inputFile)
def project_env = "JAVA"

for (i=0; i<InputJSON.project.size(); i++) {

    def project_name = InputJSON.project[i].repository_name
    def repository_name = InputJSON.project[i].repository_name
    def num_to_keep = InputJSON.num_to_keep
    def repo_owner_name = InputJSON.repo_owner_name

    pipelineJob("${project_env}/${project_name}") {    

        logRotator {    
            numToKeep(num_to_keep)    
        }        

        definition {
            cps{
                sandbox()
                script('''
                     pipeline {
                        agent any
                        stages {
                            stage('Checkout') {
                                steps {
                                    echo 'Checking out code...'
                                }
                            }
                            stage('Build') {
                                steps {
                                    echo 'Building the application...'
                                }
                            }
                            stage('Test') {
                                steps {
                                    echo 'Running tests...'
                                }
                            }
                            stage('Deploy') {
                                steps {
                                    echo 'Deploying the application...'
                                }
                            }
                        }
                        post {
                            always {
                                echo 'Pipeline complete, cleaning up workspace...'
                                cleanWs()
                            }
                        }
                    }
                ''')
            }
            
        }




    }
}