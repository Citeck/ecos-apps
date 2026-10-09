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

### COREDEV-631: совместимость Spring Boot Admin

Parent обновлён до опубликованного `ecos-webapp-spring-hibernate-parent:3.34.9`.
Эта версия заменяет `jackson-datatype-hibernate5` на `jackson-datatype-hibernate6`,
восстанавливая `sba-settings.js`: Hibernate 6 использует Jakarta Persistence,
а старый Jackson-модуль обращается к `javax.persistence.Transient`.
Maven получает релизный parent из Nexus; локальная установка commons для запуска
приложения не требуется.

Spring Boot Admin обновлён до 3.5.9, где исправлен шаблон `variables.css`,
несовместимый с ограничениями Thymeleaf 3.1.4 и новее.
Подробности: [Spring Boot Admin 3.5.9](https://github.com/codecentric/spring-boot-admin/releases/tag/3.5.9).

Проверка хотфикса 09.10.2026: полный `mvn -DCI=true verify` для commons 3.34.9
завершился без ошибок (включая регрессионный тест Jackson). Чистый
`mvn clean verify` для `eapps` 2.28.1 с опубликованным parent 3.34.9:
99 тестов, 0 ошибок, 0 пропусков, `BUILD SUCCESS`. На локально запущенном
`eapps` через gateway проверены HTTP 200 для wallboard, `sba-settings.js`,
`variables.css`, API `instances` (с `Accept: application/json`) и основных JS/CSS
ресурсов; локальный `eapps` имеет статус `UP`. Дополнительно wallboard открыт
через Playwright с установленным Chromium: страница отображается, скриншот
проверен, ошибок JavaScript и ответов HTTP 4xx/5xx нет. Все приложения имеют
статус `UP`, кроме `edi-sim` (`OFFLINE`).

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
