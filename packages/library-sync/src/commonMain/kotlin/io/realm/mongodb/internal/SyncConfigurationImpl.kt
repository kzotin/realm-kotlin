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

package io.realm.mongodb.internal

import io.realm.internal.InternalConfiguration
import io.realm.internal.SimpleFrozenRealmImpl
import io.realm.internal.SimpleLiveRealmImpl
import io.realm.internal.interop.FrozenRealmPointer
import io.realm.internal.interop.LiveRealmPointer
import io.realm.internal.interop.RealmConfigurationPointer
import io.realm.internal.interop.RealmInterop
import io.realm.internal.interop.RealmSyncConfigurationPointer
import io.realm.internal.interop.RealmSyncSessionPointer
import io.realm.internal.interop.SyncAfterClientResetHandler
import io.realm.internal.interop.SyncBeforeClientResetHandler
import io.realm.internal.interop.SyncErrorCallback
import io.realm.internal.interop.sync.PartitionValue
import io.realm.internal.interop.sync.SyncError
import io.realm.internal.interop.sync.SyncSessionResyncMode
import io.realm.internal.platform.freeze
import io.realm.mongodb.sync.ClientResetRequiredError
import io.realm.mongodb.sync.DiscardUnsyncedChangesStrategy
import io.realm.mongodb.sync.InitialSubscriptionsCallback
import io.realm.mongodb.sync.SyncClientResetStrategy
import io.realm.mongodb.sync.SyncConfiguration
import io.realm.mongodb.sync.SyncMode
import io.realm.mongodb.sync.SyncSession

@Suppress("LongParameterList")
internal class SyncConfigurationImpl(
    private val configuration: InternalConfiguration,
    internal val partitionValue: PartitionValue?,
    override val user: UserImpl,
    override val errorHandler: SyncSession.ErrorHandler,
    override val syncClientResetStrategy: SyncClientResetStrategy,
    val initialSubscriptionsCallback: InitialSubscriptionsCallback?,
    val rerunInitialSubscriptions: Boolean
) : InternalConfiguration by configuration, SyncConfiguration {

    override fun createNativeConfiguration(): RealmConfigurationPointer {
        val ptr: RealmConfigurationPointer = configuration.createNativeConfiguration()
        return syncInitializer(ptr)
    }

    private val syncInitializer: (RealmConfigurationPointer) -> RealmConfigurationPointer

    init {
        // We need to freeze `errorHandler` reference on initial thread
        val userErrorHandler = errorHandler
        val clientResetStrategy = syncClientResetStrategy.freeze()
        val frozenAppPointer = user.app.nativePointer.freeze()
        val errorCallback = object : SyncErrorCallback {
            override fun onSyncError(pointer: RealmSyncSessionPointer, error: SyncError) {
                val session = SyncSessionImpl(pointer)
                val syncError = convertSyncError(error)

                // Notify before/after callbacks too if error is client reset
                if (error.isClientResetRequested) {
                    when (clientResetStrategy) {
                        is DiscardUnsyncedChangesStrategy -> {
                            clientResetStrategy.onError(
                                session,
                                ClientResetRequiredError(
                                    frozenAppPointer,
                                    error
                                )
                            )
                        }
                        else -> throw IllegalArgumentException("Invalid client reset strategy.")
                    }
                }

                userErrorHandler.onError(session, syncError)
            }
        }.freeze()

        syncInitializer = { nativeConfig: RealmConfigurationPointer ->
            val nativeSyncConfig: RealmSyncConfigurationPointer = if (partitionValue == null) {
                RealmInterop.realm_flx_sync_config_new(user.nativePointer)
            } else {
                RealmInterop.realm_sync_config_new(user.nativePointer, partitionValue.asSyncPartition())
            }

            RealmInterop.realm_sync_config_set_error_handler(
                nativeSyncConfig,
                errorCallback
            )

            val clientResetMode: SyncSessionResyncMode = when (clientResetStrategy) {
                is DiscardUnsyncedChangesStrategy ->
                    SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL
                else -> throw IllegalArgumentException("Invalid client reset type.")
            }
            RealmInterop.realm_sync_config_set_resync_mode(nativeSyncConfig, clientResetMode)

            // Set before and after handlers only if resync mode is set to discard local
            if (clientResetMode == SyncSessionResyncMode.RLM_SYNC_SESSION_RESYNC_MODE_DISCARD_LOCAL) {
                val onBefore: SyncBeforeClientResetHandler = object : SyncBeforeClientResetHandler {
                    override fun onBeforeReset(realmBefore: FrozenRealmPointer) {
                        (clientResetStrategy as DiscardUnsyncedChangesStrategy).onBeforeReset(
                            SimpleFrozenRealmImpl(realmBefore, configuration)
                        )
                    }
                }
                RealmInterop.realm_sync_config_set_before_client_reset_handler(
                    nativeSyncConfig,
                    onBefore
                )

                val onAfter: SyncAfterClientResetHandler = object : SyncAfterClientResetHandler {
                    override fun onAfterReset(
                        realmBefore: FrozenRealmPointer,
                        realmAfter: LiveRealmPointer,
                        didRecover: Boolean
                    ) {
                        (clientResetStrategy as DiscardUnsyncedChangesStrategy).onAfterReset(
                            SimpleFrozenRealmImpl(realmBefore, configuration),
                            SimpleLiveRealmImpl(realmAfter, configuration)
                        )
                    }
                }
                RealmInterop.realm_sync_config_set_after_client_reset_handler(
                    nativeSyncConfig,
                    onAfter
                )
            }

            RealmInterop.realm_config_set_sync_config(nativeConfig, nativeSyncConfig)

            nativeConfig
        }
    }

    override val syncMode: SyncMode =
        if (partitionValue == null) SyncMode.FLEXIBLE else SyncMode.PARTITION_BASED
}
