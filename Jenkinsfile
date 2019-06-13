properties([
    [$class: 'RebuildSettings', autoRebuild: false, rebuildDisabled: false],
    [$class: 'ThrottleJobProperty', categories: [], limitOneJobWithMatchingParams: false, maxConcurrentPerNode: 0, maxConcurrentTotal: 0, paramsToUseForLimit: '', throttleEnabled: false, throttleOption: 'project'],
    parameters([
        string(name: 'ecos_apps_branch', defaultValue: 'develop', description: 'Branch of ecos-apps')
    ])
])
node {
  stage('Checkout SCM') {
    checkout([
      $class: 'GitSCM',
      branches: [[name: '${ecos_apps_branch}']],
      doGenerateSubmoduleConfigurations: false,
      extensions: [],
      submoduleCfg: [],
      userRemoteConfigs: [[
        credentialsId: 'bc074014-bab1-4fb0-b5a4-4cfa9ded5e66',
        url: 'git@bitbucket.org:citeck/ecos-apps.git'
      ]]
    ])
  }
  stage('Build ecos-uiserv') {
    sh "mvn clean package -DskipTests=true -Djib.docker.image.tag=${ecos_apps_branch} jib:dockerBuild"
  }
  stage('Push docker image') {
    withCredentials([usernamePassword(credentialsId: '3400f5ec-0ef3-4944-b59a-97e67680777a', passwordVariable: 'pass', usernameVariable: 'user')]) {
      sh "docker login -u $user -p $pass nexus.citeck.ru"
      sh "docker push nexus.citeck.ru/ecosapps:${ecos_apps_branch}"
    }
  }
}
