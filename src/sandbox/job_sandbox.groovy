import groovy.json.JsonSlurper

def inputFile = readFileFromWorkspace("sandbox.json")
def InputJSON = new JsonSlurper().parseText(inputFile)
def project_env = "Sandbox"
def git_url = "https://github.com/gedharizka/tweet-trend.git"

for (i=0; i<InputJSON.project.size(); i++) {

    def project_name = InputJSON.project[i].repository_name
    def repository_name = InputJSON.project[i].repository_name
    def num_to_keep = InputJSON.project[i].num_to_keep
    def repo_owner_name = InputJSON.repo_owner_name

    pipelineJob("${project_env}/${project_name}") {    

        parameters{
            // need plugin ---List Git Branches Parameter---
                // remoteURL(git_url)
        listGitBranches {
            branchFilter('.*')
            defaultValue('main')
            name('BRANCH_OR_TAG')
            type('PT_BRANCH')
            remoteURL(git_url)
            credentialsId('github-credentials')
            sortMode('DESCENDING')
            selectedValue('TOP')
            listSize('100')
            tagFilter('')
            quickFilterEnabled(true)
        }
        //    gitParameter {
        //         name('BRANCH_OR_TAG') 
        //         description('Select a branch or tag to build') 
        //         type('PT_BRANCH') 
        //         branch('main') 
        //         branchFilter('origin/*') 
        //         tagFilter('*') 
        //         useRepository('https://github.com/gedharizka/tweet-trend.git') 
        //         defaultValue('main') 
        //         selectedValue('TOP') 
        //         quickFilterEnabled(true) 
        //         listSize('10') 
        //         sortMode('ASCENDING_SMART') 
        //     }
        }

        logRotator {    
            numToKeep(10)    
        }        

        definition {
            cps{
                sandbox()
                script('''

node(){
    try {
        stage("Clone Repository"){
            echo "==== Clone ===="
            def branch = "${params.BRANCH_OR_TAG}"
            def remove_prefix = "refs/heads/"
            if (branch.startsWith(remove_prefix)) {
                branch = branch.substring(remove_prefix.size())
            }
            echo "===== branch : ${branch} ======"
            git([url: 'https://github.com/gedharizka/tweet-trend.git', branch: branch])
        }

        withEnv([
            "PATH=/opt/apache-maven-3.9.6/bin:$PATH"
        ]){
            stage("Build"){
                echo """ ==== mvn === """
                sh""" mvn --version """
                sh 'mvn clean deploy -Dmaven.test.skip=true'
            }

            stage("Test"){
                echo " ===> unit test started <==="
                sh """ mvn surefire-report:report """
                echo " ===> unit test ended <==="
            }

            stage("SonarQube Scan"){
                withSonarQubeEnv('sonar-docker-server'){
                    withCredentials([string(credentialsId:'sonar-token',variable:'SONAR_TOKEN')]){
                        sh """
                            mvn clean verify sonar:sonar \
                            -Dsonar.projectKey=tweet-trend \
                            -Dsonar.projectName=tweet-trend \
                            -Dsonar.host.url=http://localhost:9001 \
                            -Dsonar.login=${SONAR_TOKEN}
                        """
                    }
                }
            }

            stage('Quality Gate') {
                timeout(time: 5, unit: 'MINUTES') { // Just in case something goes wrong, pipeline will be killed after a timeout
                    def qg = waitForQualityGate() // Reuse taskId previously collected by withSonarQubeEnv
                    if (qg.status != 'OK') {
                        error "Pipeline aborted due to quality gate failure: ${qg.status}"
                    }   
                }
                
            }

        }

         


        stage("Build Docker Images"){
            echo"======> Build Images <======>"
            sh """ docker build -t gedharizka/'''+repository_name+''':latest ."""
            sh """ docker image ls"""
        }

        stage("Build"){
            echo"Build"
            sh """ docker images """
        }

    }catch (Exception e){
        echo "Error"

    }finally {
        echo "==== Finaly ===="
    }
}

// pipeline {
//     agent any
//     environment{
//         PATH = "/opt/apache-maven-3.9.6/bin:$PATH"
//     }
//     stages {
//         stage('Checkout') {
//             steps {
//                 def branch = "${params.BRANCH_OR_TAG}"
//                 echo 'Checking out code...'
//                 git([url: 'https://github.com/gedharizka/tweet-trend.git', branch: branch])
                
//             }
//         }
//         stage('Build') {
//             steps {
//                 echo " ===> Build started <==="
                
//             }
//         }
//         stage('Test') {
//             steps {
//                 echo " ===> unit test started <==="
                
//             }
//         }



//     }
//     post {
//         always {
//             echo 'Pipeline complete, cleaning up workspace...'
//             cleanWs()
//         }
//     }
// }
                ''')
            }
            
        }




    }
}