pipelineJob('Simple-Pipeline-Job') {
    description("This is a simple Jenkins pipeline job created with Job DSL.")

    // Mengatur log rotator untuk menjaga hanya 10 build terakhir
    logRotator {
        numToKeep(10)
    }

    definition {
        cps {
            sandbox(true)
            script('''
                pipeline {
                    agent any

                    stages {
                        stage('Checkout') {
                            steps {
                                echo 'Checking out code...'
                                // Contoh checkout dari Git
                                checkout([$class: 'GitSCM', branches: [[name: '*/main']], userRemoteConfigs: [[url: 'https://github.com/your-repo.git']]])
                            }
                        }
                        stage('Build') {
                            steps {
                                echo 'Building the application...'
                                // Perintah build bisa ditambahkan di sini
                            }
                        }
                        stage('Test') {
                            steps {
                                echo 'Running tests...'
                                // Perintah untuk menjalankan test bisa ditambahkan di sini
                            }
                        }
                        stage('Deploy') {
                            steps {
                                echo 'Deploying the application...'
                                // Perintah untuk deploy bisa ditambahkan di sini
                            }
                        }
                    }

                    post {
                        always {
                            echo 'Pipeline complete, cleaning up workspace...'
                            cleanWs()
                        }
                        success {
                            echo 'Pipeline succeeded!'
                        }
                        failure {
                            echo 'Pipeline failed!'
                        }
                    }
                }
            ''')
        }
    }
}