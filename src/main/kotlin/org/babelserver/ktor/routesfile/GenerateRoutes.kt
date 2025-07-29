package org.babelserver.ktor.routesfile

/**
 * Annotation to mark where routes should be generated from a routes file
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class GenerateRoutes(
    /**
     * Path to the routes file, relative to the project root
     */
    @Suppress("unused")
    val routesFile: String = "routes"
)
