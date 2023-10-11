// TODO Figure out if we should support watch
///*
// * Copyright 2020 Realm Inc.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// * http://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package io.realm.mongodb.mongo.events
//
//import org.bson.BsonDocument
//
///**
// * Represents the set of properties that exist on all MongoDB realm change events produced
// * by watch streams in this SDK. Other change event types inherit from this type.
// *
// * @param <DocumentT> The type of the full document in the change event.
//</DocumentT> */
//abstract class BaseChangeEvent<DocumentT> protected constructor(
//	/**
//	 * Returns the operation type of the change that triggered the change event.
//	 *
//	 * @return the operation type of this change event.
//	 */
//	val operationType: OperationType,
//	@field:Nullable
//	/**
//	 * The full document at some point after the change has been applied.
//	 *
//	 * @return the full document.
//	 */
//	@get:Nullable
//	@param:Nullable val fullDocument: DocumentT,
//	documentKey: BsonDocument,
//	@Nullable updateDescription: UpdateDescription?,
//	hasUncommittedWrites: Boolean
//) {
//
//	private val documentKey: BsonDocument
//
//	@Nullable
//	private val updateDescription: UpdateDescription
//	private val hasUncommittedWrites: Boolean
//
//	/**
//	 * The unique identifier for the document that was actually changed.
//	 *
//	 * @return the document key.
//	 */
//	fun getDocumentKey(): BsonDocument {
//		return documentKey
//	}
//
//	/**
//	 * In the case of an update, the description of which fields have been added, removed or updated.
//	 *
//	 * @return the update description.
//	 */
//	@Nullable
//	fun getUpdateDescription(): UpdateDescription {
//		return updateDescription
//	}
//
//	/**
//	 * Indicates a local change event that has not yet been synchronized with a remote data store.
//	 * Used only for the sync use case.
//	 *
//	 * @return whether or not this change event represents uncommitted writes.
//	 */
//	fun hasUncommittedWrites(): Boolean {
//		return hasUncommittedWrites
//	}
//
//	init {
//		this.documentKey = documentKey
//		this.updateDescription =
//			if (updateDescription == null) UpdateDescription(null, null) else updateDescription
//		this.hasUncommittedWrites = hasUncommittedWrites
//	}
//
//	/**
//	 * Converts the change event to a BSON representation, as it would look on a MongoDB realm change
//	 * stream, or a Realm compact watch stream.
//	 *
//	 * @return The BSON document representation of the change event.
//	 */
//	abstract fun toBsonDocument(): BsonDocument?
//
//	/**
//	 * Represents the different MongoDB operations that can occur.
//	 */
//	enum class OperationType {
//		INSERT,
//		DELETE,
//		REPLACE,
//		UPDATE,
//		UNKNOWN
//	}
//}
