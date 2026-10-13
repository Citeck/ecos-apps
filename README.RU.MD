![Citeck ECOS Logo](https://raw.githubusercontent.com/Citeck/ecos-ui/develop/public/img/logo/ecos-logo.svg)

# `ecos-apps`

**На других языках: [Английский](README.md)**

Добро пожаловать в репозиторий Citeck `ecos-apps`! Репозиторий содержит набор дополнительных приложений и модулей для платформы Citeck - системы для автоматизации бизнес-процессов и управления корпоративными данными.

## Начало работы

Если вы впервые знакомитесь с платформой Citeck и хотите запустить программное обеспечение локально, мы рекомендуем загрузить Docker-версию из [Demo-репозитория](https://github.com/Citeck/citeck-community).

## Зависимости

Для запуска этого приложения необходимы следующие компоненты из развертывания Citeck:

* zookeeper
* rabbitmq
* ecos-model
* ecos-registry

## Разработка

Чтобы запустить приложение в режиме разработки (dev), выполните:

```
./mvnw spring-boot:run
```

Если ваша IDE поддерживает прямой запуск Spring Boot-приложений, вы можете просто запустить класс 'ru.citeck.ecos.apps.EcosAppsApp' без дополнительных настроек.

При работе с Citeck Launcher сначала остановите `eapps` в лончере, затем запустите
локальное приложение с профилями `dev,dev_local`. Оно получит настройки через
Config Server лончера (`http://localhost:8761/config/`) и зарегистрируется в
окружении автоматически. Админка останется доступна по адресу
`http://localhost/gateway/eapps/admin/wallboard`; локальный порт приложения — 8089.

```bash
mvn spring-boot:run -Dspring-boot.run.profiles=dev,dev_local
```

Оба профиля нужны для локального запуска: `dev_local` подменяет адреса сервисов
на адреса, доступные с хоста. Перед возвратом к контейнеру остановите локальный
процесс и запустите `eapps` в лончере.

### Сборка для production

Для сборки приложения в production-режиме выполните:

```
./mvnw -Pprod clean package jib:dockerBuild -Djib.docker.image.tag=custom 
```

Чтобы убедиться, что всё работает, остановите оригинальный контейнер ecos-apps и запустите ecos-apps:custom вместо него.

### Тестирование

Для запуска тестов выполните:

```
./mvnw clean test
```

#### Качество кода

Для анализа качества кода используется Sonar. Вы можете запустить локальный сервер Sonar (доступный по адресу http://localhost:9001) командой:

```
docker compose -f docker/sonar.yml up -d
```

Затем выполните анализ Sonar:

```
./mvnw -Pprod clean test sonar:sonar
```

## Полезные ссылки

- [Документация](https://citeck-ecos.readthedocs.io/ru/latest/index.html) содержит более подробную информацию.

## Участие в разработке

Мы приветствуем вклад сообщества в развитие Citeck. Все участники проекта должны соблюдать [нормы поведения](https://github.com/rubygems/rubygems/blob/master/CODE_OF_CONDUCT.md).

## Поддержка

Если вам нужна помощь или у вас есть вопросы по Citeck `ecos-apps`, то создайте issue в этом репозитории или обратитесь в нашу [службу поддержки](mailto:support@citeck.ru).

## Лицензия

Citeck `ecos-apps` распространяется под лицензией [GNU Lesser General Public License](LICENSE).
