package org.babelserver.ktor.routesfile

import io.ktor.http.*
import kotlin.test.*

class RoutesParserTest {
    
    private val parser = RoutesParser()
    
    @Test
    fun `should parse simple GET route`() {
        val routes = parser.parseRoutesFile("GET / HomeController.index()")
        
        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals(HttpMethod.Get, route.method)
        assertEquals("/", route.path)
        assertEquals("HomeController", route.controller)
        assertEquals("index", route.action)
    }
    
    @Test
    fun `should parse route with path parameters`() {
        val routes = parser.parseRoutesFile("GET /users/:id UserController.show(id: String)")
        
        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals(HttpMethod.Get, route.method)
        assertEquals("/users/:id", route.path)
        assertEquals("UserController", route.controller)
        assertEquals("show", route.action)
        assertEquals(1, route.pathParameters.size)
        assertEquals("id", route.pathParameters.first().name)
        assertEquals("String", route.pathParameters.first().type)
    }
    
    @Test
    fun `should parse route with multiple path parameters`() {
        val routes = parser.parseRoutesFile("GET /blog/:year/:month/:slug BlogController.showPost(year: Int, month: Int, slug: String)")
        
        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals(3, route.pathParameters.size)
        assertEquals("year", route.pathParameters[0].name)
        assertEquals("month", route.pathParameters[1].name)
        assertEquals("slug", route.pathParameters[2].name)
    }
    
    @Test
    fun `should parse different HTTP methods`() {
        val content = """
            GET /users UserController.list()
            POST /users UserController.create()
            PUT /users/:id UserController.update(id: String)
            DELETE /users/:id UserController.delete(id: String)
            PATCH /users/:id UserController.patch(id: String)
        """.trimIndent()
        
        val routes = parser.parseRoutesFile(content)
        
        assertEquals(5, routes.size)
        assertEquals(HttpMethod.Get, routes[0].method)
        assertEquals(HttpMethod.Post, routes[1].method)
        assertEquals(HttpMethod.Put, routes[2].method)
        assertEquals(HttpMethod.Delete, routes[3].method)
        assertEquals(HttpMethod.Patch, routes[4].method)
    }
    
    @Test
    fun `should parse controller with package name`() {
        val routes = parser.parseRoutesFile("GET / com.example.controllers.HomeController.index()")
        
        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals("com.example.controllers.HomeController", route.controller)
        assertEquals("index", route.action)
    }
    
    @Test
    fun `should parse method parameters`() {
        val routes = parser.parseRoutesFile("GET /api/search ApiController.search(q: String, limit: Int)")
        
        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals(2, route.methodParameters.size) // call: ApplicationCall is filtered out
        assertEquals("q", route.methodParameters[0].name)
        assertEquals("String", route.methodParameters[0].type)
        assertEquals("limit", route.methodParameters[1].name)
        assertEquals("Int", route.methodParameters[1].type)
    }
    
    @Test
    fun `should skip empty lines and comments`() {
        val content = """
            # This is a comment
            
            GET /users UserController.list()
            # Another comment
            
            POST /users UserController.create()
        """.trimIndent()
        
        val routes = parser.parseRoutesFile(content)
        
        assertEquals(2, routes.size)
        assertEquals("/users", routes[0].path)
        assertEquals("/users", routes[1].path)
    }
    
    @Test
    fun `should provide helpful error message when call ApplicationCall is used`() {
        try {
            parser.parseRoutesFile("GET /users UserController.list(call: ApplicationCall)")
            fail("Expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("call: ApplicationCall"))
            assertTrue(e.message!!.contains("automatically included"))
            assertTrue(e.message!!.contains("should be omitted"))
        }
    }
    
    @Test
    fun `should handle whitespace in route definitions`() {
        val content = """
            GET     /users               UserController.list()
            POST    /users/:id/activate  UserController.activate(id: String)
        """.trimIndent()
        
        val routes = parser.parseRoutesFile(content)
        
        assertEquals(2, routes.size)
        assertEquals("/users", routes[0].path)
        assertEquals("/users/:id/activate", routes[1].path)
    }
    
    @Test
    fun `should throw exception for invalid HTTP method`() {
        assertFailsWith<IllegalArgumentException> {
            parser.parseRoutesFile("INVALID / HomeController.index()")
        }
    }
    
    @Test
    fun `should throw exception for invalid route format`() {
        assertFailsWith<IllegalArgumentException> {
            parser.parseRoutesFile("GET")
        }
    }
    
    @Test
    fun `should throw exception for invalid controller action format`() {
        assertFailsWith<IllegalArgumentException> {
            parser.parseRoutesFile("GET / invalid")
        }
    }
    
    @Test
    fun `should generate correct route names`() {
        val routes = parser.parseRoutesFile("""
            GET /users UserController.list()
            POST /users/:id/activate UserController.activate(id: String)
            DELETE /api/v1/products/:id ApiController.deleteProduct(id: Int)
        """.trimIndent())
        
        assertEquals("get_users", routes[0].routeName)
        assertEquals("post_users_id_activate", routes[1].routeName)
        assertEquals("delete_api_v1_products_id", routes[2].routeName)
    }
    
    @Test
    fun `should parse complex paths correctly`() {
        val routes = parser.parseRoutesFile("GET /api/v1/users/:userId/posts/:postId/comments/:commentId CommentController.show(userId: String, postId: String, commentId: String)")
        
        assertEquals(1, routes.size)
        val route = routes.first()
        assertEquals("/api/v1/users/:userId/posts/:postId/comments/:commentId", route.path)
        assertEquals(3, route.pathParameters.size)
        assertEquals("userId", route.pathParameters[0].name)
        assertEquals("postId", route.pathParameters[1].name)
        assertEquals("commentId", route.pathParameters[2].name)
    }
}