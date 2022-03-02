[comment]: <> ([![Maven Central]&#40;https://maven-badges.herokuapp.com/maven-central/com.futuremind/koru/badge.svg&#41;]&#40;https://maven-badges.herokuapp.com/maven-central/com.futuremind/koru&#41;)

# This project is forked and modified from [koru](https://github.com/FutureMind/koru).

## What is modified?

- Rewrite with ksp.
- Generate extension functions instead of wrapper classes.
- Changed package name and artifact group to avoid conflicts with the original library.
- This README.md is modified to reflect usage of this modified project.

# Koru

Automatically generates wrappers for `suspend` functions and `Flow` for easy access from Swift code in Kotlin Multiplatform projects.

Inspired by https://touchlab.co/kotlin-coroutines-rxswift/ by Russell Wolf.

**Note**: this library is in beta state - the API should be mostly stable but there might be minor changes.

## Getting started

To get started, consult the Basic example below, read [introductory article](https://medium.com/futuremind/handling-kotlin-multiplatform-coroutines-in-swift-koru-4a80b93f232b) or check out the [example repo](https://github.com/FutureMind/koru-example).

### Basic example

Let's say you have a class in the `shared` module, that looks like this:

```kotlin
@ToNativeClass
class LoadUserUseCase(private val service: Service) {

    suspend fun loadUser(username: String) : User? = service.loadUser(username)
    
}
```

Such use case can be easily consumed from Android code, but in Kotlin Native (e.g. iOS) suspend functions generate a completion handler which is a bit of a PITA to work with.

When you add `@ToNativeClass` annotation to the class, a file is generated:

```kotlin

public fun LoadUserUseCase.loadUserNative(username: String): SuspendWrapper<User?> = 
  SuspendWrapper(null) { this.loadUser(username) }
  

```

Notice that in place of `suspend` function, we get a function exposing `SuspendWrapper`. When you expose generated function to your Swift code, it can be consumed like this:

```swift
loadUserUseCase.loadUserNative(username: "foo").subscribe(
            scope: coroutineScope, //this can be provided automatically, more on that below
            onSuccess: { user in print(user?.description() ?? "none") },
            onThrow: { error in print(error.description())}
        )
```

From here it can be easily wrapped into RxSwift `Single<User?>` or Combine `AnyPublisher<User?, Error>`.

## Generated functions / properties - Suspend, Flow and regular

The wrappers generate different return types based on the original member signature

| Original | Wrapper |
|-|-|
| `suspend` fun returning `T` | fun returning `SuspendWrapper<T>` |
| fun returning `Flow<T>` | fun returning `FlowWrapper<T>` |
| fun returning `T` | Nop |
| val / var returning `Flow<T>` | val returning `FlowWrapper<T>` |
| val / var returning `T` | Nop |

So, for example, this class:

```kotlin
@ToNativeClass
class LoadUserUseCase(private val service: Service) {

    suspend fun loadUser(username: String) : User? = service.loadUser(username)
    
    fun observeUser(username: String) : Flow<User?> = service.observeUser(username)
    
    fun getUser(username: String) : User? = service.getUser(username)

    val someone : User? get() = service.getUser("someone")

    val someoneFlow : Flow<User> = service.observeUser("someone")

}
```

becomes:

```kotlin


public fun LoadUserUseCase.loadUser(username: String): SuspendWrapper<User?> =
    SuspendWrapper(null) { this.loadUser(username) }

public fun LoadUserUseCase.observeUser(username: String): FlowWrapper<User?> =
    FlowWrapper(null, this.observeUser(username))

public val LoadUserUseCase.someoneFlow: FlowWrapper<User>
    get() = com.futuremind.koru.FlowWrapper(null, this.someoneFlow)
    

```

## More options

### ~~Customizing generated names~~

This is not supported.

### Provide the scope automatically

One of the caveats of accessing suspend functions / Flows from Swift code is that you still have to provide `CoroutineScope` from the Swift code. This might upset your iOS team ;). In the spirit of keeping the shared code API as *business-focused* as possible, we can utilize `@ExportScopeProvider` to handle scopes automagically.

First you need to show the suspend wrappers where to look for the scope, like this:

```kotlin
@ExportedScopeProvider
class MainScopeProvider : ScopeProvider {

    override val scope = MainScope()
    
}
```

And then you provide the scope like this

```kotlin
@ToNativeClass(launchOnScope = MainScopeProvider::class)
```

Thanks to this, your Swift code can be simplified to just the callbacks, scope that launches coroutines is handled implicitly.

```swift
loadUserUseCase.loadUserNative(username: "some username").subscribe(
            onSuccess: { user in print(user?.description() ?? "none") },
            onThrow: { error in print(error.description())}
        )
```

<details>
  <summary>What happens under the hood?</summary>
    
  Under the hood, a top level property `val exportedScopeProvider_mainScopeProvider = MainScopeProvider()` is created. Then, it is injected into the generated file and then into `SuspendWrapper`s and `FlowWrapper`s as the default scope that `launch`es the coroutines. Remember, that you can always override with your custom scope if you need to.
  
  ```kotlin

  private val scopeProvider: ScopeProvider?

  fun LoadUserUseCaseIos.flow(foo: String) = FlowWrapper(scopeProvider, wrapped.flow(foo))
  fun LoadUserUseCaseIos.suspending(foo: String) = SuspendWrapper(scopeProvider) { wrapped.suspending(foo) }

  ```

</details>

### ~~Generate interfaces from classes and classes from interfaces~~

This part is not applicable. We only generate extension functions.

## Handling in Swift code

You can consume the coroutine wrappers directly as callbacks. But if you are working with Swift Combine, you can wrap those callbacks using [simple global functions](https://github.com/FutureMind/koru-example/blob/master/iosApp/iosApp/Utils/Coroutine2Combine.swift) (extension functions are not supported for Kotlin Native generic types at this time).

Then, you can call them like this:

```swift
createPublisher(wrapper: loadUserUseCase.loadUserNative(username: "Bob"))
    .sink(
        receiveCompletion: { completion in print("Completion: \(completion)") },
        receiveValue: { user in print("Hello from the Kotlin side \(user?.name)") }
    )
    .store(in: &cancellables)
```

Similar helper functions can be easily created for RxSwift.

## Download

The artifacts are available on Maven Central. 

To use the library in a KMM project, use this config in the `build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp") version "1.6.10-1.0.2"
    ...
}

kotlin {

  ...
  
  sourceSets {
        
        ...
  
        val commonMain by getting {
            dependencies {
                ...
                implementation("com.futuremind.koruksp:koruksp:0.11.0")

            }
        }
        
        val iosMain by getting {
            ...
            kotlin.srcDir("${buildDir.absolutePath}/generated/source/kaptKotlin/")
        }
        
    }
    
}

dependencies {
    val koruKspProcessor = "com.futuremind.koruksp:koruksp-processor:0.11.0"
    add("kspIosArm64", koruKspProcessor)
    add("kspIosX64", koruKspProcessor)
    // ... Other ios platforms
}
```
