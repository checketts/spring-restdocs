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

import org.hamcrest.Matchers.`is`
import org.hamcrest.Matchers.notNullValue
import org.springframework.restdocs.headers.HeaderDocumentation.headerWithName
import org.springframework.restdocs.headers.HeaderDocumentation.responseHeaders
import org.springframework.restdocs.hypermedia.HypermediaDocumentation.linkWithRel
import org.springframework.restdocs.hypermedia.HypermediaDocumentation.links
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.patch
import org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.post
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessRequest
import org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse
import org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint
import org.springframework.restdocs.payload.PayloadDocumentation.fieldWithPath
import org.springframework.restdocs.payload.PayloadDocumentation.requestFields
import org.springframework.restdocs.payload.PayloadDocumentation.responseFields
import org.springframework.restdocs.payload.PayloadDocumentation.subsectionWithPath
import org.springframework.restdocs.snippet.Attributes.key
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import java.util.Arrays
import java.util.HashMap

import javax.servlet.RequestDispatcher

import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.hateoas.MediaTypes
import org.springframework.restdocs.JUnitRestDocumentation
import org.springframework.restdocs.constraints.ConstraintDescriptions
import org.springframework.restdocs.mockmvc.RestDocumentationResultHandler
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.JsonFieldType
import org.springframework.test.context.junit4.SpringRunner
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.util.StringUtils
import org.springframework.web.context.WebApplicationContext

import com.fasterxml.jackson.databind.ObjectMapper
import cz.petrbalat.spring.mvc.test.dsl.DslRequestBuilder
import cz.petrbalat.spring.mvc.test.dsl.performGet
import cz.petrbalat.spring.mvc.test.dsl.performPatch
import cz.petrbalat.spring.mvc.test.dsl.performPost
import org.springframework.restdocs.headers.HeaderDescriptor
import org.springframework.restdocs.hypermedia.LinkDescriptor
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.payload.SubsectionDescriptor
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import kotlin.math.exp

@RunWith(SpringRunner::class)
@SpringBootTest
class ApiDocumentation {

    @get:Rule
    val restDocumentation = JUnitRestDocumentation()

    private lateinit var documentationHandler: RestDocumentationResultHandler

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
        this.documentationHandler = document("{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()))

        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context)
                .apply<DefaultMockMvcBuilder>(documentationConfiguration(this.restDocumentation))
                .alwaysDo<DefaultMockMvcBuilder>(this.documentationHandler)
                .build()
    }

    fun DslRequestBuilder.document(identifier: String, vararg snippets: Snippet, block: DocumentationDslBuilder.() -> Unit = {}) {
        actions {
            val allSnippets = DocumentationDslBuilder(snippets = snippets.toMutableList()).apply(block).buildSnippets()
            andDo(MockMvcRestDocumentation.document(identifier, *allSnippets))
        }
    }

    fun DslRequestBuilder.document(handler: RestDocumentationResultHandler, vararg snippets: Snippet, block: DocumentationDslBuilder.() -> Unit = {}) {
        actions {
            val allSnippets = DocumentationDslBuilder(snippets = snippets.toMutableList()).apply(block).buildSnippets()
            this.andDo(handler.document(*allSnippets))
        }
    }

    @DslMarker
    annotation class DocumentationDsl

    @DocumentationDsl
    class DocumentationDslBuilder(val snippets: MutableList<Snippet> = mutableListOf()) {

        private val response = RequestResponseDslBuilder()
        private val request = RequestResponseDslBuilder()
        private val linkDescriptors = mutableListOf<LinkDescriptor>()

        fun request(block: RequestResponseDslBuilder.() -> Unit) {
            request.apply(block)
        }

        fun response(block: RequestResponseDslBuilder.() -> Unit) {
            response.apply(block)
        }

        infix fun String.link(block: LinkDescriptor.() -> Unit) {
            val link = linkWithRel(this)
            link.block()
            linkDescriptors.add(link)
        }

        fun buildSnippets(): Array<Snippet> {
            if (response.fieldDescriptors.isNotEmpty()) {
                snippets.add(responseFields(response.fieldDescriptors))
            }
            if (response.headerDescriptors.isNotEmpty()) {
                snippets.add(responseHeaders(response.headerDescriptors))
            }

            if (request.fieldDescriptors.isNotEmpty()) {
                snippets.add(requestFields(request.fieldDescriptors))
            }
            if (request.headerDescriptors.isNotEmpty()) {
                snippets.add(responseHeaders(request.headerDescriptors))
            }
            if (linkDescriptors.isNotEmpty()) {
                snippets.add(links(linkDescriptors))
            }

            return snippets.toTypedArray()
        }
    }

    @DocumentationDsl
    class RequestResponseDslBuilder(
            val fieldDescriptors: MutableList<FieldDescriptor> = mutableListOf(),
            val headerDescriptors: MutableList<HeaderDescriptor> = mutableListOf()
    ) {

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

        infix fun String.header(block: HeaderDescriptor.() -> Unit) {
            val header = headerWithName(this)
            header.block()
            headerDescriptors.add(header)
        }
    }

    @Test
    fun headersExample() {
        this.mockMvc.performGet("/") {
            expect { status { isOk } }
            document(documentationHandler) {
                response {
                    "Content-Type" header { description("The Content-Type of the payload, e.g. `application/hal+json`") }
                }
            }
        }
    }

    @Test
    fun errorExample() {
        this.mockMvc.performGet("/error") {
            builder {
                requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/notes")
                requestAttr(RequestDispatcher.ERROR_MESSAGE, "The tag 'http://localhost:8080/tags/123' does not exist")
            }

            expect {
                status { isBadRequest }
                "error" jsonPathIs "Bad Request"
                jsonPath("timestamp") { value(`is`(notNullValue())) }
                "status" jsonPathIs 400
                jsonPath("path") { value(`is`(notNullValue())) }
            }

            document(documentationHandler) {
                response {
                    "error" field { description("The HTTP error that occurred, e.g. `Bad Request`") }
                    "message" field { description("A description of the cause of the error") }
                    "path" field { description("The path to which the request was made") }
                    "status" field { description("The HTTP status code, e.g. `400`") }
                    "timestamp" field { description("The time, in milliseconds, at which the error occurred") }
                }

            }
        }
    }

    @Test
    fun indexExample() {
        this.mockMvc.performGet("/") {
            expect { status { isOk } }
            document(documentationHandler) {
                "notes" link { description("The <<resources-notes,Notes resource>>") }
                "tags" link { description("The <<resources-tags,Tags resource>>") }
                response {
                    "_links" subsection { description("<<resources-index-links,Links>> to other resources") }
                }
            }
        }

    }

    @Test
    fun notesListExample() {
        this.noteRepository.deleteAll()

        createNote("REST maturity model", "http://martinfowler.com/articles/richardsonMaturityModel.html")
        createNote("Hypertext Application Language (HAL)", "http://stateless.co/hal_specification.html")
        createNote("Application-Level Profile Semantics (ALPS)", "http://alps.io/spec/")

        this.mockMvc.performGet("/notes") {
            expect { status { isOk } }
            document(documentationHandler) {
                response {
                    "_embedded.notes" subsection { description("An array of <<resources-note, Note resources>>") }
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


        val note = mapOf("title" to "REST maturity model",
                "body" to "http://martinfowler.com/articles/richardsonMaturityModel.html",
                "tags" to Arrays.asList(tagLocation))

        this.mockMvc.performPost("/notes") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(note))
            }
            expect { status { isCreated } }
            document(documentationHandler) {
                request {
                    "title" field { description("The title of the note").withContraints<NoteInput>() }
                    "body" field { description("The body of the note").withContraints<NoteInput>() }
                    "tags" field { description("An array of tag resource URIs").withContraints<NoteInput>() }
                }
            }
        }
    }

    @Test
    fun noteGetExample() {
        val tag = mapOf("name" to "REST")

        val tagLocation = this.mockMvc.performPost("/tags") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(tag))
            }
            expect { status { isCreated } }
        }.response.getHeader("Location")

        val note = mapOf("title" to "REST maturity model",
                "body" to "http://martinfowler.com/articles/richardsonMaturityModel.html",
                "tags" to Arrays.asList(tagLocation))

        val noteLocation = this.mockMvc.performPost("/notes") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(note))
            }
            expect { status { isCreated } }
        }.response.getHeader("Location")!!

        this.mockMvc.performGet(noteLocation) {
            expect {
                status { isOk }
                "title" jsonPathIs note["title"]!!
                "body" jsonPathIs note["body"]!!
                "_links.self.href" jsonPathIs noteLocation
                jsonPath("_links.note-tags") { value(`is`(notNullValue())) }
            }

            document(documentationHandler) {
                "self" link { description("This <<resources-note,note>>") }
                "note-tags" link { description("This note's <<resources-note-tags,tags>>") }
                response {
                    "title" field { description("The title of the note") }
                    "body" field { description("The body of the note") }
                    "_links" subsection { description("<<resources-note-links,Links>> to other resources") }
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
            document(documentationHandler) {
                response {
                    "_embedded.tags" subsection { description("An array of <<resources-tag,Tag resources>>") }
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
            document(documentationHandler) {
                request {
                    "name" field { description("The name of the tag").withContraints<TagInput>() }
                }
            }
        }
    }

    @Test
    fun noteUpdateExample() {
        val note = mapOf("title" to "REST maturity model",
                "body" to "http://martinfowler.com/articles/richardsonMaturityModel.html"
        )


        val noteLocation = this.mockMvc.performPost("/notes") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(note))
            }
            expect { status { isCreated } }
        }.response.getHeader("Location")!!

        this.mockMvc.performGet(noteLocation) {
            expect {
                status { isOk }
                "title" jsonPathIs note["title"]!!
                "body" jsonPathIs note["body"]!!
                "_links.self.href" jsonPathIs noteLocation
                jsonPath("_links.note-tags") { value(`is`(notNullValue())) }
            }
        }


        val tag = mapOf("name" to "REST")

        val tagLocation = this.mockMvc.performPost("/tags") {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(tag))
            }
            expect { status { isCreated } }
        }.response.getHeader("Location")

        val noteUpdate = mapOf("tags" to listOf(tagLocation))

        this.mockMvc.performPatch(noteLocation) {
            builder {
                contentType(MediaTypes.HAL_JSON)
                content(objectMapper.writeValueAsString(noteUpdate))
            }
            expect { status { isNoContent } }
            document(documentationHandler) {
                request {
                    "title" field {
                        description("The title of the note")
                        type(JsonFieldType.STRING)
                        optional()
                        withContraints<NotePatchInput>()
                    }
                    "body" field {
                        description("The body of the note")
                        type(JsonFieldType.STRING)
                        optional()
                        withContraints<NotePatchInput>()
                    }
                    "tags" field { description("An array of tag resource URIs").withContraints<NotePatchInput>() }
                }
            }
        }
    }

    @Test
    fun tagGetExample() {
        val tag = mapOf("name" to "REST")

        val tagLocation = this.mockMvc.performPost("/tags") {
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

            document(documentationHandler) {
                "self" link {description("This <<resources-tag,tag>>")}
                "tagged-notes" link {description("The <<resources-tagged-notes,notes>> that have this tag")}
                response {
                    "name" field {description("The name of the tag")}
                    "_links" subsection {description("<<resources-tag-links,Links>> to other resources")}
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
            expect { status { isNoContent } }
            document(documentationHandler) {
                request { "name" field {description("The name of the tag").withContraints<TagPatchInput>() }}
            }
        }
    }

    private fun createNote(title: String, body: String) {
        val note = Note()
        note.title = title
        note.body = body

        this.noteRepository.save(note)
    }

    private fun createTag(name: String) {
        val tag = Tag()
        tag.name = name
        this.tagRepository.save(tag)
    }

    private inline fun <reified T> FieldDescriptor.withContraints() {
        this.attributes(key("constraints").value(StringUtils
                .collectionToDelimitedString(ConstraintDescriptions(T::class.java)
                        .descriptionsForProperty(path), ". ")))
    }
}
