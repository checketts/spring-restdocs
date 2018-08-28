/*
 * Copyright 2014-2018 the original author or authors.
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

package com.example.junit5

import cz.petrbalat.spring.mvc.test.dsl.DslRequestBuilder
import cz.petrbalat.spring.mvc.test.dsl.performGet
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.documentationConfiguration
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.restdocs.RestDocumentationContextProvider
import org.springframework.restdocs.RestDocumentationExtension
import org.springframework.restdocs.hypermedia.HypermediaDocumentation
import org.springframework.restdocs.hypermedia.LinkDescriptor
import org.springframework.restdocs.mockmvc.MockMvcRestDocumentation
import org.springframework.restdocs.payload.FieldDescriptor
import org.springframework.restdocs.payload.PayloadDocumentation
import org.springframework.restdocs.payload.SubsectionDescriptor
import org.springframework.restdocs.snippet.Snippet
import org.springframework.test.context.junit.jupiter.SpringExtension
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.ResultActions
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import org.springframework.web.context.WebApplicationContext

@SpringBootTest
@ExtendWith(RestDocumentationExtension::class, SpringExtension::class)
class SampleJUnit5ApplicationTests {

    @Autowired
    private val context: WebApplicationContext? = null

    private lateinit var mockMvc: MockMvc

    @BeforeEach
    fun setUp(restDocumentation: RestDocumentationContextProvider) {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(context!!)
                .apply<DefaultMockMvcBuilder>(documentationConfiguration(restDocumentation)).build()
    }

    @Test
    fun sample() {
        this.mockMvc.performGet("/") {
            expect { status { isOk } }
            document("sample")
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
            val link = HypermediaDocumentation.linkWithRel(this)
            link.block()
            linkDescriptors.add(link)
        }

        fun andDocument(actions: ResultActions, identifier: String) {
            if (responseFieldDescriptors.fieldDescriptors.isNotEmpty()) {
                snippets.add(PayloadDocumentation.responseFields(responseFieldDescriptors.fieldDescriptors))
            }
            if (requestFieldDescriptors.fieldDescriptors.isNotEmpty()) {
                snippets.add(PayloadDocumentation.requestFields(requestFieldDescriptors.fieldDescriptors))
            }
            if (linkDescriptors.isNotEmpty()) {
                snippets.add(HypermediaDocumentation.links(linkDescriptors))
            }

            actions.andDo(MockMvcRestDocumentation.document(identifier, *snippets.toTypedArray()))
        }
    }

    @DocumentationDsl
    class RequestResponseDslBuilder(val fieldDescriptors : MutableList<FieldDescriptor> = mutableListOf()){

        infix fun String.field(block: FieldDescriptor.() -> Unit) {
            val field = PayloadDocumentation.fieldWithPath(this)
            field.block()
            fieldDescriptors.add(field)
        }

        infix fun String.subsection(block: SubsectionDescriptor.() -> Unit) {
            val subsec = PayloadDocumentation.subsectionWithPath(this)
            subsec.block()
            fieldDescriptors.add(subsec)
        }
    }


}
