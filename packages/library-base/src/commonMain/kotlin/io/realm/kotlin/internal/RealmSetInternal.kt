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

package io.realm.kotlin.internal

import io.realm.kotlin.UpdatePolicy
import io.realm.kotlin.internal.interop.Callback
import io.realm.kotlin.internal.interop.RealmChangesPointer
import io.realm.kotlin.internal.interop.RealmInterop
import io.realm.kotlin.internal.interop.RealmNotificationTokenPointer
import io.realm.kotlin.internal.interop.RealmSetPointer
import io.realm.kotlin.internal.interop.RealmValueTransport
import io.realm.kotlin.internal.interop.scoped
import io.realm.kotlin.internal.interop.unscoped
import io.realm.kotlin.notifications.SetChange
import io.realm.kotlin.notifications.internal.DeletedSetImpl
import io.realm.kotlin.notifications.internal.InitialSetImpl
import io.realm.kotlin.notifications.internal.UpdatedSetImpl
import io.realm.kotlin.types.BaseRealmObject
import io.realm.kotlin.types.RealmSet
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.flow.Flow
import kotlin.reflect.KClass

/**
 * Implementation for unmanaged sets, backed by a [MutableSet].
 */
internal class UnmanagedRealmSet<E> : RealmSet<E>, InternalDeleteable, MutableSet<E> by mutableSetOf() {
    override fun asFlow(): Flow<SetChange<E>> {
        throw UnsupportedOperationException("Unmanaged sets cannot be observed.")
    }

    override fun delete() {
        throw UnsupportedOperationException("Unmanaged sets cannot be deleted.")
    }
}

/**
 * Implementation for managed sets, backed by Realm.
 */
internal class ManagedRealmSet<E>(
    internal val nativePointer: RealmSetPointer,
    val operator: SetOperator<E>
) : AbstractMutableSet<E>(), RealmSet<E>, InternalDeleteable, Observable<ManagedRealmSet<E>, SetChange<E>>, Flowable<SetChange<E>> {

    override val size: Int
        get() {
            operator.realmReference.checkClosed()
            return RealmInterop.realm_set_size(nativePointer).toInt()
        }

    override fun add(element: E): Boolean {
        operator.realmReference.checkClosed()
        try {
            return operator.add(element)
        } catch (exception: Throwable) {
            throw CoreExceptionConverter.convertToPublicException(
                exception,
                "Could not add element to set"
            )
        }
    }

    override fun clear() {
        operator.realmReference.checkClosed()
        RealmInterop.realm_set_clear(nativePointer)
    }

    override fun contains(element: E): Boolean {
        operator.realmReference.checkClosed()
        return operator.contains(element)
    }

    override fun iterator(): MutableIterator<E> {
        operator.realmReference.checkClosed()
        return object : MutableIterator<E> {

            private var pos = -1

            override fun hasNext(): Boolean = pos + 1 < size

            override fun next(): E {
                pos = pos.inc()
                if (pos >= size) {
                    throw NoSuchElementException("Cannot access index $pos when size is $size. Remember to check hasNext() before using next().")
                }
                return operator.get(pos)
            }

            override fun remove() {
                if (pos < 0) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: iterator never returned an element.")
                }
                if (isEmpty()) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: set is empty.")
                }

                // Fetching this element is like calling a getter so there is no need to scope it
                val element = unscoped {
                    RealmInterop.realm_set_get(nativePointer, pos.toLong(), it)
                }

                // Creating a null transport might be overkill but it assures we don't call getType
                // on the transport object every time we need to see if the value is null and is
                // proven to be more efficient this way for ordinary accessors
                val erased = scoped {
                    val transport = element ?: RealmValueTransport.createNull(it)
                    RealmInterop.realm_set_erase(nativePointer, transport)
                }
                if (!erased) {
                    throw NoSuchElementException("Could not remove last element returned by the iterator: was there an element to remove?")
                }
            }
        }
    }

    override fun asFlow(): Flow<SetChange<E>> {
        operator.realmReference.checkClosed()
        return operator.realmReference.owner.registerObserver(this)
    }

    override fun freeze(frozenRealm: RealmReference): ManagedRealmSet<E>? {
        return RealmInterop.realm_set_resolve_in(nativePointer, frozenRealm.dbPointer)?.let {
            ManagedRealmSet(it, operator.copy(frozenRealm, it))
        }
    }

    override fun thaw(liveRealm: RealmReference): ManagedRealmSet<E>? {
        return RealmInterop.realm_set_resolve_in(nativePointer, liveRealm.dbPointer)?.let {
            ManagedRealmSet(it, operator.copy(liveRealm, it))
        }
    }

    override fun registerForNotification(
        callback: Callback<RealmChangesPointer>
    ): RealmNotificationTokenPointer {
        return RealmInterop.realm_set_add_notification_callback(nativePointer, callback)
    }

    override fun emitFrozenUpdate(
        frozenRealm: RealmReference,
        change: RealmChangesPointer,
        channel: SendChannel<SetChange<E>>
    ): ChannelResult<Unit>? {
        val frozenSet: ManagedRealmSet<E>? = freeze(frozenRealm)
        return if (frozenSet != null) {
            val builder = SetChangeSetBuilderImpl(change)

            if (builder.isEmpty()) {
                channel.trySend(InitialSetImpl(frozenSet))
            } else {
                channel.trySend(UpdatedSetImpl(frozenSet, builder.build()))
            }
        } else {
            channel.trySend(DeletedSetImpl(UnmanagedRealmSet()))
                .also {
                    channel.close()
                }
        }
    }

    override fun delete() {
        RealmInterop.realm_set_remove_all(nativePointer)
    }
}

/**
 * Operator interface abstracting the connection between the API and and the interop layer.
 */
internal interface SetOperator<E> : CollectionOperator<E> {

    fun add(
        element: E,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean

    fun addAll(
        elements: Collection<E>,
        updatePolicy: UpdatePolicy = UpdatePolicy.ALL,
        cache: ObjectCache = mutableMapOf()
    ): Boolean {
        @Suppress("VariableNaming")
        var changed = false
        for (e in elements) {
            val hasChanged = add(e, updatePolicy, cache)
            if (hasChanged) {
                changed = true
            }
        }
        return changed
    }

    fun get(index: Int): E
    fun contains(element: E): Boolean
    fun copy(realmReference: RealmReference, nativePointer: RealmSetPointer): SetOperator<E>
}

/**
 * Operator for primitive types.
 */
internal class PrimitiveSetOperator<E>(
    override val exposedClass: KClass<*>,
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val converter: RealmValueConverter<E>,
    private val nativePointer: RealmSetPointer
) : SetOperator<E> {

    override fun add(element: E, updatePolicy: UpdatePolicy, cache: ObjectCache): Boolean = scoped {
        val transport = converter.publicToRealmValue(it, element)
        RealmInterop.realm_set_insert(nativePointer, transport)
    }
//        val value = converter.publicToRealmValue(element)
//        return RealmInterop.realm_set_insert(nativePointer, value)

    override fun get(index: Int): E = unscoped {
        RealmInterop.realm_set_get(nativePointer, index.toLong(), it)
            .let { transport -> converter.realmValueToPublic(transport) } as E
    }
//        RealmInterop.realm_set_get(nativePointer, position.toLong())
//            ?.let { converter.realmValueToPublic(it) } as E

    override fun contains(element: E): Boolean = scoped {
        val value = converter.publicToRealmValue(it, element)
        RealmInterop.realm_set_find(nativePointer, value)
    }
//        val value = converter.publicToRealmValue(element)
//        return RealmInterop.realm_set_find(nativePointer, value)

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> =
        PrimitiveSetOperator(exposedClass, mediator, realmReference, converter, nativePointer)
}

/**
 * Operator for Realm objects.
 */
internal class RealmObjectSetOperator<E>(
    override val exposedClass: KClass<*>,
    override val mediator: Mediator,
    override val realmReference: RealmReference,
    override val converter: RealmValueConverter<E>,
    private val nativePointer: RealmSetPointer
) : SetOperator<E> {

    override fun add(element: E, updatePolicy: UpdatePolicy, cache: ObjectCache): Boolean =
        scoped {
            val transport = realmObjectToRealmValue(
                it,
                element as BaseRealmObject?,
                mediator,
                realmReference,
                updatePolicy,
                cache
            )
            RealmInterop.realm_set_insert(nativePointer, transport)
        }
//        val realmObjectToRealmValue = realmObjectToRealmValue(
//            element as BaseRealmObject?,
//            mediator,
//            realmReference,
//            updatePolicy,
//            cache
//        )
//        return RealmInterop.realm_set_insert(nativePointer, realmObjectToRealmValue)

    @Suppress("UNCHECKED_CAST")
    override fun get(index: Int): E = unscoped {
        RealmInterop.realm_set_get(nativePointer, index.toLong(), it)
            ?.let { transport ->
                converter.realmValueToPublic(transport)
            } as E
    }
//        RealmInterop.realm_set_get(nativePointer, position.toLong())
//            ?.let { converter.realmValueToPublic(it) } as E

    override fun contains(element: E): Boolean = scoped {
        val transport = realmObjectToRealmValue(
            it,
            element as BaseRealmObject?,
            mediator,
            realmReference
        )
        RealmInterop.realm_set_find(nativePointer, transport)
    }
//        val realmObjectToRealmValue = realmObjectToRealmValue(
//            element as BaseRealmObject?,
//            mediator,
//            realmReference
//        )
//        return RealmInterop.realm_set_find(nativePointer, realmObjectToRealmValue)

    override fun copy(
        realmReference: RealmReference,
        nativePointer: RealmSetPointer
    ): SetOperator<E> {
        val converter =
            converter<E>(exposedClass, mediator, realmReference) as CompositeConverter<E, *>
        return RealmObjectSetOperator(
            exposedClass,
            mediator,
            realmReference,
            converter,
            nativePointer
        )
    }
}

internal fun <T> Array<out T>.asRealmSet(): RealmSet<T> =
    UnmanagedRealmSet<T>().apply { addAll(this@asRealmSet) }
