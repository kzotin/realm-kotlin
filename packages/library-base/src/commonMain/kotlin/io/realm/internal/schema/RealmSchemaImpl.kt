/*
 * Copyright 2021 Realm Inc.
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
package io.realm.internal.schema

import io.realm.internal.RealmReference
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.ClassKey
import io.realm.internal.interop.Table
import io.realm.internal.interop.Property
import io.realm.schema.RealmClass
import io.realm.schema.RealmSchema

data class RealmSchemaImpl(override val classes: Set<RealmClass>) : RealmSchema {

    companion object {
        fun fromRealm(realmReference: RealmReference): RealmSchemaImpl {
            val dbPointer = realmReference.dbPointer
            val realmGetNumClasses = RealmInterop.realm_get_num_classes(dbPointer)
            val classKeys = RealmInterop.realm_get_class_keys(dbPointer)
            println("SchemaKeys: $classKeys")
            val classes = classKeys.map {
                val coreClazz: Table = RealmInterop.realm_get_class(dbPointer, it)
                val coreProperties =
                    RealmInterop.realm_get_class_properties(dbPointer, it, coreClazz.numProperties)
                val realmProperties = coreProperties.map {
                    RealmPropertyImpl.fromCoreProperty(it)
                }
                with(coreClazz) {
                    RealmClassImpl(name, null, false, realmProperties.toSet())
                }
            }

            println("classes: $classes")
            return RealmSchemaImpl(classes.toSet())
        }
    }
}
