package app.fugitive.engine

/** Method-level lock on the JVM (Android touches the engine from several threads); no-op elsewhere. */
@OptIn(ExperimentalMultiplatform::class)
@OptionalExpectation
@Target(AnnotationTarget.FUNCTION)
expect annotation class SynchronizedOnJvm()
