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

package io.realm.kotlin.internal

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmListPointer
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.internal.interop.scoped
import io.realm.kotlin.internal.interop.unscoped
import io.realm.kotlin.notifications.ListChange
import io.realm.kotlin.notifications.internal.DeletedListImpl
import io.realm.kotlin.notifications.internal.InitialListImpl
import io.realm.kotlin.notifications.internal.UpdatedListImpl
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmList
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged lists, backed by a [MutableList].
 */
internal class UnmanagedRealmList<E> : RealmList<E>, InternalDeleteable, MutableList<E> by mutableListOf() {
    override fun asFlow(): Flow<ListChange<E>> =
        throw UnsupportedOperationException("Unmanaged lists cannot be observed.")

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged lists cannot be deleted.")
    }
}

/**
 * Implementation for managed lists, backed by Realm.
 */
internal class ManagedRealmList<E>(
    internal val nativePointer: RealmListPointer,
    val operator: ListOperator<E>,
) : AbstractMutableList<E>(), RealmList<E>, InternalDeleteable, Observable<ManagedRealmList<E>, ListChange<E>>, Flowable<ListChange<E>> {

    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_list_size(nativePointer).toInt()
        }

    override fun get(index: Int): E {
        operator.realmReference.checkClosed()
        try {
            return operator.get(index)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Could not get element at list index $index",
            )
        }
    }

    override fun add(index: Int, element: E) {
        try {
            operator.insert(index, element)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Could not add element at list index $index",
            )
        }
    }

    // We need explicit overrides of these to ensure that we capture duplicate references to the
    // same unmanaged object in our internal import caching mechanism
    override fun addAll(elements: Collection<E>): Boolean = operator.insertAll(size, elements)

    // We need explicit overrides of these to ensure that we capture duplicate references to the
    // same unmanaged object in our internal import caching mechanism
    override fun addAll(index: Int, elements: Collection<E>): Boolean {
        checkPositionIndex(index, size)
        return operator.insertAll(index, elements)
    }

    override fun clear() {
        operator.realmReference.checkClosed()
        RealmInterop.realm_list_clear(nativePointer)
    }

    override fun removeAt(index: Int): E = get(index).also {
        operator.realmReference.checkClosed()
        try {
            RealmInterop.realm_list_erase(nativePointer, index.toLong())
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Could not remove element at list index $index",
            )
        }
    }

    override fun set(index: Int, element: E): E {
        operator.realmReference.checkClosed()
        try {
            return operator.set(index, element)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Could not set list element at list index $index",
            )
        }
    }

    override fun asFlow(): Flow<ListChange<E>> {
        operator.realmReference.checkClosed()
        return operator.realmReference.owner.registerObserver(this)
    }

    override fun freeze(frozenRealm: RealmReference): ManagedRealmList<E>? =
        RealmInterop.realm_list_resolve_in(nativePointer, frozenRealm.dbPointer)?.let {
            ManagedRealmList(it, operator.copy(frozenRealm, it))
        }

    override fun thaw(liveRealm: RealmReference): ManagedRealmList<E>? =
        RealmInterop.realm_list_resolve_in(nativePointer, liveRealm.dbPointer)?.let {
            ManagedRealmList(it, operator.copy(liveRealm, it))
        }

    override fun registerForNotification(
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer =
        RealmInterop.realm_list_add_notification_callback(nativePointer, callback)

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: RealmChangesPointer,
        channel: SendChannel<ListChange<E>>
    ): ChannelResult<Unit>? {
        val frozenList: ManagedRealmList<E>? = freeze(frozenRealm)
        return if (frozenList != null) {
            val builder = ListChangeSetBuilderImpl(change)

            if (builder.isEmpty()) {
                channel.trySend(InitialListImpl(frozenList))
            } else {
                channel.trySend(UpdatedListImpl(frozenList, builder.build()))
            }
        } else {
            channel.trySend(DeletedListImpl(UnmanagedRealmList()))
                .also {
                    channel.close()
                }
        }
    }

    // TODO from LifeCycle interface
    internal fun isValid(): Boolean = RealmInterop.realm_list_is_valid(nativePointer)

    override fun delete() = RealmInterop.realm_list_remove_all(nativePointer)
}

// Cloned from https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/src/kotlin/collections/AbstractList.kt
private fun checkPositionIndex(index: Int, size: Int) {
    if (index < 0 || index > size) {
        throw IndexOutOfBoundsException("index: $index, size: $size")
    }
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer.
 */
internal interface ListOperator<E> : CollectionOperator<E> {

    fun get(index: Int): E

    // TODO OPTIMIZE We technically don't need update policy and cache for primitie lists but right now RealmObjectHelper.assign doesn't know how to differentiate the calls to the operator
    fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    )

    fun insertAll(
        index: Int,
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var _index = index
        var changed = false
        for (e in elements) {
            insert(_index++, e, updatePolicy, cache)
            changed = true
        }
        return changed
    }

    fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): E

    // Creates a new operator from an existing one to be able to issue frozen/thawed instances of the list operating on the new version of the list
    fun copy(realmReference: RealmReference, nativePointer: RealmListPointer): ListOperator<E>
}

internal class PrimitiveListOperator<E> constructor(
    override val exposedClass: KClass<*>,
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    val nativePointer: RealmListPointer,
    override val converter: RealmValueConverter<E>
) : ListOperator<E> {

    override fun get(index: Int): E = unscoped {
        val transport = RealmInterop.realm_list_get(nativePointer, index.toLong(), it)
        val value = converter.realmValueToPublic(transport)
        return value as E
    }

    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ) = scoped {
        val transport = converter.publicToRealmValue(it, element)
        RealmInterop.realm_list_add(nativePointer, index.toLong(), transport)
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ): E = get(index).also {
        scoped {
            val transport = converter.publicToRealmValue(it, element)
            RealmInterop.realm_list_set(nativePointer, index.toLong(), transport)
        }
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmListPointer
    ): ListOperator<E> = PrimitiveListOperator(
        exposedClass,
        mediator,
        realmReference,
        nativePointer,
        converter
    )
}

internal abstract class BaseRealmObjectListOperator<E>(
    override val exposedClass: KClass<*>,
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    val nativePointer: RealmListPointer,
    override val converter: RealmValueConverter<E>
) : ListOperator<E> {

    override fun get(index: Int): E = unscoped { struct ->
        val transport = RealmInterop.realm_list_get(nativePointer, index.toLong(), struct)
        val value = converter.realmValueToPublic(transport)
        return value as E
    }
}

internal class RealmObjectListOperator<E>(
    exposedClass: KClass<*>,
    mediator: Mediator,
    realmReference: RealmReference,
    nativePointer: RealmListPointer,
    converter: RealmValueConverter<E>
) : BaseRealmObjectListOperator<E>(
    exposedClass,
    mediator,
    realmReference,
    nativePointer,
    converter
) {

    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ) = scoped {
        val realmValue = realmObjectToRealmValue(
            it,
            element as BaseRealmObject?,
            mediator,
            realmReference,
            updatePolicy,
            cache
        )
        RealmInterop.realm_list_add(nativePointer, index.toLong(), realmValue)
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ): E = scoped {
        val realmValue = realmObjectToRealmValue(
            it,
            element as BaseRealmObject?,
            mediator,
            realmReference,
            updatePolicy,
            cache
        )
        return RealmInterop.realm_list_set(nativePointer, index.toLong(), realmValue)
            ?.let { transport ->
                converter.realmValueToPublic(transport)
            } as E
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmListPointer
    ): ListOperator<E> {
        val converter: RealmValueConverter<E> =
            converter<E>(exposedClass, mediator, realmReference) as CompositeConverter<E, *>
        return RealmObjectListOperator(
            exposedClass,
            mediator,
            realmReference,
            nativePointer,
            converter
        )
    }
}

internal class EmbeddedRealmObjectListOperator<E : BaseRealmObject>(
    exposedClass: KClass<*>,
    mediator: Mediator,
    realmReference: RealmReference,
    nativePointer: RealmListPointer,
    converter: RealmValueConverter<E>
) : BaseRealmObjectListOperator<E>(
    exposedClass,
    mediator,
    realmReference,
    nativePointer,
    converter
) {

    override fun insert(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ) {
        val embedded = RealmInterop.realm_list_insert_embedded(nativePointer, index.toLong())
        val newObj = embedded.toRealmObject<BaseRealmObject>(
            element::class as KClass<BaseRealmObject>,
            mediator,
            realmReference
        )
        RealmObjectHelper.assign(newObj, element, updatePolicy, cache)
    }

    override fun set(
        index: Int,
        element: E,
        updatePolicy: UpdatePolicy,
        cache: ObjectCache
    ): E = unscoped {
        // We cannot return the old object as it is deleted when losing its parent and cannot
        // return null as this is not allowed for lists with non-nullable elements, so just return
        // the newly created object even though it goes against the list API.
        val embedded = RealmInterop.realm_list_set_embedded(nativePointer, index.toLong(), it)
        val newEmbeddedRealmObject = converter.realmValueToPublic(embedded) as BaseRealmObject
        RealmObjectHelper.assign(newEmbeddedRealmObject, element, updatePolicy, cache)
        return newEmbeddedRealmObject as E
    }

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmListPointer
    ): EmbeddedRealmObjectListOperator<E> {
        val converter: RealmValueConverter<E> =
            converter<E>(exposedClass, mediator, realmReference) as CompositeConverter<E, *>
        return EmbeddedRealmObjectListOperator(
            exposedClass,
            mediator,
            realmReference,
            nativePointer,
            converter
        )
    }
}

internal fun <T> Array<out T>.asRealmList(): RealmList<T> =
    UnmanagedRealmList<T>().apply { addAll(this@asRealmList) }
