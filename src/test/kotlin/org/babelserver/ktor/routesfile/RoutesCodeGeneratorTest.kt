package org.babelserver.ktor.routesfile

import com.squareup.kotlinpoet.*
import io.ktor.http.*
import kotlin.test.*

class RoutesCodeGeneratorTest {
    
    private val generator = RoutesCodeGenerator()
    
    @Test
    fun `should generate routes class with correct name`() {
        val routes = listOf(
            createTestRoute("GET", "/", "HomeController", "index")
        )
        
        val routesClass = generator.generateRoutesClass("TestRoutes", routes)
        
        assertEquals("TestRoutes", routesClass.name)
        assertTrue(routesClass.modifiers.contains(KModifier.OPEN))
    }
    
    @Test
    fun `should generate configureRoutes method`() {
        val routes = listOf(
            createTestRoute("GET", "/", "HomeController", "index"),
            createTestRoute("POST", "/users", "UserController", "create")
        )
        
        val routesClass = generator.generateRoutesClass("TestRoutes", routes)
        val configureMethod = routesClass.funSpecs.find { it.name == "configureRoutes" }
        
        assertNotNull(configureMethod)
        assertEquals(1, configureMethod.parameters.size)
        assertEquals("routing", configureMethod.parameters.first().name)
    }
    
    @Test
    fun `should generate configureRoutes method with inline route handling`() {
        val routes = listOf(
            createTestRoute("GET", "/users/:id", "UserController", "show", 
                pathParams = listOf(PathParameter("id", "String")))
        )
        
        val routesClass = generator.generateRoutesClass("TestRoutes", routes)
        val configureMethod = routesClass.funSpecs.find { it.name == "configureRoutes" }
        
        assertNotNull(configureMethod)
        assertEquals(1, configureMethod.parameters.size)
        assertEquals("routing", configureMethod.parameters.first().name)
        
        // Check that the method contains route registration and parameter extraction
        val methodCode = configureMethod.body.toString()
        assertTrue(methodCode.contains("get(\"/users/{id}\")"))
        assertTrue(methodCode.contains("userController.show"))
    }
    
    @Test
    fun `should generate controller properties`() {
        val routes = listOf(
            createTestRoute("GET", "/", "com.example.controllers.HomeController", "index"),
            createTestRoute("GET", "/users", "com.example.controllers.UserController", "list")
        )
        
        val routesClass = generator.generateRoutesClass("TestRoutes", routes)
        
        val homeControllerProp = routesClass.propertySpecs.find { it.name == "homeController" }
        val userControllerProp = routesClass.propertySpecs.find { it.name == "userController" }
        
        assertNotNull(homeControllerProp)
        assertNotNull(userControllerProp)
        assertTrue(homeControllerProp.modifiers.contains(KModifier.PRIVATE))
        assertTrue(userControllerProp.modifiers.contains(KModifier.PRIVATE))
    }
    
    @Test
    fun `should handle path parameter conversion for different types`() {
        val routes = listOf(
            createTestRoute("GET", "/api/products/:id", "ApiController", "getProduct",
                pathParams = listOf(PathParameter("id", "Int")))
        )
        
        val routesClass = generator.generateRoutesClass("TestRoutes", routes)
        val configureMethod = routesClass.funSpecs.find { it.name == "configureRoutes" }
        
        assertNotNull(configureMethod)
        // Check that the method contains type conversion logic
        val methodCode = configureMethod.body.toString()
        assertTrue(methodCode.contains("toIntOrNull()"))
        assertTrue(methodCode.contains("val id: Int ="))
    }
    
    @Test 
    fun `should convert Ktor path format correctly`() {
        val routes = listOf(
            createTestRoute("GET", "/users/:id/posts/:postId", "PostController", "show",
                pathParams = listOf(
                    PathParameter("id", "String"),
                    PathParameter("postId", "String")
                ))
        )
        
        val routesClass = generator.generateRoutesClass("TestRoutes", routes)
        val configureMethod = routesClass.funSpecs.find { it.name == "configureRoutes" }
        
        assertNotNull(configureMethod)
        // The generated code should convert :id to {id} for Ktor
        val methodCode = configureMethod.body.toString()
        assertTrue(methodCode.contains("/users/{id}/posts/{postId}"))
    }
    
    @Test
    fun `should generate unique controller instances`() {
        val routes = listOf(
            createTestRoute("GET", "/", "com.example.HomeController", "index"),
            createTestRoute("GET", "/about", "com.example.HomeController", "about"),
            createTestRoute("GET", "/users", "com.example.UserController", "list")
        )
        
        val routesClass = generator.generateRoutesClass("TestRoutes", routes)
        
        // Should only have 2 controller properties (HomeController and UserController)
        assertEquals(2, routesClass.propertySpecs.size)
        assertTrue(routesClass.propertySpecs.any { it.name == "homeController" })
        assertTrue(routesClass.propertySpecs.any { it.name == "userController" })
    }
    
    private fun createTestRoute(
        method: String,
        path: String,
        controller: String,
        action: String,
        pathParams: List<PathParameter> = emptyList(),
        methodParams: List<MethodParameter> = emptyList()
    ): RouteDefinition {
        val httpMethod = when (method.uppercase()) {
            "GET" -> HttpMethod.Get
            "POST" -> HttpMethod.Post
            "PUT" -> HttpMethod.Put
            "DELETE" -> HttpMethod.Delete
            "PATCH" -> HttpMethod.Patch
            else -> HttpMethod.Get
        }
        
        return RouteDefinition(
            method = httpMethod,
            path = path,
            controller = controller,
            action = action,
            pathParameters = pathParams,
            methodParameters = methodParams,
            lineNumber = 1
        )
    }
}