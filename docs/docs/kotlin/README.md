---
layout: docs-inject
title: Inject - Kotlin
permalink: /kotlin/
---

# Kotlin

Apply the plugin to the project:

```kotlin
plugins {
    id("io.arrow-kt.inject") version "$version"
}
```

## Usage

- `Data sources`
  - `Database`: local database that can have a user or being empty
  - `Remote`: network data source to fetch a user if it isn't in the database
- `Repository`: Abstract away access to data sources

#### Data sources

```kotlin
@Provider class Database {
    suspend fun findUser(): User?
}

@Provider class Remote {
    suspend fun fetchUser(): User
}
```

#### Repository

```kotlin
context(Database, Remote)
@Provider class Repository {
    suspend fun getUser(): User = findUser() ?: fetchUser()
}
```

#### Application

```kotlin
suspend fun main() {
    context<Repository>()
    val user = getUser()
}
```
