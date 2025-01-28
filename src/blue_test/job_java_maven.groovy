import groovy.json.JsonSlurper

def inputFile = readFileFromWorkspace("blue_test.json")
def InputJSON = new JsonSlurper().parseText(inputFile)
def project_env = "Blue-Test"
def git_url = "https://github.com/gedharizka/tweet-trend.git"

for (i=0; i<InputJSON.project.size(); i++) {

    def project_name = InputJSON.project[i].repository_name
    def repository_name = InputJSON.project[i].repository_name
    def num_to_keep = InputJSON.num_to_keep
    def repo_owner_name = InputJSON.repo_owner_name

    pipelineJob("${project_env}/${project_name}") {    

        parameters{
            // need plugin ---List Git Branches Parameter---
                // remoteURL(git_url)
           gitParameter {
                name('BRANCH_OR_TAG') 
                description('Select a branch or tag to build') 
                type('PT_BRANCH') 
                branch('main') 
                branchFilter('origin/*') 
                tagFilter('*') 
                useRepository('https://github.com/gedharizka/tweet-trend.git') 
                defaultValue('main') 
                selectedValue('TOP') 
                quickFilterEnabled(true) 
                listSize('10') 
                sortMode('ASCENDING_SMART') 
            }
        }

        logRotator {    
            numToKeep(num_to_keep)    
        }        

        definition {
            cps{
                sandbox()
                script('''
                     pipeline {
                        agent any
                        environment{
                            PATH = "/opt/apache-maven-3.9.6/bin:$PATH"
                        }
                        stages {
                            stage('Checkout') {
                                steps {
                                    echo 'Checking out code...'
                                    def branch = "${params.BRANCH_OR_TAG}"
                                    git([url: 'https://github.com/gedharizka/tweet-trend.git', branch: branch, credentialsId: 'github-credential'])
                                }
                            }
                            stage('Build') {
                                steps {
                                    echo " ===> Build started <==="
                                    sh 'mvn clean deploy -Dmaven.test.skip=true'
                                    echo " ===> Build end <==="
                                }
                            }
                            stage('Test') {
                                steps {
                                    echo " ===> unit test started <==="
                                    sh """ mvn surefire-report:report """
                                    echo " ===> unit test ended <==="
                                }
                            }
                            stage('SonarQube Scan') {
                                environment{
                                    sonarScan = tool 'sonar-scanncer';
                                }
                                steps {
                                    withSonarQubeEnv('sonar-scanner-server'){
                                        withCredentials([string(credentialsId: 'sonar_token', variable: 'SONAR_TOKEN')]){
                                            sh """
                                                mvn clean verify sonar:sonar \
                                                -Dsonar.projectKey=trend-app \
                                                -Dsonar.projectName=trend-app \
                                                -Dsonar.host.url=http://178.128.84.214:9002 \
                                                -Dsonar.login=${SONAR_TOKEN}
                                            """
                                        }
                                    }

                                }// end step
                            }

                            stage('Quality Gate') {
                                steps {
                                    timeout(time: 5, unit: 'MINUTES') { // Just in case something goes wrong, pipeline will be killed after a timeout
                                        def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
                                        if (qg.status != 'OK') {
                                            error "Pipeline aborted due to quality gate failure: ${qg.status}"
                                        }   
                                    }
                                }
                            }

                            stage('Docker Build') {
                                steps {
                                    echo " ===> docker build <==="
                                    sh """ docker build -t gedharizka/'''+repository_name+''':latest . """
                                    sh """ docker image ls"""

                                    echo " ===> end docker build <==="
                                }
                            }

                            stage('Image Scan by trivy') {
                                steps {       
                                    
                                    def trivyOutput = sh(script: """ trivy image gedharizka/'''+repository_name+''':latest  """, returnStdout: true).trim()

                                    println trivyOutput

                                    if (trivyOutput.contains("Total: 0")) {
                                        echo "No vulnerabilities found in the Docker image."
                                    } else {
                                        echo "Vulnerabilities found in the Docker image."

                                    }

                                }
                            }

                            stage('Docker Push ') {
                                steps {       
                                    withCredentials([usernamePassword(credentialsId: 'dockerHub', passwordVariable: 'dockerHubPassword', usernameVariable: 'dockerHubUser')]) {
                                        sh """ docker login -u ${dockerHubUser} -p ${dockerHubPassword} """
                                        sh """ docker push gedharizka/'''+repository_name+''':latest """

                                    }

                                }
                            }

                            

                            stage('Deploy to Kubernetes ') {
                                steps {       
                                    sh """ kubectl apply -f namespace.yaml """
                                    sh """ kubectl apply -f secret """
                                    sh """ kubectl apply -f deployment"""
                                    sh """ kubectl apply -f service"""

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