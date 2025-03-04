import groovy.json.JsonSlurper

def inputFile = readFileFromWorkspace("cakap.json")
def project_env = "cakap"
def InputJSON = new JsonSlurper().parseText(inputFile)
def git_url = "https://gitlab.com/k8s5395209/k8s-go-postgresql.git"

for (i=0; i<InputJSON.project.size(); i++) {

    def ns_project = InputJSON.project[i].name
    def project_name = InputJSON.project[i].repository_name
    def repository_name = InputJSON.project[i].repository_name
    def num_to_keep = InputJSON.num_to_keep
    def repo_owner_name = InputJSON.repo_owner_name

    pipelineJob("${project_env}/${project_name}") {    
        parameters{
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

        }

        logRotator {    
            numToKeep(num_to_keep)    
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
            git([url: 'https://gitlab.com/k8s5395209/k8s-go-postgresql.git', branch: branch])
        }

        stage("Build Docker Images"){
            echo"======> Build Images <======"
            sh """ cd code"""
            sh """ docker build -t gedharizka/'''+repository_name+''':latest ./code """
            sh """ docker image ls"""

        }

        stage("Docker Push"){
            withCredentials([usernamePassword(credentialsId: 'docker-credential', usernameVariable: 'DOCKER_HUB_USERNAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]){
                echo "Logging into Docker Hub..."
                sh""" docker login -u ${DOCKER_HUB_USERNAME} -p ${DOCKER_HUB_PASSWORD} """
                echo "Pushing Docker image..."
                sh """ docker push gedharizka/'''+repository_name+''':latest """ 
            }
        }

        stage("Deploy Kubernetes"){

            sh """ kubectl apply -f manifests/application.yaml """
            sh """ kubectl rollout restart deployment/do-sample-app  """
            sh """ kubectl rollout status deployment/do-sample-app  """

        }

    }catch (Exception e){
        echo "Error"

    }finally {
        echo "==== Finaly ===="
    }
}
                ''')
            }
            
        }




    }
}