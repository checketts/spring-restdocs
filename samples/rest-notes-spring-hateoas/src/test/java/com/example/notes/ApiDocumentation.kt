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
import org.springframework.restdocs.hypermedia.LinkDescriptor
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.payload.SubsectionDescriptor
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder

@RunWith(SpringRunner::class)
@SpringBootTest
class ApiDocumentation {

    @get:Rule
    val restDocumentation = JUnitRestDocumentation()

    private lateinit var documentationHandler: RestDocumentationResultHandler

    @Autowired
    private val noteRepository: NoteRepository? = null

    @Autowired
    private val tagRepository: TagRepository? = null

    @Autowired
    private val objectMapper: ObjectMapper? = null

    @Autowired
    private val context: WebApplicationContext? = null

    private var mockMvc: MockMvc? = null

    @Before
    fun setUp() {
        this.documentationHandler = document("{method-name}",
                preprocessRequest(prettyPrint()),
                preprocessResponse(prettyPrint()))

        this.mockMvc = MockMvcBuilders.webAppContextSetup(this.context!!)
                .apply<DefaultMockMvcBuilder>(documentationConfiguration(this.restDocumentation))
                .alwaysDo<DefaultMockMvcBuilder>(this.documentationHandler)
                .build()
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
    @Throws(Exception::class)
    fun headersExample() {
        this.mockMvc!!
                .perform(get("/"))
                .andExpect(status().isOk)
                .andDo(this.documentationHandler!!.document(
                        responseHeaders(
                                headerWithName("Content-Type").description("The Content-Type of the payload, e.g. `application/hal+json`"))))
    }

    @Test
    @Throws(Exception::class)
    fun errorExample() {
        this.mockMvc!!
                .perform(get("/error")
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/notes")
                        .requestAttr(RequestDispatcher.ERROR_MESSAGE, "The tag 'http://localhost:8080/tags/123' does not exist"))
                .andExpect(status().isBadRequest)
                .andExpect(jsonPath("error", `is`("Bad Request")))
                .andExpect(jsonPath("timestamp", `is`(notNullValue())))
                .andExpect(jsonPath("status", `is`(400)))
                .andExpect(jsonPath("path", `is`(notNullValue())))
                .andDo(this.documentationHandler!!.document(
                        responseFields(
                                fieldWithPath("error").description("The HTTP error that occurred, e.g. `Bad Request`"),
                                fieldWithPath("message").description("A description of the cause of the error"),
                                fieldWithPath("path").description("The path to which the request was made"),
                                fieldWithPath("status").description("The HTTP status code, e.g. `400`"),
                                fieldWithPath("timestamp").description("The time, in milliseconds, at which the error occurred"))))
    }

    @Test
    @Throws(Exception::class)
    fun indexExample() {
        this.mockMvc!!.perform(get("/"))
                .andExpect(status().isOk)
                .andDo(this.documentationHandler!!.document(
                        links(
                                linkWithRel("notes").description("The <<resources-notes,Notes resource>>"),
                                linkWithRel("tags").description("The <<resources-tags,Tags resource>>")),
                        responseFields(
                                subsectionWithPath("_links").description("<<resources-index-links,Links>> to other resources"))))
    }

    @Test
    @Throws(Exception::class)
    fun notesListExample() {
        this.noteRepository!!.deleteAll()

        createNote("REST maturity model", "http://martinfowler.com/articles/richardsonMaturityModel.html")
        createNote("Hypertext Application Language (HAL)", "http://stateless.co/hal_specification.html")
        createNote("Application-Level Profile Semantics (ALPS)", "http://alps.io/spec/")

        this.mockMvc!!
                .perform(get("/notes"))
                .andExpect(status().isOk)
                .andDo(this.documentationHandler!!.document(
                        responseFields(
                                subsectionWithPath("_embedded.notes").description("An array of <<resources-note, Note resources>>"))))
    }

    @Test
    @Throws(Exception::class)
    fun notesCreateExample() {
        val tag = HashMap<String, String>()
        tag["name"] = "REST"

        val tagLocation = this.mockMvc!!
                .perform(post("/tags")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper!!.writeValueAsString(tag)))
                .andExpect(status().isCreated)
                .andReturn().response.getHeader("Location")

        val note = HashMap<String, Any>()
        note["title"] = "REST maturity model"
        note["body"] = "http://martinfowler.com/articles/richardsonMaturityModel.html"
        note["tags"] = Arrays.asList(tagLocation!!)

        val fields = ConstrainedFields(NoteInput::class.java)

        this.mockMvc!!
                .perform(post("/notes")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper.writeValueAsString(note)))
                .andExpect(
                        status().isCreated)
                .andDo(this.documentationHandler!!.document(
                        requestFields(
                                fields.withPath("title").description("The title of the note"),
                                fields.withPath("body").description("The body of the note"),
                                fields.withPath("tags").description("An array of tag resource URIs"))))
    }

    @Test
    @Throws(Exception::class)
    fun noteGetExample() {
        val tag = HashMap<String, String>()
        tag["name"] = "REST"

        val tagLocation = this.mockMvc!!
                .perform(post("/tags")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper!!.writeValueAsString(tag)))
                .andExpect(status().isCreated)
                .andReturn().response.getHeader("Location")

        val note = HashMap<String, Any>()
        note["title"] = "REST maturity model"
        note["body"] = "http://martinfowler.com/articles/richardsonMaturityModel.html"
        note["tags"] = Arrays.asList(tagLocation!!)

        val noteLocation = this.mockMvc!!
                .perform(post("/notes")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper.writeValueAsString(note)))
                .andExpect(status().isCreated)
                .andReturn().response.getHeader("Location")

        this.mockMvc!!
                .perform(get(noteLocation))
                .andExpect(status().isOk)
                .andExpect(jsonPath("title", `is`<Any>(note["title"])))
                .andExpect(jsonPath("body", `is`<Any>(note["body"])))
                .andExpect(jsonPath("_links.self.href", `is`<String>(noteLocation)))
                .andExpect(jsonPath("_links.note-tags", `is`(notNullValue())))
                .andDo(this.documentationHandler!!.document(
                        links(
                                linkWithRel("self").description("This <<resources-note,note>>"),
                                linkWithRel("note-tags").description("This note's <<resources-note-tags,tags>>")),
                        responseFields(
                                fieldWithPath("title").description("The title of the note"),
                                fieldWithPath("body").description("The body of the note"),
                                subsectionWithPath("_links").description("<<resources-note-links,Links>> to other resources"))))

    }

    @Test
    @Throws(Exception::class)
    fun tagsListExample() {
        this.noteRepository!!.deleteAll()
        this.tagRepository!!.deleteAll()

        createTag("REST")
        createTag("Hypermedia")
        createTag("HTTP")

        this.mockMvc!!
                .perform(get("/tags"))
                .andExpect(status().isOk)
                .andDo(this.documentationHandler!!.document(
                        responseFields(
                                subsectionWithPath("_embedded.tags").description("An array of <<resources-tag,Tag resources>>"))))
    }

    @Test
    @Throws(Exception::class)
    fun tagsCreateExample() {
        val tag = HashMap<String, String>()
        tag["name"] = "REST"

        val fields = ConstrainedFields(TagInput::class.java)

        this.mockMvc!!
                .perform(post("/tags")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper!!.writeValueAsString(tag)))
                .andExpect(status().isCreated)
                .andDo(this.documentationHandler!!.document(
                        requestFields(
                                fields.withPath("name").description("The name of the tag"))))
    }

    @Test
    @Throws(Exception::class)
    fun noteUpdateExample() {
        val note = HashMap<String, Any>()
        note["title"] = "REST maturity model"
        note["body"] = "http://martinfowler.com/articles/richardsonMaturityModel.html"

        val noteLocation = this.mockMvc!!
                .perform(post("/notes")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper!!.writeValueAsString(note)))
                .andExpect(status().isCreated)
                .andReturn().response.getHeader("Location")

        this.mockMvc!!
                .perform(get(noteLocation))
                .andExpect(status().isOk)
                .andExpect(jsonPath("title", `is`<Any>(note["title"])))
                .andExpect(jsonPath("body", `is`<Any>(note["body"])))
                .andExpect(jsonPath("_links.self.href", `is`<String>(noteLocation)))
                .andExpect(jsonPath("_links.note-tags", `is`(notNullValue())))

        val tag = HashMap<String, String>()
        tag["name"] = "REST"

        val tagLocation = this.mockMvc!!
                .perform(post("/tags")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper.writeValueAsString(tag)))
                .andExpect(status().isCreated)
                .andReturn().response.getHeader("Location")

        val noteUpdate = HashMap<String, Any>()
        noteUpdate["tags"] = Arrays.asList(tagLocation!!)

        val fields = ConstrainedFields(NotePatchInput::class.java)

        this.mockMvc!!
                .perform(patch(noteLocation)
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper.writeValueAsString(noteUpdate)))
                .andExpect(status().isNoContent)
                .andDo(this.documentationHandler!!.document(
                        requestFields(
                                fields.withPath("title")
                                        .description("The title of the note")
                                        .type(JsonFieldType.STRING)
                                        .optional(),
                                fields.withPath("body")
                                        .description("The body of the note")
                                        .type(JsonFieldType.STRING)
                                        .optional(),
                                fields.withPath("tags")
                                        .description("An array of tag resource URIs"))))
    }

    @Test
    @Throws(Exception::class)
    fun tagGetExample() {
        val tag = HashMap<String, String>()
        tag["name"] = "REST"

        val tagLocation = this.mockMvc!!
                .perform(post("/tags")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper!!.writeValueAsString(tag)))
                .andExpect(status().isCreated)
                .andReturn().response.getHeader("Location")

        this.mockMvc!!
                .perform(get(tagLocation))
                .andExpect(status().isOk)
                .andExpect(jsonPath("name", `is`<String>(tag["name"])))
                .andDo(this.documentationHandler!!.document(
                        links(
                                linkWithRel("self").description("This <<resources-tag,tag>>"),
                                linkWithRel("tagged-notes").description("The <<resources-tagged-notes,notes>> that have this tag")),
                        responseFields(
                                fieldWithPath("name").description("The name of the tag"),
                                subsectionWithPath("_links").description("<<resources-tag-links,Links>> to other resources"))))
    }

    @Test
    @Throws(Exception::class)
    fun tagUpdateExample() {
        val tag = HashMap<String, String>()
        tag["name"] = "REST"

        val tagLocation = this.mockMvc!!
                .perform(post("/tags")
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper!!.writeValueAsString(tag)))
                .andExpect(status().isCreated)
                .andReturn().response.getHeader("Location")

        val tagUpdate = HashMap<String, Any>()
        tagUpdate["name"] = "RESTful"

        val fields = ConstrainedFields(TagPatchInput::class.java)

        this.mockMvc!!
                .perform(patch(tagLocation)
                        .contentType(MediaTypes.HAL_JSON)
                        .content(this.objectMapper.writeValueAsString(tagUpdate)))
                .andExpect(status().isNoContent)
                .andDo(this.documentationHandler!!.document(
                        requestFields(
                                fields.withPath("name").description("The name of the tag"))))
    }

    private fun createNote(title: String, body: String) {
        val note = Note()
        note.title = title
        note.body = body

        this.noteRepository!!.save(note)
    }

    private fun createTag(name: String) {
        val tag = Tag()
        tag.name = name
        this.tagRepository!!.save(tag)
    }

    private class ConstrainedFields internal constructor(input: Class<*>) {

        private val constraintDescriptions: ConstraintDescriptions

        init {
            this.constraintDescriptions = ConstraintDescriptions(input)
        }

        fun withPath(path: String): FieldDescriptor {
            return fieldWithPath(path).attributes(key("constraints").value(StringUtils
                    .collectionToDelimitedString(this.constraintDescriptions
                            .descriptionsForProperty(path), ". ")))
        }
    }

}
