/*
 * Copyright 2023 Realm Inc.
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
package io.realm.kotlin.mongodb.mongo.result

import org.mongodb.kbson.BsonValue

/**
 * The result of an update operation.
 */
public data class UpdateResult(
	/**
	 * The number of documents matched by the query.
	 */
	val matchedCount: Long,
	/**
	 * The number of documents modified.
	 */
	val modifiedCount: Long,
	/**
	 *  The _id of the inserted document if the replace resulted in an inserted document,
	 *  otherwise null.
	 */
	val upsertedId: BsonValue? // TODO Should we use BsonNull here instead?
)
