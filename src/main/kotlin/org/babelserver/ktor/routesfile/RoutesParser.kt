package org.babelserver.ktor.routesfile

import io.ktor.http.*
import java.io.File

/**
 * Routes parser that reads routes files and produces route definitions
 */
class RoutesParser {
    
    fun parseRoutesFile(file: File): List<RouteDefinition> {
        require(file.exists()) {
            "Routes file not found: ${file.path}"
        }

        return file.readLines()
            .mapIndexedNotNull { index, line -> parseLine(line.trim(), index + 1) }
    }
    
    fun parseRoutesFile(content: String): List<RouteDefinition> {
        return content.lines()
            .mapIndexedNotNull { index, line -> parseLine(line.trim(), index + 1) }
    }
    
    private fun parseLine(line: String, lineNumber: Int): RouteDefinition? {
        // Skip empty lines and comments
        if (line.isBlank() || line.startsWith("#")) {
            return null
        }
        
        // Split by whitespace, but only into 3 parts to preserve method signature with spaces
        val parts = line.split("\\s+".toRegex(), limit = 3).filter { it.isNotBlank() }

        require(parts.size >= 3) {
            "Invalid route definition at line $lineNumber: $line"
        }

        val method = parseHttpMethod(parts[0]) 
            ?: throw IllegalArgumentException("Invalid HTTP method '${parts[0]}' at line $lineNumber")
        val path = parts[1]
        val controllerAction = parts[2].trim()
        
        // Parse controller and action from "controllers.MyController.myAction(param: Type)"
        val (controller, action) = parseControllerAction(controllerAction, lineNumber)
        
        // Parse method parameters from the controller action signature
        val methodParameters = parseMethodParameters(controllerAction)
        
        // Parse path parameters from URL pattern (using method parameters for types)
        val pathParameters = extractPathParameters(path, methodParameters)
        
        // Validate that all path parameters have corresponding method parameters
        validateParameterMatching(pathParameters, methodParameters, lineNumber, line)
        
        return RouteDefinition(
            method = method,
            path = path,
            controller = controller,
            action = action,
            pathParameters = pathParameters,
            methodParameters = methodParameters,
            lineNumber = lineNumber
        )
    }
    
    private fun parseHttpMethod(methodStr: String): HttpMethod? {
        return when (methodStr.uppercase()) {
            "GET" -> HttpMethod.Get
            "POST" -> HttpMethod.Post
            "PUT" -> HttpMethod.Put
            "DELETE" -> HttpMethod.Delete
            "PATCH" -> HttpMethod.Patch
            "HEAD" -> HttpMethod.Head
            "OPTIONS" -> HttpMethod.Options
            else -> null
        }
    }
    
    private fun parseControllerAction(controllerAction: String, lineNumber: Int): Pair<String, String> {
        // Handle cases like "controllers.MyController.myAction" or "controllers.MyController.myAction(params)"
        val withoutParams = controllerAction.substringBefore("(")
        val parts = withoutParams.split(".")

        require(parts.size >= 2) {
            "Invalid controller action format at line $lineNumber: $controllerAction"
        }

        val action = parts.last()
        val controller = parts.dropLast(1).joinToString(".")
        
        // Validate that controller and action are valid Kotlin identifiers
        require(isValidKotlinIdentifier(action)) {
            "Invalid action name '$action' at line $lineNumber: must be a valid Kotlin identifier"
        }

        // Validate controller parts (package and class names)
        val controllerParts = controller.split(".")
        controllerParts.forEach { part ->
            require(isValidKotlinIdentifier(part)) {
                "Invalid controller name '$controller' at line $lineNumber: '$part' is not a valid Kotlin identifier"
            }
        }
        
        return controller to action
    }
    
    private fun isValidKotlinIdentifier(name: String): Boolean {
        if (name.isEmpty()) return false
        if (!name.first().isLetter() && name.first() != '_') return false
        return name.all { it.isLetterOrDigit() || it == '_' }
    }
    
    private fun extractPathParameters(path: String, methodParameters: List<MethodParameter>): List<PathParameter> {
        val regex = ":([a-zA-Z_][a-zA-Z0-9_]*)".toRegex()

        return regex.findAll(path)
            .map { match ->
                val paramName = match.groupValues[1]
                // Find the type from method parameters
                val paramType = methodParameters.find { it.name == paramName }?.type ?: "String"
                PathParameter(paramName, paramType)
            }
            .toList()
    }

    private fun parseMethodParameters(controllerAction: String): List<MethodParameter> {
        val paramStart = controllerAction.indexOf("(")
        val paramEnd = controllerAction.lastIndexOf(")")

        if (paramStart == -1 || paramEnd == -1 || paramEnd <= paramStart) {
            return emptyList()
        }

        val paramString = controllerAction.substring(paramStart + 1, paramEnd)
        if (paramString.isBlank()) {
            return emptyList()
        }

        val parsedParams = paramString
            .split(",")
            .mapNotNull { param ->
                val trimmed = param.trim()
                val colonIndex = trimmed.indexOf(":")

                if (colonIndex != -1) {
                    val name = trimmed.substring(0, colonIndex).trim()
                    val type = trimmed.substring(colonIndex + 1).trim()
                    MethodParameter(name, type)
                } else {
                    null
                }
            }
        
        // Check for call: ApplicationCall parameter and provide a specialized error message
        parsedParams.forEach { param ->
            if (param.name == "call" && param.type == "ApplicationCall") {
                throw IllegalArgumentException(
                    "Found 'call: ApplicationCall' parameter in controller action '$controllerAction'. " +
                    "This parameter is automatically included in the compilation process and should be omitted from " +
                    "the routes file. Please update your routes file to exclude 'call: ApplicationCall' parameters."
                )
            }
        }
        
        return parsedParams
    }

    private fun validateParameterMatching(pathParameters: List<PathParameter>, methodParameters: List<MethodParameter>, lineNumber: Int, line: String) {
        val methodParamNames = methodParameters.mapTo(HashSet()) { it.name }
        val pathParamNames = pathParameters.mapTo(HashSet()) { it.name }

        // Check if all path parameters have corresponding method parameters
        // This is strict - every path parameter must have a matching method parameter
        val missingMethodParams = pathParamNames - methodParamNames
        require(missingMethodParams.isEmpty()) {
            "Path parameters ${missingMethodParams.joinToString(", ") { ":$it" }} " +
                    "do not have corresponding method parameters at line $lineNumber: $line"
        }

        // We don't validate the opposite direction (method params without path params)
        // because methods can have query parameters, request body parameters, etc.
        // that do not appear in the path
    }
}

/**
 * Represents a route definition with metadata
 */
data class RouteDefinition(
    val method: HttpMethod,
    val path: String,
    val controller: String,
    val action: String,
    val pathParameters: List<PathParameter>,
    val methodParameters: List<MethodParameter>,
    val lineNumber: Int
) {
    val fullControllerPath: String
        get() = "$controller.$action"
        
    val routeName: String
        get() = "${method.value.lowercase()}_${path.removePrefix("/").replace("/", "_").replace(":", "").replace("-", "_")}"
}

data class PathParameter(
    val name: String,
    val type: String
)

data class MethodParameter(
    val name: String,
    val type: String
)