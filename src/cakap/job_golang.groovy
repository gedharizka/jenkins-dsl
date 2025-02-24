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
                // credentialsId('github-credentials')
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
            echo"======> Build Images <======"
            sh """ docker build -t gedharizka/'''+repository_name+''':latest ."""
            sh """ docker image ls"""
            // sh """ trivy image gedharizka/'''+repository_name+''':latest"""
        }

        stage("Scan image by Trivy"){
            echo"======> SCAN IMAGE <======>"
            // sh """ curl -o trivy-html.tpl https://raw.githubusercontent.com/aquasecurity/trivy/main/contrib/html.tpl """
            sh """ trivy image --format template --template "@/usr/local/share/trivy/templates/html.tpl" -o trivy-report.html gedharizka/'''+repository_name+''':latest """
            echo """ *** Scann COMPLETE *** """
            publishHTML(target: [
                allowMissing: false,
                alwaysLinkToLastBuild: true,
                keepAll: true,
                reportDir: ".",
                reportFiles: "trivy-report.html",
                reportName: "Trivy Security Report"
            ])
        }

        stage("Docker Push"){
            withCredentials([usernamePassword(credentialsId: 'docker-credential', usernameVariable: 'DOCKER_HUB_USERNAME', passwordVariable: 'DOCKER_HUB_PASSWORD')]){
                echo "Logging into Docker Hub..."
                sh""" docker login -u ${DOCKER_HUB_USERNAME} -p ${DOCKER_HUB_PASSWORD} """
                echo "Pushing Docker image..."
                sh """ docker push gedharizka/'''+repository_name+''':latest """ 
            }
        }

         stage("Clone Kubernetes Manifest") {
            echo "Cloning Kubernetes manifests..."
            git([url: 'https://github.com/gedharizka/manifest-tweet-trend.git', branch: 'aku-laku'])
            sh """ docker image rm gedharizka/'''+repository_name+''':latest """ 
        }

        stage("Deploy Kubernetes"){
             echo "Deploying to Kubernetes in namespace demo..."
                
            // Pastikan namespace `demo` ada sebelum apply
            sh """ 
                if ! kubectl get namespace '''+ns_project+''' >/dev/null 2>&1; then
                        echo "Namespace tidak ditemukan, membuat namespace..."
                        kubectl create namespace '''+ns_project+'''
                    else
                        echo "Namespace sudah ada, melanjutkan..."
                fi
            """

            // Deploy aplikasi ke namespace demo
            sh """ kubectl apply -f deployment.yaml -n '''+ns_project+''' """
            sh """ kubectl apply -f service.yaml -n '''+ns_project+''' """
            sh """ kubectl apply -f ingress.yaml -n '''+ns_project+''' """
            sh """ kubectl get pods -n '''+ns_project+''' -o wide """
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