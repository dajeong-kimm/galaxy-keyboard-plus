pipeline {
    agent any

    environment {
        POSTGRE_USERNAME = credentials('POSTGRE_USERNAME')
        POSTGRE_PASSWORD = credentials('POSTGRE_PASSWORD')
        POSTGRE_DB_NAME  = credentials('POSTGRE_DB_NAME')
    }

    stages {
        stage('Checkout') {
            steps {
                git branch: 'release', url: 'https://lab.ssafy.com/s12-final/S12P31E201.git', credentialsId: 'gitlab-credentials'
            }
        }

        stage('Docker Compose Up') {
            steps {
                sh '''
                echo "POSTGRE_USERNAME=$POSTGRE_USERNAME" > .env
                echo "POSTGRE_PASSWORD=$POSTGRE_PASSWORD" >> .env
                echo "POSTGRE_DB_NAME=$POSTGRE_DB_NAME" >> .env

                docker-compose down || true
                docker-compose pull
                docker-compose up -d --build
                '''
            }
        }

        stage('Deploy') {
            steps {
                echo 'Deploying...'
            }
        }
    }
}
