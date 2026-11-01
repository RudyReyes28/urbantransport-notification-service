pipeline {
    agent any

    environment {
        JAVA_HOME = '/usr/lib/jvm/java-21-openjdk-amd64'
        PATH = "${JAVA_HOME}/bin:${env.PATH}"
        IMAGE_NAME = 'microservice-notification'
        DEPLOY_DIR = '/opt/microservices/notification-service'
    }

    stages {

        stage('Checkout') {
            steps { checkout scm }
        }

        stage('Test') {
            steps {
                sh 'java -version'
                sh 'mvn -version'
                sh 'mvn test -B'
            }
            post {
                always {
                    junit 'target/surefire-reports/*.xml'
                }
            }
        }

        stage('Build Docker Image') {
            steps {
                sh 'docker build -t $IMAGE_NAME:latest -t $IMAGE_NAME:$BUILD_NUMBER .'
            }
        }

        stage('Deploy') {
            steps {
                sh '''
                    mkdir -p $DEPLOY_DIR
                    cp docker-compose.yml $DEPLOY_DIR/docker-compose.yml
                    cd $DEPLOY_DIR
                    docker compose up -d --no-deps notification-service

                    # Limpieza: imágenes sin etiqueta y versiones numeradas viejas (conserva 2)
                    docker image prune -f
                    docker images $IMAGE_NAME --format '{{.Tag}}' | grep -E '^[0-9]+$' | sort -n | head -n -2 | xargs -r -I{} docker rmi $IMAGE_NAME:{} || true
                '''
            }
        }
    }

    post {
        failure { echo 'Pipeline del notification-service falló' }
        success { echo 'notification-service desplegado exitosamente' }
    }
}