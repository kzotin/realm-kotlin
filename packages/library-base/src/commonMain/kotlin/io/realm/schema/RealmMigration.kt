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

package io.realm.schema

import io.realm.BaseRealm
import io.realm.DynamicRealm

//
///**
// * The RealmMigration class is used to perform the migration of one Realm schema to another.
// * The schema for a Realm is defined by all classes in a project that extend [io.realm.RealmObject]
// * or implement [io.realm.RealmModel], so any changes to these classes will require a migration.
// *
// *
// * To support migrations from any previous schemaVersion to the newest, the following pattern is recommended when
// * writing a migration:
// *
// *
// * <pre>
// * `public class CustomMigration implements RealmMigration {
// * \@Override
// * public long migrate(DynamicRealm realm, long oldVersion, long newVersion) {
// * RealmSchema schema = realm.getSchema();
// *
// * if (oldVersion == 0) {
// * // Migrate from v0 to v1
// * oldVersion++;
// * }
// *
// * if (oldVersion == 1) {
// * // Migrate from v1 to v2
// * oldVersion++;
// * }
// *
// * if (oldVersion < newVersion) {
// * throw new IllegalStateException(String.format(Locale.US, "Migration missing from v%d to v%d", oldVersion, newVersion));
// * }
// * }
// * }
//` *
//</pre> *
// *
// *
// * During development when RealmObject classes can change frequently, it is possible to use
// * [io.realm.Realm.deleteRealm]. This will delete the database file and eliminate the need for
// * any migrations.
// *
// * @see io.realm.RealmConfiguration.Builder.schemaVersion
// * @see io.realm.RealmConfiguration.Builder.migration
// * @see io.realm.RealmConfiguration.Builder.deleteRealmIfMigrationNeeded
// */
// FIXME Should be internal, but cannot as RealmConfiguration exposes it across base/sync boundary
fun interface RealmMigration {
//    /**
//     * This method will be called if a migration is needed. The entire method is wrapped in a
//     * write transaction so it is possible to create, update or delete any existing objects
//     * without wrapping it in your own transaction.
//     *
//     * @param realm the Realm schema on which to perform the migration.
//     * @param oldVersion the schema version of the Realm at the start of the migration.
//     * @param newVersion the schema version of the Realm after executing the migration.
//     */
    fun migrate(old: DynamicRealm?, new: DynamicRealm?)// , oldVersion: Long, newVersion: Long)
}
