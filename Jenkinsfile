pipeline {
    agent {
        docker {
            image 'gradle:jdk11'
            args '-v nexusscripts:/home/gradle/.gradle'
        }
    }
    environment {
        GRADLE_ARGS = '--no-daemon'
    }
    stages {
        stage('publish') {
            when {
                not {
                    changeRequest()
                }
            }
            environment {
                ADMIN_NEXUS = credentials('adminnexus')
            }
            steps {
                sh './gradlew ${GRADLE_ARGS} uploadScripts -PnexusAuth=${ADMIN_NEXUS}'
            }
        }
    }
}