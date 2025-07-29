package org.babelserver.ktor.routesfile

import com.google.devtools.ksp.symbol.KSFile
import com.squareup.kotlinpoet.*
import com.squareup.kotlinpoet.ksp.addOriginatingKSFile
import io.ktor.server.routing.*

/**
 * Generates Kotlin code for type-safe routing based on routes definitions
 */
class RoutesCodeGenerator {
    
    fun generateRoutesClass(
        className: String,
        routes: List<RouteDefinition>,
        originatingFile: KSFile? = null
    ): TypeSpec {
        
        val classBuilder = TypeSpec.classBuilder(className)
            .addModifiers(KModifier.OPEN)
            
        // Add originating file if provided (for KSP processing)
        if (originatingFile != null) {
            classBuilder.addOriginatingKSFile(originatingFile)
        }
        
        // Add controller instances
        val controllerTypes = routes.map { it.controller }.distinct()
        controllerTypes.forEach { controller ->
            val property = generateControllerProperty(controller)
            classBuilder.addProperty(property)
        }
        
        // Add route registration method
        val routingMethod = generateRoutingMethod(routes)
        classBuilder.addFunction(routingMethod)
        
        return classBuilder.build()
    }
    
    private fun generateRoutingMethod(routes: List<RouteDefinition>): FunSpec {
        val method = FunSpec.builder("configureRoutes")
            .addParameter("routing", Routing::class)
            .addModifiers(KModifier.OPEN)
        
        method.beginControlFlow("routing.apply")
        
        routes.forEach { route ->
            val methodName = route.method.value.lowercase()
            val pathWithParams = convertPathToKtorFormat(route.path)
            
            method.beginControlFlow("$methodName(%S)", pathWithParams)
            
            // Generate parameter extraction inline
            // Extract path parameters
            route.pathParameters.forEach { param ->
                when (param.type.lowercase()) {
                    "int", "kotlin.int" -> method.addStatement(
                        "val %L: Int = call.parameters[%S]?.toIntOrNull() ?: throw BadRequestException(%S)",
                        param.name,
                        param.name,
                        "Invalid int parameter: ${param.name}"
                    )
                    "long", "kotlin.long" -> method.addStatement(
                        "val %L: Long = call.parameters[%S]?.toLongOrNull() ?: throw BadRequestException(%S)",
                        param.name,
                        param.name,
                        "Invalid long parameter: ${param.name}"
                    )
                    "boolean", "kotlin.boolean" -> method.addStatement(
                        "val %L: Boolean = call.parameters[%S]?.toBooleanStrictOrNull() ?: throw BadRequestException(%S)",
                        param.name,
                        param.name,
                        "Invalid boolean parameter: ${param.name}"
                    )
                    "double", "kotlin.double" -> method.addStatement(
                        "val %L: Double = call.parameters[%S]?.toDoubleOrNull() ?: throw BadRequestException(%S)",
                        param.name,
                        param.name,
                        "Invalid double parameter: ${param.name}"
                    )
                    "float", "kotlin.float" -> method.addStatement(
                        "val %L: Float = call.parameters[%S]?.toFloatOrNull() ?: throw BadRequestException(%S)",
                        param.name,
                        param.name,
                        "Invalid float parameter: ${param.name}"
                    )
                    else -> method.addStatement(
                        "val %L: String = call.parameters[%S] ?: throw BadRequestException(%S)",
                        param.name,
                        param.name,
                        "Missing parameter: ${param.name}"
                    )
                }
            }
            
            // Extract query parameters and other non-path parameters
            route.methodParameters.forEach { methodParam ->
                // Skip path parameters (query parameters and other non-path parameters)
                if (route.pathParameters.none { it.name == methodParam.name }) {
                    when (methodParam.type.lowercase()) {
                        "int", "kotlin.int" -> method.addStatement(
                            "val %L: Int = call.request.queryParameters[%S]?.toIntOrNull() ?: throw BadRequestException(%S)",
                            methodParam.name,
                            methodParam.name,
                            "Invalid int query parameter: ${methodParam.name}"
                        )
                        "long", "kotlin.long" -> method.addStatement(
                            "val %L: Long = call.request.queryParameters[%S]?.toLongOrNull() ?: throw BadRequestException(%S)",
                            methodParam.name,
                            methodParam.name,
                            "Invalid long query parameter: ${methodParam.name}"
                        )
                        "boolean", "kotlin.boolean" -> method.addStatement(
                            "val %L: Boolean = call.request.queryParameters[%S]?.toBooleanStrictOrNull() ?: throw BadRequestException(%S)",
                            methodParam.name,
                            methodParam.name,
                            "Invalid boolean query parameter: ${methodParam.name}"
                        )
                        "double", "kotlin.double" -> method.addStatement(
                            "val %L: Double = call.request.queryParameters[%S]?.toDoubleOrNull() ?: throw BadRequestException(%S)",
                            methodParam.name,
                            methodParam.name,
                            "Invalid double query parameter: ${methodParam.name}"
                        )
                        "float", "kotlin.float" -> method.addStatement(
                            "val %L: Float = call.request.queryParameters[%S]?.toFloatOrNull() ?: throw BadRequestException(%S)",
                            methodParam.name,
                            methodParam.name,
                            "Invalid float query parameter: ${methodParam.name}"
                        )
                        else -> method.addStatement(
                            "val %L: String = call.request.queryParameters[%S] ?: throw BadRequestException(%S)",
                            methodParam.name,
                            methodParam.name,
                            "Missing query parameter: ${methodParam.name}"
                        )
                    }
                }
            }
            
            // Generate controller method call - always pass call as first parameter
            val controllerProperty = route.controller.split(".").last().replaceFirstChar { it.lowercase() }
            val parameterList = buildMethodParameterList(route)
            
            method.addStatement(
                "%L.%L(call%L)",
                controllerProperty,
                route.action,
                if (parameterList.isNotEmpty()) ", $parameterList" else ""
            )
            
            method.endControlFlow()
        }
        
        method.endControlFlow()
        
        return method.build()
    }
    
    
    private fun generateControllerProperty(controllerClassName: String): PropertySpec {
        val controllerClass = parseClassName(controllerClassName)
        val propertyName = controllerClassName.split(".").last().replaceFirstChar { it.lowercase() }
        
        return PropertySpec.builder(propertyName, controllerClass)
            .addModifiers(KModifier.PRIVATE)
            .initializer("%T()", controllerClass)
            .build()
    }
    
    private fun parseClassName(className: String): ClassName {
        val parts = className.split(".")
        return if (parts.size > 1) {
            val packageName = parts.dropLast(1).joinToString(".")
            val simpleName = parts.last()
            ClassName(packageName, simpleName)
        } else {
            ClassName("", className)
        }
    }
    
    private fun convertPathToKtorFormat(path: String): String {
        // Convert :param to {param} for Ktor
        return path.replace(":([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()) { match ->
            "{${match.groupValues[1]}}"
        }
    }
    
    
    private fun buildMethodParameterList(route: RouteDefinition): String {
        val params = mutableListOf<String>()
        
        // Add path parameters
        route.pathParameters.forEach { pathParam ->
            params.add(pathParam.name)
        }
        
        // Add query parameters and other non-path parameters
        route.methodParameters.forEach { methodParam ->
            // Skip path parameters (already added)
            if (route.pathParameters.none { it.name == methodParam.name }) {
                params.add(methodParam.name)
            }
        }
        
        return params.joinToString(", ")
    }
}