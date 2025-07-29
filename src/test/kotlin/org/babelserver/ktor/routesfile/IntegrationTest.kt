package org.babelserver.ktor.routesfile

import kotlin.test.*

class IntegrationTest {
    
    @Test
    fun `should parse and generate code for complete routes file`() {
        val routesContent = """
            # Complete routes file example
            
            # Home
            GET     /                           HomeController.index()
            
            # User management
            GET     /users                      UserController.list()
            GET     /users/:id                  UserController.show(id: String)
            POST    /users                      UserController.create()
            PUT     /users/:id                  UserController.update(id: String)
            DELETE  /users/:id                  UserController.delete(id: String)
            
            # API with typed parameters
            GET     /api/products/:id           ApiController.getProduct(id: Int)
            GET     /api/search                 ApiController.search(q: String)
            POST    /api/products               ApiController.createProduct()
            
            # Complex paths
            GET     /blog/:year/:month/:slug    BlogController.showPost(year: Int, month: Int, slug: String)
            GET     /admin/users/:userId/posts/:postId/comments/:commentId AdminController.showComment(userId: String, postId: String, commentId: String)
        """.trimIndent()
        
        val parser = RoutesParser()
        val generator = RoutesCodeGenerator()
        
        // Parse routes
        val routes = parser.parseRoutesFile(routesContent)
        assertEquals(11, routes.size)
        
        // Verify different HTTP methods
        val methods = routes.map { it.method.value }.toSet()
        assertTrue(methods.contains("GET"))
        assertTrue(methods.contains("POST"))
        assertTrue(methods.contains("PUT"))
        assertTrue(methods.contains("DELETE"))
        
        // Verify path parameters are extracted correctly
        val blogRoute = routes.find { it.path == "/blog/:year/:month/:slug" }
        assertNotNull(blogRoute)
        assertEquals(3, blogRoute.pathParameters.size)
        assertEquals("year", blogRoute.pathParameters[0].name)
        assertEquals("month", blogRoute.pathParameters[1].name)
        assertEquals("slug", blogRoute.pathParameters[2].name)
        
        // Verify complex route with many parameters
        val adminRoute = routes.find { it.path.contains("comments") }
        assertNotNull(adminRoute)
        assertEquals(3, adminRoute.pathParameters.size)
        
        // Generate code
        val routesClass = generator.generateRoutesClass("GeneratedRoutes", routes)
        
        // Verify class structure
        assertEquals("GeneratedRoutes", routesClass.name)
        
        // Should have configureRoutes method
        val configureMethod = routesClass.funSpecs.find { it.name == "configureRoutes" }
        assertNotNull(configureMethod)
        
        // Should only have configureRoutes method (no individual route methods)
        val routeMethods = routesClass.funSpecs.filter { it.name != "configureRoutes" }
        assertEquals(0, routeMethods.size)
        
        // Should have controller properties for unique controllers
        val uniqueControllers = routes.map { it.controller.split(".").last() }.toSet()
        assertEquals(uniqueControllers.size, routesClass.propertySpecs.size)
        
        // Verify routing code is generated correctly in configureRoutes method
        val configureMethodCode = configureMethod.body.toString()
        assertTrue(configureMethodCode.contains("get(\"/\")"))
        assertTrue(configureMethodCode.contains("get(\"/users\")"))
        assertTrue(configureMethodCode.contains("post(\"/users\")"))
        assertTrue(configureMethodCode.contains("get(\"/blog/{year}/{month}/{slug}\")"))
        assertTrue(configureMethodCode.contains("homeController.index"))
        assertTrue(configureMethodCode.contains("userController.list"))
    }
    
    @Test
    fun `should handle edge cases in routes file`() {
        val routesContent = """
            # Edge cases
            
            # Route with no parameters
            GET /simple SimpleController.simple()
            
            # Route with many path segments
            GET /api/v1/users/:userId/posts/:postId/comments/:commentId/replies/:replyId ReplyController.show(userId: String, postId: String, commentId: String, replyId: String)
            
            # Route with query parameter in method signature
            GET /search SearchController.search(q: String, limit: Int, offset: Int)
            
            # Route with boolean parameter
            GET /admin/users/:id/toggle/:active AdminController.toggleUser(id: String, active: Boolean)
            
            # Route with different parameter types
            GET /stats/:year/:month StatsController.monthlyStats(year: Int, month: Int)
        """.trimIndent()
        
        val parser = RoutesParser()
        val routes = parser.parseRoutesFile(routesContent)
        
        assertEquals(5, routes.size)
        
        // Test simple route
        val simpleRoute = routes.find { it.path == "/simple" }
        assertNotNull(simpleRoute)
        assertEquals(0, simpleRoute.pathParameters.size)
        
        // Test route with many segments
        val complexRoute = routes.find { it.path.contains("replies") }
        assertNotNull(complexRoute)
        assertEquals(4, complexRoute.pathParameters.size)
        
        // Test route with query parameters in method
        val searchRoute = routes.find { it.controller == "SearchController" }
        assertNotNull(searchRoute)
        assertEquals(3, searchRoute.methodParameters.size) // q, limit, offset (call is filtered out)
        
        // Generate code to ensure no exceptions
        val generator = RoutesCodeGenerator()
        val routesClass = generator.generateRoutesClass("EdgeCaseRoutes", routes)
        
        assertNotNull(routesClass)
        // Should only have configureRoutes method (no individual route methods)
        assertEquals(1, routesClass.funSpecs.size)
    }
    
    @Test
    fun `should validate generated route names are valid Kotlin identifiers`() {
        val routesContent = """
            GET /api/v1/users ApiController.getUsers()
            POST /api/v2/products-list ProductController.listProducts()
            DELETE /admin/cache-clear AdminController.clearCache()
            GET /blog/2024/january/my-awesome-post BlogController.showPost()
        """.trimIndent()
        
        val parser = RoutesParser()
        val routes = parser.parseRoutesFile(routesContent)
        
        // Verify route names are valid Kotlin identifiers
        routes.forEach { route ->
            val routeName = route.routeName
            // Should not start with number
            assertFalse(routeName.first().isDigit(), "Route name '$routeName' starts with digit")
            // Should not contain invalid characters
            assertTrue(routeName.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*")), "Route name '$routeName' contains invalid characters")
        }
        
        val generator = RoutesCodeGenerator()
        val routesClass = generator.generateRoutesClass("ValidatedRoutes", routes)
        
        // Ensure all method names are valid
        routesClass.funSpecs.forEach { method ->
            if (method.name != "configureRoutes") {
                assertTrue(method.name.matches(Regex("[a-zA-Z_][a-zA-Z0-9_]*")), 
                          "Method name '${method.name}' is not a valid Kotlin identifier")
            }
        }
    }
}