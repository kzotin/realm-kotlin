/*
 * Copyright 2022 Realm Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

@file:Suppress("invisible_member", "invisible_reference")

package io.realm.kotlin.test.shared

import io.realm.kotlin.Realm
import io.realm.kotlin.RealmConfiguration
import io.realm.kotlin.entities.backlink.Child
import io.realm.kotlin.entities.backlink.MissingSourceProperty
import io.realm.kotlin.entities.backlink.Parent
import io.realm.kotlin.entities.backlink.Recursive
import io.realm.kotlin.ext.query
import io.realm.kotlin.ext.realmListOf
import io.realm.kotlin.ext.realmSetOf
import io.realm.kotlin.internal.asDynamicRealm
import io.realm.kotlin.test.platform.PlatformUtils
import kotlinx.coroutines.runBlocking
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinkingObjectsTests {
    private lateinit var realm: Realm
    private lateinit var tmpDir: String

    private fun Child.assertParents(expectedSize: Int) {
        assertEquals(expectedSize, parents.size)
        assertEquals(expectedSize, parentsByList.size)
        assertEquals(expectedSize, parentsBySet.size)
    }

    @BeforeTest
    fun setup() {
        tmpDir = PlatformUtils.createTempDir()
        val configuration =
            RealmConfiguration.Builder(setOf(Parent::class, Child::class, Recursive::class))
                .directory(tmpDir)
                .build()

        realm = Realm.open(configuration)
    }

    @Test
    fun unmanaged_throws() {
        val child = Child()
        val parent = Parent()

        parent.child = child
        parent.childSet = realmSetOf(child)
        parent.childList = realmListOf(child)

        assertFailsWith<IllegalStateException> {
            child.parents
        }

        assertFailsWith<IllegalStateException> {
            child.parentsBySet
        }

        assertFailsWith<IllegalStateException> {
            child.parentsByList
        }
    }

    @Test
    fun managed_multipleChildren() {
        runBlocking {
            realm.write {
                val child = this.copyToRealm(Child())

                val parents = Array(5) {
                    this.copyToRealm(Parent(it))
                }

                child.assertParents(0)

                parents.forEach { parent ->
                    parent.child = child
                    parent.childList.add(child)
                    parent.childSet.add(child)
                }

                child.assertParents(parents.size)
            }
        }
    }

    @Test
    fun managed_duplicateChildren() {
        runBlocking {
            realm.write {
                val child = this.copyToRealm(Child())

                val parents = Array(5) {
                    this.copyToRealm(Parent(it))
                }

                child.assertParents(0)

                parents.forEach { parent ->
                    parent.child = child
                    parent.childList.add(child)
                    parent.childList.add(child)
                    parent.childSet.add(child)
                    parent.childSet.add(child)
                }

                assertEquals(5, child.parents.size)
                assertEquals(10, child.parentsByList.size)
                assertEquals(5, child.parentsBySet.size)
            }
        }
    }

    @Test
    fun recursive() {
        runBlocking {
            val recursive = realm.write {
                val recursive = this.copyToRealm(Recursive())
                recursive.recursiveField = recursive
                recursive
            }
            assertEquals(1, recursive.references.size)
            assertEquals(recursive.name, recursive.references[0].name)
        }
    }

    // Dynamic tests
    @Test
    fun dynamic() {
        runBlocking {
            realm.write {
                val child = this.copyToRealm(Child())

                this.copyToRealm(
                    Parent().apply
                    {
                        this.child = child
                        this.childSet.add(child)
                        this.childList.add(child)
                    }
                )
            }

            realm.asDynamicRealm().query(Child::class.simpleName!!).first().find()!!
                .let { dynamicObject ->
                    assertEquals(1, dynamicObject.getLinkingObjects(Child::parents.name).size)
                    assertEquals(1, dynamicObject.getLinkingObjects(Child::parentsByList.name).size)
                    assertEquals(1, dynamicObject.getLinkingObjects(Child::parentsBySet.name).size)
                }
        }
    }

    @Test
    fun dynamicMissingProperty_throws() {
        runBlocking {
            realm.write {
                this.copyToRealm(Recursive())
            }
            realm.asDynamicRealm().let { dynamicRealm ->
                val child = dynamicRealm.query("Recursive").first().find()!!
                assertFailsWith<IllegalArgumentException> {
                    child.getLinkingObjects("missing")
                }
            }
        }
    }

    @Test
    fun dynamicWrongProperty_throws() {
        runBlocking {
            realm.write {
                this.copyToRealm(Recursive())
            }
            realm.asDynamicRealm().let { dynamicRealm ->
                val child = dynamicRealm.query("Recursive").first().find()!!
                assertFailsWith<IllegalArgumentException> {
                    child.getLinkingObjects("name")
                }
            }
        }
    }

    // Missing stuff
    @Test
    fun classNotInSchema() {
        runBlocking {
            tmpDir = PlatformUtils.createTempDir()
            val configuration =
                RealmConfiguration.Builder(setOf(Child::class))
                    .directory(tmpDir)
                    .build()

            assertFailsWith<IllegalStateException> {
                Realm.open(configuration)
            }
        }
    }

    @Test
    fun fieldNotInClass() {
        runBlocking {
            tmpDir = PlatformUtils.createTempDir()
            val configuration =
                RealmConfiguration.Builder(setOf(MissingSourceProperty::class))
                    .directory(tmpDir)
                    .build()

            assertFailsWith<IllegalStateException> {
                Realm.open(configuration)
            }
        }
    }

    @Test
    fun query() {
        realm.writeBlocking {
            val child = this.copyToRealm(Child())

            val parents = Array(5) {
                this.copyToRealm(Parent(it))
            }

            child.assertParents(0)

            parents.forEach { parent ->
                parent.child = child
                parent.childList.add(child)
                parent.childSet.add(child)
            }

            child.assertParents(parents.size)
        }

        realm.query<Child>().first().find()!!.let { child ->
            assertEquals(2, child.parents.query("id > 2").find().size)
            assertEquals(2, child.parentsByList.query("id > 2").find().size)
            assertEquals(2, child.parentsBySet.query("id > 2").find().size)
        }
    }
}