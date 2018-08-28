/*
 * Copyright 2014-2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.notes

import com.fasterxml.jackson.databind.ObjectMapper
import cz.petrbalat.spring.mvc.test.dsl.DslRequestBuilder
import cz.petrbalat.spring.mvc.test.dsl.performGet
import cz.petrbalat.spring.mvc.test.dsl.performPatch
import cz.petrbalat.spring.mvc.test.dsl.performPost
import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.hateoas.MediaTypes
import org.springframework.restdocs.JUnitRestDocumentation
import org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel
import org.springframework.restdocs.hypermedia.HypermediaDocumentation.links
import org.springframework.restdocs.hypermedia.LinkDescriptor
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.restdocs.payload.PayloadDocumentation.*
import org.springframework.restdocs.payload.SubsectionDescriptor
import org.springframework.restdocs.snippet.Snippet
import org.springframework.restdocs.templates.TemplateFormats.markdown
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext
import javax.servlet.RequestDispatcher

@RunWith(SpringRunner::class)
@SpringBootTest
class ApiDocumentation {

    @get:Rule
    val restDocumentation = JUnitRestDocumentation()

    @Autowired
    private lateinit var noteRepository: NoteRepository

    @Autowired
    private lateinit var tagRepository: TagRepository

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var context: WebApplicationContext

    private lateinit var mockMvc: MockMvc

    @Before
    fun setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply<DefaultMockMvcBuilder>(documentationConfiguration(this.restDocumentation).snippets()
                        .withTemplateFormat(markdown())).build()
    }

    @Test
    fun errorExample() {
        this.mockMvc.performGet("/error") {
            builder {
                requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/notes")
                requestAttr(RequestDispatcher.ERROR_MESSAGE, "The tag 'http://localhost:8080/tags/123' does not exist")
            }
            printRequestAndResponse()
            expect {
                status { isBadRequest }
                "error" jsonPathIs "Bad Request"
                jsonPath("timestamp") { value(`is`(notNullValue())) }
                "status" jsonPathIs 400
                jsonPath("path") { value(`is`(notNullValue())) }
            }

            document("error-example") {
                response {
                    "error" field { description("The HTTP error that occurred, e.g. `Bad Request` Clint") }
                    "message" field { description("A description of the cause of the error") }
                    "path" field { description("The path to which the request was made") }
                    "status" field { description("The HTTP status code, e.g. `400`") }
                    "timestamp" field { description("The time, in milliseconds, at which the error occurred") }
                }
            }

        }
    }

    fun DslRequestBuilder.document(identifier: String, vararg snippets: Snippet, block: DocumentationDslBuilder.() -> Unit = {}) {
        actions { DocumentationDslBuilder(snippets = snippets.toMutableList()).apply(block).andDocument(this, identifier) }
    }

    @DslMarker
    annotation class DocumentationDsl

    @DocumentationDsl
    class DocumentationDslBuilder(val snippets: MutableList<Snippet> = mutableListOf()) {

        private val responseFieldDescriptors = RequestResponseDslBuilder()
        private val requestFieldDescriptors = RequestResponseDslBuilder()
        private val linkDescriptors = mutableListOf<LinkDescriptor>()

        fun request(block: RequestResponseDslBuilder.() -> Unit) {
            requestFieldDescriptors.apply(block)
        }

        fun response(block: RequestResponseDslBuilder.() -> Unit) {
            responseFieldDescriptors.apply(block)
        }

        infix fun String.link(block: LinkDescriptor.() -> Unit) {
            val link = linkWithRel(this)
            link.block()
            linkDescriptors.add(link)
        }

        fun andDocument(actions: ResultActions, identifier: String) {
            if (responseFieldDescriptors.fieldDescriptors.isNotEmpty()) {
                snippets.add(responseFields(responseFieldDescriptors.fieldDescriptors))
            }
            if (requestFieldDescriptors.fieldDescriptors.isNotEmpty()) {
                snippets.add(requestFields(requestFieldDescriptors.fieldDescriptors))
            }
            if (linkDescriptors.isNotEmpty()) {
                snippets.add(links(linkDescriptors))
            }

            actions.andDo(MockMvcRestDocumentation.document(identifier, *snippets.toTypedArray()))
        }
    }

    @DocumentationDsl
    class RequestResponseDslBuilder(val fieldDescriptors : MutableList<FieldDescriptor> = mutableListOf()){

        infix fun String.field(block: FieldDescriptor.() -> Unit) {
            val field = fieldWithPath(this)
            field.block()
            fieldDescriptors.add(field)
        }

        infix fun String.subsection(block: SubsectionDescriptor.() -> Unit) {
            val subsec = subsectionWithPath(this)
            subsec.block()
            fieldDescriptors.add(subsec)
        }
    }


    @Test
    fun indexExample() {
        this.mockMvc.performGet("/") {
            expect { status { isOk } }

            document("index-example") {
                "notes" link { description("The [Notes](#notes) resource") }
                "tags" link { description("The [Tags](#tags) resource") }
                "profile" link { description("The ALPS profile for the service") }

                response {
                    "_links" subsection { description("Links to other resources") }
                }
            }
        }
    }

    @Test
    fun notesListExample() {
        this.noteRepository.deleteAll()

        createNote("REST maturity model",
                "http://martinfowler.com/articles/richardsonMaturityModel.html")
        createNote("Hypertext Application Language (HAL)",
                "http://stateless.co/hal_specification.html")
        createNote("Application-Level Profile Semantics (ALPS)", "http://alps.io/spec/")

        this.mockMvc.performGet("/notes") {
            expect { status { isOk } }
            document("notes-list-example") {
                response {
                    "_embedded.notes" subsection { description("An array of [Note](#note) resources") }
                    "_links" subsection { description("Links to other resources") }
                }
            }
        }
    }

    @Test
    fun notesCreateExample() {
        val tag = mapOf("name" to "REST")

        val tagLocation = this.mockMvc.performPost("/tags") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(tag))
            }
            expect { status { isCreated } }
        }.response.getHeader("Location")

        val note = mapOf(
                "title" to "REST maturity model",
                "body" to "http://martinfowler.com/articles/richardsonMaturityModel.html",
                "tags" to listOf(tagLocation)
        )


        this.mockMvc.performPost("/notes") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(note))
            }
            expect { status { isCreated } }

            document("notes-create-example") {
                request {
                    "title" field { description("The title of the note") }
                    "body" field { description("The body of the note") }
                    "tags" field { description("An array of [Tag](#tag) resource URIs") }
                }
            }

        }
    }

    @Test
    fun noteGetExample() {
        val tag = mapOf("name" to "REST")

        val tagLocation = mockMvc.performPost("/tags") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(tag))
            }
            expect {
                status { isCreated }
            }
        }.response.getHeader("Location")

        val note = mapOf(
                "title" to "REST maturity model",
                "body" to "http://martinfowler.com/articles/richardsonMaturityModel.html",
                "tags" to listOf(tagLocation)
        )

        val noteLocation = this.mockMvc.performPost("/notes") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(note))
            }
            expect { status { isCreated } }
        }.response.getHeader("Location")

        this.mockMvc.performGet(noteLocation!!) {
            expect {
                status { isOk }
                "title" jsonPathIs note["title"]!!
                "body" jsonPathIs note["body"]!!
                "_links.self.href" jsonPathIs noteLocation
                jsonPath("_links.tags") { value(`is`(notNullValue())) }
            }

            document("note-get-example") {
                "self" link { description("Canonical link for this resource") }
                "note" link { description("This note") }
                "tags" link { description("This note's tags") }

                response {
                    "title" field  { description("The title of the note") }
                    "body" field { description("The body of the note") }
                    "_links" subsection { description("Links to other resources") }
                }

            }
        }
    }


    @Test
    fun tagsListExample() {
        this.noteRepository.deleteAll()
        this.tagRepository.deleteAll()

        createTag("REST")
        createTag("Hypermedia")
        createTag("HTTP")

        this.mockMvc.performGet("/tags") {
            expect { status { isOk } }

            document("tags-list-example") {
                response {
                    "_embedded.tags" subsection { description("An array of [Tag](#tag) resources") }
                    "_links" subsection { description("Links to other resources") }
                }

            }

        }
    }

    @Test
    fun tagsCreateExample() {
        val tag = mapOf("name" to "REST")

        this.mockMvc.performPost("/tags") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(tag))
            }
            expect { status { isCreated } }

            document("tags-create-example") {
                request {
                    "name" field { description("The name of the tag") }
                }
            }

        }
    }

    @Test
    fun noteUpdateExample() {
        val note = mapOf(
                "title" to "REST maturity model",
                "body" to "http://martinfowler.com/articles/richardsonMaturityModel.html"
        )

        val noteLocation = this.mockMvc.performPost("/notes") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(note))
            }
            expect { status { isCreated } }
        }.response.getHeader("Location") ?: throw AssertionError("Location not found")

        this.mockMvc.performGet(noteLocation) {
            expect {
                status { isOk }
                "title" jsonPathIs note["title"]!!
                "body" jsonPathIs note["body"]!!
                "_links.self.href" jsonPathIs noteLocation
                jsonPath("_links.tags") { value(notNullValue()) }
            }
        }


        val tag = mapOf("name" to "REST")


        val tagLocation = this.mockMvc
                .performPost("/tags") {
                    builder {
                        contentType(MediaTypes.HAL_JSON)
                        content(objectMapper.writeValueAsString(tag))
                    }
                    expect { status { isCreated } }
                }.response.getHeader("Location")

        val noteUpdate = mapOf("tags" to listOf(tagLocation!!))

        this.mockMvc.performPatch(noteLocation) {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(noteUpdate))
            }
            expect { status { isNoContent } }
            document("note-update-example") {
                request {
                    "title" field { description("The title of the note").type(JsonFieldType.STRING).optional() }
                    "body" field { description("The body of the note").type(JsonFieldType.STRING).optional() }
                    "tags" field { description("An array of [tag](#tag) resource URIs").optional() }
                }

            }

        }
    }

    @Test
    fun tagGetExample() {
        val tag = mapOf("name" to "REST")

        val tagLocation = this.mockMvc
                .performPost("/tags") {
                    builder {
                        contentType(MediaTypes.HAL_JSON)
                        content(objectMapper.writeValueAsString(tag))
                    }
                    expect { status { isCreated } }
                }.response.getHeader("Location")!!

        this.mockMvc.performGet(tagLocation) {
            expect {
                status { isOk }
                "name" jsonPathIs tag["name"]!!
            }

            document("tag-get-example") {
                "self" link {description("Canonical link for this resource")}
                "tag" link {description("This tag")}
                "notes" link {description("The notes that have this tag")}
                response {
                    "name" field {description("The name of the tag")}
                    "_links" subsection {description("Links to other resources")}
                }

            }

        }

    }

    @Test
    fun tagUpdateExample() {
        val tag = mapOf("name" to "REST")

        val tagLocation = this.mockMvc.performPost("/tags") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(tag))
            }
            expect { status { isCreated } }
        }.response.getHeader("Location")!!

        val tagUpdate = mapOf("name" to "RESTful")

        this.mockMvc.performPatch(tagLocation) {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(tagUpdate))
            }
            expect {
                status { isNoContent }
            }
            document("tag-update-example") {
                request {
                    "name" field {description("The name of the tag")}
                }
            }
        }
    }

    private fun createNote(title: String, body: String) = this.noteRepository.save(Note().also { it.title = title; it.body = body })

    private fun createTag(name: String) = this.tagRepository.save(Tag().also { it.name = name })


}
