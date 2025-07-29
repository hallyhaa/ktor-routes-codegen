package org.babelserver.ktor.routesfile

import com.google.devtools.ksp.processing.*
import com.google.devtools.ksp.symbol.*
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.*
import java.io.File

/**
 * KSP processor that generates type-safe routing code from @GenerateRoutes annotations
 */
class RoutesProcessor(environment: SymbolProcessorEnvironment) : SymbolProcessor {
    
    private val codeGenerator = environment.codeGenerator
    private val logger = environment.logger
    
    private val parser = RoutesParser()
    private val routesCodeGenerator = RoutesCodeGenerator()
    
    override fun process(resolver: Resolver): List<KSAnnotated> {
        val symbols = resolver.getSymbolsWithAnnotation(GenerateRoutes::class.qualifiedName!!)
        
        symbols.forEach { symbol ->
            if (symbol is KSClassDeclaration) {
                processGenerateRoutesAnnotation(symbol, resolver)
            }
        }
        
        return emptyList()
    }
    
    private fun processGenerateRoutesAnnotation(classDeclaration: KSClassDeclaration, resolver: Resolver) {
        val annotation = classDeclaration.annotations
            .find { it.shortName.asString() == "GenerateRoutes" }
            ?: return
            
        // Get routesFile argument, falling back to default value from annotation definition
        val routesFile = annotation.arguments.find { it.name?.asString() == "routesFile" }?.value as? String
            ?: annotation.defaultArguments.find { it.name?.asString() == "routesFile" }?.value as? String
            ?: error("No routesFile argument found and no default value available")
        
        try {
            generateRoutesFromFile(classDeclaration, routesFile, resolver)
        } catch (e: Exception) {
            logger.error("Failed to generate routes from file $routesFile: ${e.message}")
        }
    }
    
    private fun generateRoutesFromFile(classDeclaration: KSClassDeclaration, routesFilePath: String, resolver: Resolver) {
        val routesFile = findRoutesFile(routesFilePath)
        if (routesFile == null || !routesFile.exists()) {
            logger.error("Routes file not found: $routesFilePath")
            return
        }
        
        logger.info("Processing routes file: ${routesFile.path}")
        
        // Parse routes file
        val routes = try {
            parser.parseRoutesFile(routesFile)
        } catch (e: Exception) {
            logger.error("Failed to parse routes file: ${e.message}")
            return
        }
        
        if (routes.isEmpty()) {
            logger.warn("No routes found in file: $routesFilePath")
            return
        }
        
        logger.info("Found ${routes.size} routes")
        
        // Validate that controller classes exist
        routes.forEach { route ->
            try {
                val controllerName = route.controller
                resolver.getClassDeclarationByName(resolver.getKSNameFromString(controllerName))
                    ?: run {
                        logger.error("Controller class not found: $controllerName (referenced in route at line ${route.lineNumber})")
                        return
                    }
            } catch (e: Exception) {
                logger.warn("Could not validate controller class '${route.controller}' at line ${route.lineNumber}: ${e.message}")
            }
        }
        
        // Generate the routes class
        val packageName = classDeclaration.packageName.asString()
        val className = "GeneratedRoutes"
        val routesClass = routesCodeGenerator.generateRoutesClass(className, routes, classDeclaration.containingFile!!)
        
        // Generate the routes class
        val fileSpec = FileSpec.builder(packageName, className)
            .addType(routesClass)
            .addFileComment("Generated code from routes file: $routesFilePath")
            .addFileComment("Do not edit manually!")
            .addImport("io.ktor.server.routing", "get", "post", "put", "delete", "patch", "head", "options")
            .addImport("io.ktor.server.application", "ApplicationCall", "call")
            .addImport("io.ktor.server.plugins", "BadRequestException")
            .build()
            
        try {
            fileSpec.writeTo(codeGenerator, aggregating = false)
            logger.info("Generated routes class: $packageName.$className")
        } catch (e: Exception) {
            logger.error("Failed to write generated file: ${e.message}")
        }
    }
    
    private fun findRoutesFile(routesFilePath: String): File? {
        val possiblePaths = listOf(
            "src/main/resources/$routesFilePath", // Standard Maven layout
            routesFilePath // Exact specified path as a fallback
        )

        for (path in possiblePaths) {
            val file = File(path)
            if (file.exists()) {
                return file
            }
        }
        
        logger.warn("Routes file not found. Tried paths: ${possiblePaths.joinToString(", ")}")
        return null
    }
    
}

/**
 * KSP processor provider
 */
class RoutesProcessorProvider : SymbolProcessorProvider {
    override fun create(environment: SymbolProcessorEnvironment): SymbolProcessor {
        return RoutesProcessor(environment)
    }
}