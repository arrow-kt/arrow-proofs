---
layout: docs-inject
title: Inject - Kotlin
permalink: /kotlin/
---

# Kotlin

In the following example we will create a minimal application that demonstrates how Arrow Inject works.

Apply the plugin to the project:

```kotlin
plugins {
    id("io.arrow-kt.inject") version "$version"
}
```

## Usage

The application has a list of services that will act as providers for dependency injection
in the application.

- `Data sources`
  - `Database`: local database that can have a user or being empty
  - `Remote`: network data source to fetch a user if it isn't in the database
- `Repository`: Abstract away access to data sources

#### Declaring providers

A provider can be registered for any type. In the following example we will register a provider for the `Database` and
`Remote` services. To register a provider we use the `arrow.inject.annotations.Provider` annotation.

```kotlin
import arrow.inject.annotations.Provider

@Provider class Database {
    suspend fun findUser(): User?
}

@Provider class Remote {
    suspend fun fetchUser(): User
}
```

#### Using dependencies

To use your `@Provider` dependencies you can simply use kotlin's native feature for context receivers.
Context receivers place in scope all declared types inside `context(...)` making functions and members in tose types
immediately available in the declaration.

In this example `Repository` uses `Database` and `Remote` services as context dependencies.
`Repository` is also `@Provider` on its own, so it can be used as a dependency in other places.

```kotlin
context(Database, Remote)
@Provider class Repository {
    suspend fun getUser(): User = findUser() ?: fetchUser()
}
```

Finally, we can bring into scope at the entry point of our application our `Repository`.

#### Resolving dependencies

Calling the function `context<Repository>()` brings into `main` the `Repository` which
on its own contains the `Database` and `Remote` services.

```kotlin
import arrow.inject.annotations.context

suspend fun main() {
    context<Repository>()
    val user = getUser()
}
```

Arrow Inject will automatically resolve the dependencies for you. 
If you were not using Arrow Inject you would have to manually resolve the dependencies.

```diff
import arrow.inject.annotations.context

suspend fun main() {
+  context<Repository>()
+  val user = getUser()
-  with(Database()) {
-    with(Remote()) {
-      with(Repository()) {
-        val user = getUser()
-      }  
-    }
-  }
}
```
